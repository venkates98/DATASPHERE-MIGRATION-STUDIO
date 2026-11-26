package com.datasphere.migration.db.dialect;

import com.datasphere.migration.mapping.type.TypeCategory;
import java.util.List;

/**
 * A vendor-native column type proposed for a normalised source type. {@code unsupported} means the engine
 * will not invent a fallback type.
 */
public record NativeTypeSpec(
        String ddl,
        String nativeName,
        TypeCategory category,
        Long length,
        Integer precision,
        Integer scale,
        Integer fractionalDigits,
        boolean unicode,
        boolean unsupported,
        String unsupportedReason,
        boolean potentialDataLoss,
        List<String> warnings) {

    public static NativeTypeSpec of(String ddl, String nativeName, TypeCategory category) {
        return new NativeTypeSpec(ddl, nativeName, category, null, null, null, null, false, false, null, false,
                List.of());
    }

    public static NativeTypeSpec text(String ddl, String nativeName, Long length, boolean unicode,
                                      List<String> warnings, boolean loss) {
        return new NativeTypeSpec(ddl, nativeName, TypeCategory.STRING, length, null, null, null, unicode, false,
                null, loss, warnings);
    }

    public static NativeTypeSpec numeric(String ddl, String nativeName, TypeCategory category, Integer precision,
                                         Integer scale, List<String> warnings, boolean loss) {
        return new NativeTypeSpec(ddl, nativeName, category, null, precision, scale, null, false, false, null, loss,
                warnings);
    }

    public static NativeTypeSpec temporal(String ddl, String nativeName, TypeCategory category, Integer fraction,
                                          List<String> warnings, boolean loss) {
        return new NativeTypeSpec(ddl, nativeName, category, null, null, null, fraction, false, false, null, loss,
                warnings);
    }

    public static NativeTypeSpec unsupported(TypeCategory category, String reason) {
        return new NativeTypeSpec(null, null, category, null, null, null, null, false, true, reason, true, List.of());
    }

    public NativeTypeSpec withWarning(String warning) {
        java.util.ArrayList<String> next = new java.util.ArrayList<>(warnings);
        next.add(warning);
        return new NativeTypeSpec(ddl, nativeName, category, length, precision, scale, fractionalDigits, unicode,
                unsupported, unsupportedReason, potentialDataLoss, List.copyOf(next));
    }
}
