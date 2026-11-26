package com.datasphere.migration.mapping.type;

/**
 * A column type reduced to what matters for compatibility decisions.
 *
 * @param nativeName       lower-case vendor type name without size, e.g. {@code nvarchar}, {@code number}
 * @param display          text for messages, e.g. {@code NVARCHAR(100)}
 * @param unicode          text types: can the column hold any Unicode character
 * @param length           text/binary: maximum characters or bytes ({@code null} = unbounded)
 * @param precision        exact numerics: total digits ({@code null} = unbounded); floats: mantissa bits
 * @param scale            exact numerics: digits after the decimal point
 * @param fractionalDigits date/time types: fractional-second digits
 */
public record NormalizedType(
        TypeCategory category,
        String nativeName,
        String display,
        boolean unicode,
        Long length,
        Integer precision,
        Integer scale,
        Integer fractionalDigits) {

    /** Plain text type used to model the result of converting a value to text. */
    public static NormalizedType text(long length) {
        return new NormalizedType(TypeCategory.STRING, "text", "TEXT(" + length + ")", false, length, null, null, null);
    }

    public static NormalizedType of(TypeCategory category, String display) {
        return new NormalizedType(category, display.toLowerCase(), display, false, null, null, null, null);
    }

    /** Digits available before the decimal point for exact numerics, or {@code null} when unbounded. */
    public Integer integerDigits() {
        return precision == null ? null : precision - (scale == null ? 0 : scale);
    }
}
