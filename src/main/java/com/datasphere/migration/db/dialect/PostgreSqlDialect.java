package com.datasphere.migration.db.dialect;

import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.TypeCategory;
import java.util.List;

public final class PostgreSqlDialect extends AbstractSqlDialect {

    public PostgreSqlDialect() {
        super('"', '"');
    }

    @Override
    public PaginationStyle paginationStyle() {
        return PaginationStyle.LIMIT_OFFSET;
    }

    @Override
    public List<String> unsupportedTypeNotes() {
        return List.of("SQL Server hierarchyid / geography are unsupported.",
                "Oracle XMLTYPE is mapped to XML; spatial and object types are unsupported.",
                "MySQL SET and spatial types are unsupported.");
    }

    @Override
    public NativeTypeSpec mapType(NormalizedType source) {
        return switch (source.category()) {
            case INTEGER -> {
                int p = source.precision() == null ? 10 : source.precision();
                if (p <= 4) {
                    yield NativeTypeSpec.numeric("SMALLINT", "smallint", TypeCategory.INTEGER, 5, 0, List.of(), false);
                }
                if (p <= 9) {
                    yield NativeTypeSpec.numeric("INTEGER", "integer", TypeCategory.INTEGER, 10, 0, List.of(), false);
                }
                boolean loss = p > 19;
                yield NativeTypeSpec.numeric("BIGINT", "bigint", TypeCategory.INTEGER, 19, 0,
                        loss ? List.of("PostgreSQL BIGINT holds 19 digits.") : List.of(), loss);
            }
            case DECIMAL -> {
                int p = source.precision() == null ? 38 : source.precision();
                int s = source.scale() == null ? 0 : source.scale();
                yield NativeTypeSpec.numeric("NUMERIC(" + p + "," + s + ")", "numeric", TypeCategory.DECIMAL, p, s,
                        List.of(), false);
            }
            case FLOAT -> NativeTypeSpec.numeric("DOUBLE PRECISION", "double precision", TypeCategory.FLOAT, 53, null,
                    List.of(), false);
            case STRING -> {
                if (source.length() == null || source.length() > 10485760) {
                    yield NativeTypeSpec.text("TEXT", "text", null, true, List.of(), false);
                }
                yield NativeTypeSpec.text("VARCHAR(" + source.length() + ")", "varchar", source.length(), true,
                        List.of(), false);
            }
            case CLOB -> NativeTypeSpec.text("TEXT", "text", null, true, List.of(), false);
            case BINARY, BLOB -> NativeTypeSpec.of("BYTEA", "bytea", TypeCategory.BLOB);
            case DATE -> NativeTypeSpec.of("DATE", "date", TypeCategory.DATE);
            case TIME -> NativeTypeSpec.temporal("TIME(" + frac(source, 6) + ")", "time", TypeCategory.TIME,
                    frac(source, 6), List.of(), false);
            case TIMESTAMP -> NativeTypeSpec.temporal("TIMESTAMP(" + frac(source, 6) + ")", "timestamp",
                    TypeCategory.TIMESTAMP, frac(source, 6), List.of(), false);
            case TIMESTAMP_TZ -> NativeTypeSpec.temporal("TIMESTAMPTZ(" + frac(source, 6) + ")", "timestamptz",
                    TypeCategory.TIMESTAMP_TZ, frac(source, 6), List.of(), false);
            case BOOLEAN -> NativeTypeSpec.of("BOOLEAN", "boolean", TypeCategory.BOOLEAN);
            case UUID -> NativeTypeSpec.of("UUID", "uuid", TypeCategory.UUID);
            case XML -> NativeTypeSpec.of("XML", "xml", TypeCategory.XML);
            case JSON -> NativeTypeSpec.of("JSONB", "jsonb", TypeCategory.JSON);
            case OTHER -> NativeTypeSpec.unsupported(TypeCategory.OTHER,
                    "There is no PostgreSQL type for " + source.display() + ".");
        };
    }

    private static int frac(NormalizedType source, int max) {
        return source.fractionalDigits() == null ? Math.min(6, max) : Math.min(source.fractionalDigits(), max);
    }
}
