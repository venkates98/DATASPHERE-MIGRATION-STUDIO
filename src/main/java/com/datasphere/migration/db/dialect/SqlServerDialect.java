package com.datasphere.migration.db.dialect;

import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.TypeCategory;
import java.util.List;

public final class SqlServerDialect extends AbstractSqlDialect {

    public SqlServerDialect() {
        super('[', ']');
    }

    @Override
    public PaginationStyle paginationStyle() {
        return PaginationStyle.OFFSET_FETCH;
    }

    @Override
    public List<String> unsupportedTypeNotes() {
        return List.of("Oracle XMLTYPE / spatial / object types have no SQL Server equivalent.",
                "PostgreSQL arrays, ranges, geometric types and composite types are unsupported.");
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
                    yield NativeTypeSpec.numeric("INT", "int", TypeCategory.INTEGER, 10, 0, List.of(), false);
                }
                yield NativeTypeSpec.numeric("BIGINT", "bigint", TypeCategory.INTEGER, 19, 0, List.of(), p > 19);
            }
            case DECIMAL -> {
                int p = cap(source.precision(), 38);
                int s = source.scale() == null ? 0 : Math.min(source.scale(), p);
                boolean loss = source.precision() != null && source.precision() > 38;
                List<String> w = loss ? List.of("SQL Server DECIMAL is limited to 38 digits.") : List.of();
                yield NativeTypeSpec.numeric("DECIMAL(" + p + "," + s + ")", "decimal", TypeCategory.DECIMAL, p, s, w,
                        loss);
            }
            case FLOAT -> NativeTypeSpec.numeric("FLOAT", "float", TypeCategory.FLOAT, 53, null, List.of(), false);
            case STRING -> text(source, false);
            case CLOB -> NativeTypeSpec.text("NVARCHAR(MAX)", "nvarchar", null, true, List.of(), false);
            case BINARY -> binary(source, "VARBINARY");
            case BLOB -> NativeTypeSpec.of("VARBINARY(MAX)", "varbinary", TypeCategory.BLOB);
            case DATE -> NativeTypeSpec.of("DATE", "date", TypeCategory.DATE);
            case TIME -> NativeTypeSpec.temporal("TIME(" + frac(source, 7) + ")", "time", TypeCategory.TIME,
                    frac(source, 7), List.of(), false);
            case TIMESTAMP -> NativeTypeSpec.temporal("DATETIME2(" + frac(source, 7) + ")", "datetime2",
                    TypeCategory.TIMESTAMP, frac(source, 7), List.of(), false);
            case TIMESTAMP_TZ -> NativeTypeSpec.temporal("DATETIMEOFFSET(" + frac(source, 7) + ")", "datetimeoffset",
                    TypeCategory.TIMESTAMP_TZ, frac(source, 7), List.of(), false);
            case BOOLEAN -> NativeTypeSpec.of("BIT", "bit", TypeCategory.BOOLEAN);
            case UUID -> NativeTypeSpec.of("UNIQUEIDENTIFIER", "uniqueidentifier", TypeCategory.UUID);
            case XML -> NativeTypeSpec.of("XML", "xml", TypeCategory.XML);
            case JSON -> NativeTypeSpec.text("NVARCHAR(MAX)", "nvarchar", null, true,
                    List.of("JSON is stored as NVARCHAR(MAX)."), false);
            case OTHER -> NativeTypeSpec.unsupported(TypeCategory.OTHER,
                    "There is no SQL Server type for " + source.display() + ".");
        };
    }

    private static NativeTypeSpec text(NormalizedType source, boolean forceUnicode) {
        boolean unicode = forceUnicode || source.unicode();
        if (source.length() == null || source.length() > 4000) {
            return NativeTypeSpec.text(unicode ? "NVARCHAR(MAX)" : "VARCHAR(MAX)", unicode ? "nvarchar" : "varchar",
                    null, unicode, List.of(), false);
        }
        long n = source.length();
        String ddl = (unicode ? "NVARCHAR(" : "VARCHAR(") + n + ")";
        return NativeTypeSpec.text(ddl, unicode ? "nvarchar" : "varchar", n, unicode, List.of(), false);
    }

    private static NativeTypeSpec binary(NormalizedType source, String name) {
        if (source.length() == null || source.length() > 8000) {
            return NativeTypeSpec.of("VARBINARY(MAX)", "varbinary", TypeCategory.BLOB);
        }
        return new NativeTypeSpec(name + "(" + source.length() + ")", name.toLowerCase(), TypeCategory.BINARY,
                source.length(), null, null, null, false, false, null, false, List.of());
    }

    private static int frac(NormalizedType source, int max) {
        return source.fractionalDigits() == null ? Math.min(7, max) : Math.min(source.fractionalDigits(), max);
    }
}
