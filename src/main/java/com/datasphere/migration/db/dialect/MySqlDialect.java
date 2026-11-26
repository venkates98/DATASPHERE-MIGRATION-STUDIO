package com.datasphere.migration.db.dialect;

import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.TypeCategory;
import java.util.List;

public final class MySqlDialect extends AbstractSqlDialect {

    public MySqlDialect() {
        super('`', '`');
    }

    @Override
    public PaginationStyle paginationStyle() {
        return PaginationStyle.LIMIT_OFFSET;
    }

    @Override
    public List<String> unsupportedTypeNotes() {
        return List.of("Oracle XMLTYPE / NCLOB have no native MySQL equivalent (stored as LONGTEXT).",
                "PostgreSQL arrays, ranges and geometric types are unsupported.",
                "SQL Server hierarchyid / geography are unsupported.",
                "MySQL DATETIME has no time zone; TIMESTAMP WITH TIME ZONE loses zone information.");
    }

    @Override
    public NativeTypeSpec mapType(NormalizedType source) {
        return switch (source.category()) {
            case INTEGER -> {
                int p = source.precision() == null ? 10 : source.precision();
                if (p <= 2) {
                    yield NativeTypeSpec.numeric("TINYINT", "tinyint", TypeCategory.INTEGER, 3, 0, List.of(), false);
                }
                if (p <= 4) {
                    yield NativeTypeSpec.numeric("SMALLINT", "smallint", TypeCategory.INTEGER, 5, 0, List.of(), false);
                }
                if (p <= 9) {
                    yield NativeTypeSpec.numeric("INT", "int", TypeCategory.INTEGER, 10, 0, List.of(), false);
                }
                boolean loss = p > 19;
                yield NativeTypeSpec.numeric("BIGINT", "bigint", TypeCategory.INTEGER, 19, 0,
                        loss ? List.of("MySQL BIGINT holds 19 digits.") : List.of(), loss);
            }
            case DECIMAL -> {
                int p = cap(source.precision(), 65);
                int s = source.scale() == null ? 0 : Math.min(source.scale(), Math.min(p, 30));
                boolean loss = source.precision() != null && source.precision() > 65;
                yield NativeTypeSpec.numeric("DECIMAL(" + p + "," + s + ")", "decimal", TypeCategory.DECIMAL, p, s,
                        loss ? List.of("MySQL DECIMAL is limited to 65 digits.") : List.of(), loss);
            }
            case FLOAT -> NativeTypeSpec.numeric("DOUBLE", "double", TypeCategory.FLOAT, 53, null, List.of(), false);
            case STRING -> {
                if (source.length() == null || source.length() > 16383) {
                    yield NativeTypeSpec.text("LONGTEXT", "longtext", null, true, List.of(), false);
                }
                yield NativeTypeSpec.text("VARCHAR(" + source.length() + ")", "varchar", source.length(), true,
                        List.of("Assumes utf8mb4 so Unicode is preserved."), false);
            }
            case CLOB -> NativeTypeSpec.of("LONGTEXT", "longtext", TypeCategory.CLOB);
            case BINARY -> {
                if (source.length() == null || source.length() > 65535) {
                    yield NativeTypeSpec.of("LONGBLOB", "longblob", TypeCategory.BLOB);
                }
                yield new NativeTypeSpec("VARBINARY(" + source.length() + ")", "varbinary", TypeCategory.BINARY,
                        source.length(), null, null, null, false, false, null, false, List.of());
            }
            case BLOB -> NativeTypeSpec.of("LONGBLOB", "longblob", TypeCategory.BLOB);
            case DATE -> NativeTypeSpec.of("DATE", "date", TypeCategory.DATE);
            case TIME -> NativeTypeSpec.temporal("TIME(" + frac(source, 6) + ")", "time", TypeCategory.TIME,
                    frac(source, 6), List.of(), false);
            case TIMESTAMP -> NativeTypeSpec.temporal("DATETIME(" + frac(source, 6) + ")", "datetime",
                    TypeCategory.TIMESTAMP, frac(source, 6), List.of(), false);
            case TIMESTAMP_TZ -> NativeTypeSpec.temporal("DATETIME(" + frac(source, 6) + ")", "datetime",
                    TypeCategory.TIMESTAMP, frac(source, 6),
                    List.of("MySQL DATETIME has no time zone; the zone is dropped."), true);
            case BOOLEAN -> NativeTypeSpec.numeric("TINYINT(1)", "tinyint", TypeCategory.BOOLEAN, 1, 0, List.of(),
                    false);
            case UUID -> NativeTypeSpec.text("CHAR(36)", "char", 36L, false,
                    List.of("Unique identifiers are stored as CHAR(36)."), false);
            case XML -> NativeTypeSpec.of("LONGTEXT", "longtext", TypeCategory.CLOB)
                    .withWarning("XML is stored as LONGTEXT.");
            case JSON -> NativeTypeSpec.of("JSON", "json", TypeCategory.JSON);
            case OTHER -> NativeTypeSpec.unsupported(TypeCategory.OTHER,
                    "There is no MySQL type for " + source.display() + ".");
        };
    }

    private static int frac(NormalizedType source, int max) {
        return source.fractionalDigits() == null ? Math.min(6, max) : Math.min(source.fractionalDigits(), max);
    }
}
