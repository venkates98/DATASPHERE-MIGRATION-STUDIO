package com.datasphere.migration.metadata;

import com.datasphere.migration.db.DatabaseType;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads structure (schemas, tables, columns, keys, unique constraints) through the standard JDBC
 * {@link DatabaseMetaData} API, so the same code serves every supported database. It reads catalog
 * information only; no table data is ever selected.
 */
public final class JdbcMetadataReader {

    /** Upper bound for one table list so a huge catalog cannot exhaust memory. */
    public static final int MAX_TABLES = 5000;

    private JdbcMetadataReader() {
    }

    public static List<String> listSchemas(Connection connection, DatabaseType type) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        List<String> names = new ArrayList<>();
        try (ResultSet rs = type.databaseIsSchema() ? md.getCatalogs() : md.getSchemas()) {
            String column = type.databaseIsSchema() ? "TABLE_CAT" : "TABLE_SCHEM";
            while (rs.next()) {
                String name = rs.getString(column);
                if (name != null && !type.isSystemSchema(name)) {
                    names.add(name);
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static List<TableSummary> listTables(Connection connection, DatabaseType type, String schema)
            throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        String escape = md.getSearchStringEscape();
        List<TableSummary> tables = new ArrayList<>();
        String catalog = type.databaseIsSchema() ? emptyToNull(schema) : null;
        String schemaPattern = type.databaseIsSchema() ? null : likeLiteral(emptyToNull(schema), escape);
        try (ResultSet rs = md.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next() && tables.size() < MAX_TABLES) {
                String name = rs.getString("TABLE_NAME");
                if (name != null) {
                    tables.add(new TableSummary(schema == null ? "" : schema, name, rs.getString("TABLE_TYPE")));
                }
            }
        }
        tables.sort(Comparator.comparing(TableSummary::name, String.CASE_INSENSITIVE_ORDER));
        return tables;
    }

    /** @return the table's structure, or empty if no table or view with exactly this name exists */
    public static Optional<TableMetadata> readTable(Connection connection, DatabaseType type, TableRef ref)
            throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        String escape = md.getSearchStringEscape();
        String schema = emptyToNull(ref.schema());
        String catalog = type.databaseIsSchema() ? schema : null;
        String schemaExact = type.databaseIsSchema() ? null : schema;
        String schemaPattern = likeLiteral(schemaExact, escape);
        String tablePattern = likeLiteral(ref.name(), escape);

        String tableType = null;
        try (ResultSet rs = md.getTables(catalog, schemaPattern, tablePattern, new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                if (ref.name().equals(rs.getString("TABLE_NAME"))) {
                    tableType = rs.getString("TABLE_TYPE");
                    break;
                }
            }
        }
        if (tableType == null) {
            return Optional.empty();
        }

        Set<String> primaryKey = primaryKeyColumns(md, catalog, schemaExact, ref.name());
        Map<String, String[]> foreignKeys = foreignKeys(md, catalog, schemaExact, ref.name());
        Set<String> unique = singleColumnUniques(md, catalog, schemaExact, ref.name());

        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet rs = md.getColumns(catalog, schemaPattern, tablePattern, "%")) {
            while (rs.next()) {
                if (!ref.name().equals(rs.getString("TABLE_NAME"))) {
                    continue; // a LIKE pattern matched a similarly named table
                }
                String name = rs.getString("COLUMN_NAME");
                String[] fk = foreignKeys.get(name);
                columns.add(new ColumnMetadata(
                        name,
                        rs.getInt("ORDINAL_POSITION"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("DATA_TYPE"),
                        nullableInt(rs, "COLUMN_SIZE"),
                        nullableInt(rs, "DECIMAL_DIGITS"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        yes(optional(rs, "IS_AUTOINCREMENT")),
                        yes(optional(rs, "IS_GENERATEDCOLUMN")),
                        hasDefault(rs.getString("COLUMN_DEF")),
                        primaryKey.contains(name),
                        unique.contains(name),
                        fk != null,
                        fk == null ? null : fk[0],
                        fk == null ? null : fk[1]));
            }
        }
        columns.sort(Comparator.comparingInt(ColumnMetadata::ordinal));
        return Optional.of(new TableMetadata(ref.schema(), ref.name(), tableType, columns));
    }

    private static Set<String> primaryKeyColumns(DatabaseMetaData md, String catalog, String schema, String table) {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        } catch (SQLException ignored) {
            // views and some catalogs have no key metadata; keys are advisory evidence, not required
        }
        return columns;
    }

    private static Map<String, String[]> foreignKeys(DatabaseMetaData md, String catalog, String schema, String table) {
        Map<String, String[]> result = new HashMap<>();
        try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String pkSchema = rs.getString("PKTABLE_SCHEM");
                String pkTable = rs.getString("PKTABLE_NAME");
                String reference = pkSchema == null || pkSchema.isEmpty() ? pkTable : pkSchema + "." + pkTable;
                result.put(rs.getString("FKCOLUMN_NAME"), new String[]{reference, rs.getString("PKCOLUMN_NAME")});
            }
        } catch (SQLException ignored) {
            // see primaryKeyColumns
        }
        return result;
    }

    /**
     * Columns covered by a unique index of their own. {@code approximate=true} is deliberate: on Oracle,
     * {@code false} makes the driver run ANALYZE, which writes statistics to the database.
     */
    private static Set<String> singleColumnUniques(DatabaseMetaData md, String catalog, String schema, String table) {
        Map<String, List<String>> indexColumns = new LinkedHashMap<>();
        try (ResultSet rs = md.getIndexInfo(catalog, schema, table, true, true)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                String index = rs.getString("INDEX_NAME");
                if (column != null && index != null && rs.getShort("TYPE") != DatabaseMetaData.tableIndexStatistic) {
                    indexColumns.computeIfAbsent(index, k -> new ArrayList<>()).add(column);
                }
            }
        } catch (SQLException ignored) {
            // see primaryKeyColumns
        }
        Set<String> unique = new HashSet<>();
        indexColumns.values().stream().filter(cols -> cols.size() == 1).forEach(cols -> unique.add(cols.get(0)));
        return unique;
    }

    private static boolean hasDefault(String definition) {
        if (definition == null) {
            return false;
        }
        String d = definition.trim();
        return !d.isEmpty() && !d.equalsIgnoreCase("NULL") && !d.equalsIgnoreCase("(NULL)");
    }

    private static boolean yes(String value) {
        return value != null && value.equalsIgnoreCase("YES");
    }

    private static String optional(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null; // column not provided by this driver version
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Escapes LIKE wildcards ({@code _} and {@code %}) so a name is matched literally. */
    static String likeLiteral(String value, String escape) {
        if (value == null || escape == null || escape.isEmpty()) {
            return value;
        }
        return value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }
}
