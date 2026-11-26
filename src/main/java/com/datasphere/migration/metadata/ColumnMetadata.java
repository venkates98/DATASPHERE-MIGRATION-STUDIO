package com.datasphere.migration.metadata;

import java.sql.Types;
import java.util.Locale;
import java.util.Set;

/**
 * Structure of one column as discovered from the database catalog. Contains no data values.
 *
 * @param size  character length, byte length or numeric precision (as reported by the driver)
 * @param scale digits after the decimal point, or fractional-second digits for date/time types
 */
public record ColumnMetadata(
        String name,
        int ordinal,
        String typeName,
        int jdbcType,
        Integer size,
        Integer scale,
        boolean nullable,
        boolean autoIncrement,
        boolean generated,
        boolean hasDefault,
        boolean primaryKey,
        boolean unique,
        boolean foreignKey,
        String referencedTable,
        String referencedColumn) {

    private static final Set<Integer> SIZED = Set.of(Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR,
            Types.BINARY, Types.VARBINARY, Types.NUMERIC, Types.DECIMAL);
    private static final int UNBOUNDED = 1_000_000;

    /** A target column that must receive a value for an insert to succeed. */
    public boolean required() {
        return !nullable && !hasDefault && !autoIncrement && !generated;
    }

    /** Human-readable type such as {@code NVARCHAR(100)} or {@code DECIMAL(10,2)}. */
    public String displayType() {
        String type = typeName == null ? "UNKNOWN" : typeName.toUpperCase(Locale.ROOT);
        if (type.contains("(") || !SIZED.contains(jdbcType) || size == null || size <= 0) {
            return type;
        }
        if (size >= UNBOUNDED) {
            return type + "(MAX)";
        }
        boolean numeric = jdbcType == Types.NUMERIC || jdbcType == Types.DECIMAL;
        if (numeric && scale != null && scale >= 0) {
            return type + "(" + size + "," + scale + ")";
        }
        return numeric && scale != null ? type : type + "(" + size + ")";
    }
}
