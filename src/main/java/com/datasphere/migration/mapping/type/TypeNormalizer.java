package com.datasphere.migration.mapping.type;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.metadata.ColumnMetadata;
import java.sql.Types;
import java.util.Locale;

/**
 * Reduces vendor-specific column types (SQL Server, Oracle, PostgreSQL, MySQL, ...) to a
 * {@link NormalizedType}. Native names are recognised first because drivers report some types
 * misleadingly (Oracle {@code DATE} contains a time part; SQL Server {@code timestamp} is a binary row version).
 */
public final class TypeNormalizer {

    private static final long UNBOUNDED_LENGTH = 1_000_000L;

    private TypeNormalizer() {
    }

    public static NormalizedType normalize(ColumnMetadata column, DatabaseType db) {
        String display = column.displayType();
        String base = baseName(column.typeName());
        boolean unsigned = column.typeName() != null && column.typeName().toLowerCase(Locale.ROOT).contains("unsigned");
        Integer size = column.size();
        Integer scale = column.scale();
        boolean unicodeByDefault = db == DatabaseType.POSTGRESQL || db == DatabaseType.MYSQL;

        // Boolean-like columns first (MySQL TINYINT(1) and SQL Server BIT arrive as BIT/BOOLEAN).
        if ((column.jdbcType() == Types.BOOLEAN || column.jdbcType() == Types.BIT)
                && (base.equals("bit") || base.equals("boolean") || base.equals("bool") || base.equals("tinyint"))) {
            return simple(TypeCategory.BOOLEAN, base, display);
        }

        switch (base) {
            case "tinyint":
                return integer(base, display, 3);
            case "smallint", "int2", "smallserial":
                return integer(base, display, 5);
            case "mediumint":
                return integer(base, display, 8);
            case "int", "integer", "int4", "serial":
                return integer(base, display, 10);
            case "bigint", "int8", "bigserial":
                return integer(base, display, unsigned ? 20 : 19);
            case "decimal", "numeric", "dec", "number":
                return decimal(base, display, size, scale);
            case "money":
                return new NormalizedType(TypeCategory.DECIMAL, base, display, false, null, 19, 4, null);
            case "smallmoney":
                return new NormalizedType(TypeCategory.DECIMAL, base, display, false, null, 10, 4, null);
            case "real", "float4", "binary_float":
                return floating(base, display, 24);
            case "double", "double precision", "float8", "binary_double":
                return floating(base, display, 53);
            case "float":
                return floating(base, display, size == null ? 53 : size <= 24 ? 24 : size <= 53 ? 53 : 126);
            case "boolean", "bool", "bit":
                return simple(TypeCategory.BOOLEAN, base, display);
            case "char", "nchar", "varchar", "nvarchar", "varchar2", "nvarchar2", "character", "character varying",
                 "national character", "national character varying", "bpchar", "varchar_ignorecase", "string":
                return string(base, display, size, unicodeByDefault || base.startsWith("n") && !base.equals("name"));
            case "text", "ntext", "tinytext", "mediumtext", "longtext", "clob", "nclob", "long", "long varchar":
                return new NormalizedType(TypeCategory.CLOB, base, display,
                        unicodeByDefault || base.equals("ntext") || base.equals("nclob"), null, null, null, null);
            case "binary", "varbinary", "raw", "binary varying":
                return binary(base, display, size);
            case "image", "blob", "tinyblob", "mediumblob", "longblob", "bytea", "long raw", "binary large object":
                return simple(TypeCategory.BLOB, base, display);
            case "rowversion":
                return new NormalizedType(TypeCategory.BINARY, base, display, false, 8L, null, null, null);
            case "date":
                // Oracle DATE always carries a time of day.
                return db == DatabaseType.ORACLE
                        ? timestamp(TypeCategory.TIMESTAMP, base, display, 0)
                        : simple(TypeCategory.DATE, base, display);
            case "time", "time without time zone":
                return simple(TypeCategory.TIME, base, display);
            case "smalldatetime":
                return timestamp(TypeCategory.TIMESTAMP, base, display, 0);
            case "datetime":
                return timestamp(TypeCategory.TIMESTAMP, base, display, db == DatabaseType.SQLSERVER ? 3 : fraction(scale, 0));
            case "datetime2":
                return timestamp(TypeCategory.TIMESTAMP, base, display, fraction(scale, 7));
            case "timestamp":
                // The real SQL Server driver reports timestamp (row version) as BINARY/VARBINARY;
                // a driver that reports a temporal JDBC type means a genuine date-time column.
                if (db == DatabaseType.SQLSERVER && column.jdbcType() != Types.TIMESTAMP) {
                    return new NormalizedType(TypeCategory.BINARY, "rowversion", display, false, 8L, null, null, null);
                }
                return timestamp(TypeCategory.TIMESTAMP, base, display, fraction(scale, 6));
            case "timestamp without time zone":
                return timestamp(TypeCategory.TIMESTAMP, base, display, fraction(scale, 6));
            case "datetimeoffset":
                return timestamp(TypeCategory.TIMESTAMP_TZ, base, display, fraction(scale, 7));
            case "timestamp with time zone", "timestamp with local time zone", "timestamptz":
                return timestamp(TypeCategory.TIMESTAMP_TZ, base, display, fraction(scale, 6));
            case "uniqueidentifier", "uuid":
                return simple(TypeCategory.UUID, base, display);
            case "xml", "xmltype", "sqlxml":
                return simple(TypeCategory.XML, base, display);
            case "json", "jsonb":
                return simple(TypeCategory.JSON, base, display);
            default:
                return fromJdbcType(column, base, display, size, scale, unicodeByDefault);
        }
    }

    /** Lower-case native name with size, parentheses and modifiers removed. */
    static String baseName(String typeName) {
        if (typeName == null) {
            return "unknown";
        }
        String s = typeName.toLowerCase(Locale.ROOT).replaceAll("\\(.*?\\)", "");
        s = s.replace(" identity", "").replace(" unsigned", "").replace(" zerofill", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static NormalizedType fromJdbcType(ColumnMetadata c, String base, String display, Integer size,
                                               Integer scale, boolean unicodeByDefault) {
        return switch (c.jdbcType()) {
            case Types.TINYINT -> integer(base, display, 3);
            case Types.SMALLINT -> integer(base, display, 5);
            case Types.INTEGER -> integer(base, display, 10);
            case Types.BIGINT -> integer(base, display, 19);
            case Types.NUMERIC, Types.DECIMAL -> decimal(base, display, size, scale);
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> floating(base, display, c.jdbcType() == Types.REAL ? 24 : 53);
            case Types.CHAR, Types.VARCHAR -> string(base, display, size, unicodeByDefault);
            case Types.NCHAR, Types.NVARCHAR -> string(base, display, size, true);
            case Types.LONGVARCHAR, Types.CLOB -> new NormalizedType(TypeCategory.CLOB, base, display,
                    unicodeByDefault, null, null, null, null);
            case Types.LONGNVARCHAR, Types.NCLOB -> new NormalizedType(TypeCategory.CLOB, base, display, true,
                    null, null, null, null);
            case Types.BINARY, Types.VARBINARY -> binary(base, display, size);
            case Types.LONGVARBINARY, Types.BLOB -> simple(TypeCategory.BLOB, base, display);
            case Types.DATE -> simple(TypeCategory.DATE, base, display);
            case Types.TIME -> simple(TypeCategory.TIME, base, display);
            case Types.TIMESTAMP -> timestamp(TypeCategory.TIMESTAMP, base, display, fraction(scale, 6));
            case Types.TIMESTAMP_WITH_TIMEZONE -> timestamp(TypeCategory.TIMESTAMP_TZ, base, display, fraction(scale, 6));
            case Types.BOOLEAN, Types.BIT -> simple(TypeCategory.BOOLEAN, base, display);
            case Types.SQLXML -> simple(TypeCategory.XML, base, display);
            default -> simple(TypeCategory.OTHER, base, display);
        };
    }

    private static NormalizedType simple(TypeCategory category, String base, String display) {
        return new NormalizedType(category, base, display, false, null, null, null, null);
    }

    private static NormalizedType integer(String base, String display, int digits) {
        return new NormalizedType(TypeCategory.INTEGER, base, display, false, null, digits, 0, null);
    }

    /** Oracle {@code NUMBER} without precision (reported as 0 or scale -127) is an unbounded decimal. */
    private static NormalizedType decimal(String base, String display, Integer size, Integer scale) {
        boolean unbounded = size == null || size <= 0 || (scale != null && scale == -127);
        Integer precision = unbounded ? null : size;
        Integer effectiveScale = unbounded ? null : (scale == null || scale < 0 ? 0 : scale);
        return new NormalizedType(TypeCategory.DECIMAL, base, display, false, null, precision, effectiveScale, null);
    }

    private static NormalizedType floating(String base, String display, int bits) {
        return new NormalizedType(TypeCategory.FLOAT, base, display, false, null, bits, null, null);
    }

    private static NormalizedType string(String base, String display, Integer size, boolean unicode) {
        if (size == null || size <= 0 || size >= UNBOUNDED_LENGTH) {
            return new NormalizedType(TypeCategory.CLOB, base, display, unicode, null, null, null, null);
        }
        return new NormalizedType(TypeCategory.STRING, base, display, unicode, size.longValue(), null, null, null);
    }

    private static NormalizedType binary(String base, String display, Integer size) {
        if (size == null || size <= 0 || size >= UNBOUNDED_LENGTH) {
            return simple(TypeCategory.BLOB, base, display);
        }
        return new NormalizedType(TypeCategory.BINARY, base, display, false, size.longValue(), null, null, null);
    }

    private static NormalizedType timestamp(TypeCategory category, String base, String display, int fractional) {
        return new NormalizedType(category, base, display, false, null, null, null, fractional);
    }

    private static int fraction(Integer scale, int fallback) {
        return scale == null || scale < 0 || scale > 12 ? fallback : scale;
    }
}
