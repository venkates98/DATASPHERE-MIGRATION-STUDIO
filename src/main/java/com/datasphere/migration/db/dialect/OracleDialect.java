package com.datasphere.migration.db.dialect;

import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.TypeCategory;
import java.util.List;

public final class OracleDialect extends AbstractSqlDialect {

    public OracleDialect() {
        super('"', '"');
    }

    @Override
    public PaginationStyle paginationStyle() {
        return PaginationStyle.OFFSET_FETCH;
    }

    @Override
    public List<String> unsupportedTypeNotes() {
        return List.of("PostgreSQL arrays, ranges and geometric types have no Oracle equivalent.",
                "SQL Server hierarchyid / geography / geometry are unsupported.",
                "MySQL SET and spatial types are unsupported.");
    }

    @Override
    public NativeTypeSpec mapType(NormalizedType source) {
        return switch (source.category()) {
            case INTEGER -> {
                int p = cap(source.precision() == null ? 10 : source.precision(), 38);
                boolean loss = source.precision() != null && source.precision() > 38;
                yield NativeTypeSpec.numeric("NUMBER(" + p + ")", "number", TypeCategory.INTEGER, p, 0,
                        loss ? List.of("Oracle NUMBER is limited to 38 digits.") : List.of(), loss);
            }
            case DECIMAL -> {
                int p = cap(source.precision(), 38);
                int s = source.scale() == null ? 0 : Math.min(source.scale(), p);
                boolean loss = source.precision() != null && source.precision() > 38;
                yield NativeTypeSpec.numeric("NUMBER(" + p + "," + s + ")", "number", TypeCategory.DECIMAL, p, s,
                        loss ? List.of("Oracle NUMBER is limited to 38 digits.") : List.of(), loss);
            }
            case FLOAT -> NativeTypeSpec.numeric("BINARY_DOUBLE", "binary_double", TypeCategory.FLOAT, 53, null,
                    List.of(), false);
            case STRING -> text(source);
            case CLOB -> NativeTypeSpec.of(source.unicode() ? "NCLOB" : "CLOB", source.unicode() ? "nclob" : "clob",
                    TypeCategory.CLOB);
            case BINARY -> {
                if (source.length() == null || source.length() > 2000) {
                    yield NativeTypeSpec.of("BLOB", "blob", TypeCategory.BLOB);
                }
                yield new NativeTypeSpec("RAW(" + source.length() + ")", "raw", TypeCategory.BINARY, source.length(),
                        null, null, null, false, false, null, false, List.of());
            }
            case BLOB -> NativeTypeSpec.of("BLOB", "blob", TypeCategory.BLOB);
            case DATE -> NativeTypeSpec.of("DATE", "date", TypeCategory.TIMESTAMP)
                    .withWarning("Oracle DATE includes a time of day.");
            case TIME -> NativeTypeSpec.temporal("TIMESTAMP(0)", "timestamp", TypeCategory.TIMESTAMP, 0,
                    List.of("Oracle has no TIME type; a TIMESTAMP without a date is used."), true);
            case TIMESTAMP -> NativeTypeSpec.temporal("TIMESTAMP(" + frac(source, 9) + ")", "timestamp",
                    TypeCategory.TIMESTAMP, frac(source, 9), List.of(), false);
            case TIMESTAMP_TZ -> NativeTypeSpec.temporal("TIMESTAMP(" + frac(source, 9) + ") WITH TIME ZONE",
                    "timestamp with time zone", TypeCategory.TIMESTAMP_TZ, frac(source, 9), List.of(), false);
            case BOOLEAN -> NativeTypeSpec.numeric("NUMBER(1)", "number", TypeCategory.INTEGER, 1, 0,
                    List.of("Oracle has no BOOLEAN column type in this mapping; TRUE/FALSE become 1 and 0."),
                    false);
            case UUID -> NativeTypeSpec.text("VARCHAR2(36)", "varchar2", 36L, false,
                    List.of("Unique identifiers are stored as 36-character text."), false);
            case XML -> NativeTypeSpec.of("XMLTYPE", "xmltype", TypeCategory.XML);
            case JSON -> NativeTypeSpec.of("CLOB", "clob", TypeCategory.CLOB)
                    .withWarning("JSON is stored as CLOB. Oracle JSON type is not assumed.");
            case OTHER -> NativeTypeSpec.unsupported(TypeCategory.OTHER,
                    "There is no Oracle type for " + source.display() + ".");
        };
    }

    private static NativeTypeSpec text(NormalizedType source) {
        boolean unicode = source.unicode();
        if (source.length() == null || source.length() > (unicode ? 2000 : 4000)) {
            return NativeTypeSpec.of(unicode ? "NCLOB" : "CLOB", unicode ? "nclob" : "clob", TypeCategory.CLOB);
        }
        long n = source.length();
        String ddl = unicode ? "NVARCHAR2(" + n + ")" : "VARCHAR2(" + n + ")";
        return NativeTypeSpec.text(ddl, unicode ? "nvarchar2" : "varchar2", n, unicode, List.of(), false);
    }

    private static int frac(NormalizedType source, int max) {
        return source.fractionalDigits() == null ? Math.min(6, max) : Math.min(source.fractionalDigits(), max);
    }
}
