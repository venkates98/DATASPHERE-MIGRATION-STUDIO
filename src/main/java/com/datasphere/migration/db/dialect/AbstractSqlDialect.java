package com.datasphere.migration.db.dialect;

import com.datasphere.migration.metadata.TableRef;
import java.util.List;
import java.util.stream.Collectors;

/** Identifier quoting and the SQL that is the same once identifiers are quoted. */
public abstract class AbstractSqlDialect implements SqlDialect {

    private final char open;
    private final char close;

    protected AbstractSqlDialect(char open, char close) {
        this.open = open;
        this.close = close;
    }

    @Override
    public final char quoteOpen() {
        return open;
    }

    @Override
    public final char quoteClose() {
        return close;
    }

    @Override
    public final String quote(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier is required");
        }
        String escaped = identifier.replace(String.valueOf(close), String.valueOf(close) + close);
        return open + escaped + close;
    }

    @Override
    public final String qualified(String schema, String table) {
        return schema == null || schema.isBlank() ? quote(table) : quote(schema) + "." + quote(table);
    }

    @Override
    public String createTable(TableRef table, List<ColumnDdl> columns, List<String> primaryKey) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(qualified(table.schema(), table.name()))
                .append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnDdl c = columns.get(i);
            sql.append("  ").append(quote(c.name())).append(' ').append(c.typeDdl());
            if (!c.nullable()) {
                sql.append(" NOT NULL");
            }
            if (i < columns.size() - 1 || (primaryKey != null && !primaryKey.isEmpty())) {
                sql.append(',');
            }
            sql.append('\n');
        }
        if (primaryKey != null && !primaryKey.isEmpty()) {
            sql.append("  PRIMARY KEY (").append(primaryKey.stream().map(this::quote).collect(Collectors.joining(", ")))
                    .append(")\n");
        }
        sql.append(')');
        return sql.toString();
    }

    @Override
    public String insert(TableRef table, List<String> columns) {
        String cols = columns.stream().map(this::quote).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + qualified(table.schema(), table.name()) + " (" + cols + ") VALUES (" + placeholders
                + ")";
    }

    @Override
    public String count(TableRef table) {
        return "SELECT COUNT(*) FROM " + qualified(table.schema(), table.name());
    }

    @Override
    public String countNulls(TableRef table, String column) {
        return "SELECT COUNT(*) FROM " + qualified(table.schema(), table.name()) + " WHERE " + quote(column)
                + " IS NULL";
    }

    @Override
    public String numericSum(TableRef table, String column) {
        return "SELECT SUM(" + quote(column) + ") FROM " + qualified(table.schema(), table.name());
    }

    @Override
    public String selectColumns(TableRef table, List<String> columns, List<String> orderBy) {
        String cols = columns.stream().map(this::quote).collect(Collectors.joining(", "));
        String sql = "SELECT " + cols + " FROM " + qualified(table.schema(), table.name());
        if (orderBy != null && !orderBy.isEmpty()) {
            sql += " ORDER BY " + orderBy.stream().map(this::quote).collect(Collectors.joining(", "));
        }
        return sql;
    }

    @Override
    public String selectColumnsAfter(TableRef table, List<String> columns, String orderColumn) {
        String cols = columns.stream().map(this::quote).collect(Collectors.joining(", "));
        return "SELECT " + cols + " FROM " + qualified(table.schema(), table.name()) + " WHERE "
                + quote(orderColumn) + " > ? ORDER BY " + quote(orderColumn);
    }

    protected static NativeTypeSpec loss(NativeTypeSpec spec, String warning) {
        return new NativeTypeSpec(spec.ddl(), spec.nativeName(), spec.category(), spec.length(), spec.precision(),
                spec.scale(), spec.fractionalDigits(), spec.unicode(), spec.unsupported(), spec.unsupportedReason(),
                true, append(spec.warnings(), warning));
    }

    protected static List<String> append(List<String> warnings, String extra) {
        java.util.ArrayList<String> next = new java.util.ArrayList<>(warnings);
        next.add(extra);
        return List.copyOf(next);
    }

    protected static int cap(Integer value, int max) {
        if (value == null) {
            return max;
        }
        return Math.min(value, max);
    }
}
