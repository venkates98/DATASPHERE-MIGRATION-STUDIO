package com.datasphere.migration.db.dialect;

import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.metadata.TableRef;
import java.util.List;

/**
 * Vendor-specific SQL text. Identifiers are always quoted; values are never concatenated — callers use
 * prepared statements with {@code ?} placeholders.
 */
public interface SqlDialect {

    char quoteOpen();

    char quoteClose();

    String quote(String identifier);

    String qualified(String schema, String table);

    PaginationStyle paginationStyle();

    NativeTypeSpec mapType(NormalizedType source);

    /** Types this dialect will not invent a mapping for (reported in the compatibility matrix). */
    List<String> unsupportedTypeNotes();

    String createTable(TableRef table, List<ColumnDdl> columns, List<String> primaryKey);

    String insert(TableRef table, List<String> columns);

    String count(TableRef table);

    String countNulls(TableRef table, String column);

    String numericSum(TableRef table, String column);

    String selectColumns(TableRef table, List<String> columns, List<String> orderBy);

    /**
     * Keyset resume: {@code WHERE firstPk > ?} when there is a single ordering column. Composite keys are
     * not resumed this way (the engine restarts that table and relies on duplicate handling).
     */
    String selectColumnsAfter(TableRef table, List<String> columns, String orderColumn);

    default boolean isTransient(String sqlState, int vendorCode) {
        if (sqlState == null) {
            return false;
        }
        return sqlState.startsWith("08") || "40001".equals(sqlState) || "40P01".equals(sqlState);
    }

    /** One column of a generated {@code CREATE TABLE}. */
    record ColumnDdl(String name, String typeDdl, boolean nullable) {
    }

    static List<ColumnDdl> fromMetadata(TableMetadata table, java.util.function.Function<ColumnMetadata, String> type) {
        return table.columns().stream()
                .map(c -> new ColumnDdl(c.name(), type.apply(c), c.nullable()))
                .toList();
    }
}
