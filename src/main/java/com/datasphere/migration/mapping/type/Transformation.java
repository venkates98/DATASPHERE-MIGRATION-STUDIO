package com.datasphere.migration.mapping.type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The closed set of conversions a user may attach to a column mapping. Each one is a named, validated rule;
 * there is no free-form expression or SQL, so a mapping can never smuggle arbitrary code into a migration.
 */
public enum Transformation {

    NONE("No conversion", "Copy the value as it is."),
    TRIM("Trim spaces", "Remove leading and trailing spaces from text."),
    UPPERCASE("Upper case", "Convert text to upper case."),
    LOWERCASE("Lower case", "Convert text to lower case."),
    TO_STRING("Convert to text", "Write the value as text."),
    PARSE_NUMBER("Convert text to number", "Read the text as a number. Text that is not a number is reported."),
    PARSE_DATE("Convert text to date/time",
            "Read the text as a date or date and time (ISO format, or the pattern you enter)."),
    TRUNCATE_TO_DATE("Keep the date only", "Drop the time of day and keep the date."),
    DEFAULT_IF_NULL("Default when empty", "Use the value you enter when the source value is empty (NULL).");

    private final String label;
    private final String description;

    Transformation(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean parameterRequired() {
        return this == DEFAULT_IF_NULL;
    }

    public boolean parameterAllowed() {
        return this == DEFAULT_IF_NULL || this == PARSE_DATE;
    }

    /** Can this conversion be applied when reading a {@code src} value and writing a {@code tgt} column? */
    public boolean isApplicable(TypeCategory src, TypeCategory tgt) {
        return switch (this) {
            case NONE, DEFAULT_IF_NULL -> true;
            case TRIM, UPPERCASE, LOWERCASE -> src.isText() && tgt.isText();
            case TO_STRING -> tgt.isText() && src != TypeCategory.BINARY && src != TypeCategory.BLOB
                    && src != TypeCategory.OTHER;
            case PARSE_NUMBER -> src == TypeCategory.STRING && tgt.isNumeric();
            case PARSE_DATE -> src == TypeCategory.STRING && tgt.isDateTime();
            case TRUNCATE_TO_DATE -> (src == TypeCategory.TIMESTAMP || src == TypeCategory.TIMESTAMP_TZ)
                    && tgt == TypeCategory.DATE;
        };
    }

    /** The conversion that would make an otherwise incompatible pair workable, if there is one. */
    public static Transformation suggestFor(TypeCategory src, TypeCategory tgt) {
        for (Transformation t : new Transformation[]{PARSE_NUMBER, PARSE_DATE, TRUNCATE_TO_DATE, TO_STRING}) {
            if (t.isApplicable(src, tgt)) {
                return t;
            }
        }
        return null;
    }

    /** What the source value looks like after this conversion, used to re-check compatibility with the target. */
    public NormalizedType effectiveSource(NormalizedType src) {
        return switch (this) {
            case TO_STRING -> NormalizedType.text(estimateTextLength(src));
            case PARSE_NUMBER -> new NormalizedType(TypeCategory.DECIMAL, "number", "NUMBER", false, null, null, null,
                    null);
            case PARSE_DATE -> new NormalizedType(TypeCategory.TIMESTAMP, "timestamp", "TIMESTAMP", false, null, null,
                    null, 6);
            case TRUNCATE_TO_DATE -> NormalizedType.of(TypeCategory.DATE, "DATE");
            default -> src;
        };
    }

    /**
     * @return an error in plain language, or empty if the parameter is acceptable
     */
    public Optional<String> validateParameter(String parameter, TypeCategory target) {
        boolean blank = parameter == null || parameter.isBlank();
        if (parameterRequired() && blank) {
            return Optional.of("'" + label + "' needs a value to use.");
        }
        if (!parameterAllowed() && !blank) {
            return Optional.of("'" + label + "' does not take a value.");
        }
        if (blank) {
            return Optional.empty();
        }
        if (parameter.length() > 100 || parameter.chars().anyMatch(Character::isISOControl)) {
            return Optional.of("The value for '" + label + "' is too long or contains control characters.");
        }
        if (this == PARSE_DATE) {
            try {
                DateTimeFormatter.ofPattern(parameter);
            } catch (IllegalArgumentException e) {
                return Optional.of("'" + parameter + "' is not a valid date pattern (for example yyyy-MM-dd).");
            }
            return Optional.empty();
        }
        if (this == DEFAULT_IF_NULL) {
            return validateDefault(parameter.trim(), target);
        }
        return Optional.empty();
    }

    private static Optional<String> validateDefault(String value, TypeCategory target) {
        try {
            if (target.isNumeric()) {
                new BigDecimal(value);
            } else if (target == TypeCategory.DATE) {
                LocalDate.parse(value);
            } else if (target == TypeCategory.TIMESTAMP) {
                LocalDateTime.parse(value);
            } else if (target == TypeCategory.TIMESTAMP_TZ) {
                OffsetDateTime.parse(value);
            } else if (target == TypeCategory.BOOLEAN) {
                if (!value.matches("(?i)true|false|0|1")) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (RuntimeException e) {
            return Optional.of("The default '" + value + "' is not a valid " + target.plainName() + ".");
        }
        return Optional.empty();
    }

    private static long estimateTextLength(NormalizedType src) {
        return switch (src.category()) {
            case INTEGER -> (src.precision() == null ? 20 : src.precision()) + 1L;
            case DECIMAL -> src.precision() == null ? 40 : src.precision() + 2L;
            case FLOAT -> 32;
            case DATE -> 10;
            case TIME -> 16;
            case TIMESTAMP -> 29;
            case TIMESTAMP_TZ -> 35;
            case BOOLEAN -> 5;
            case UUID -> 36;
            default -> src.length() == null ? 4000 : src.length();
        };
    }
}
