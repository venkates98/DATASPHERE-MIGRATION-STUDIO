package com.datasphere.migration.engine;

import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.mapping.type.Transformation;
import com.datasphere.migration.mapping.type.TypeCategory;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Applies a closed {@link Transformation} to a JDBC value. No SQL is generated. */
public final class ValueConverter {

    private ValueConverter() {
    }

    public static Object apply(Object value, Transformation transformation, String parameter, TypeCategory target) {
        if (transformation == null || transformation == Transformation.NONE) {
            return value;
        }
        if (value == null) {
            if (transformation == Transformation.DEFAULT_IF_NULL) {
                return parseDefault(parameter, target);
            }
            return null;
        }
        return switch (transformation) {
            case NONE, DEFAULT_IF_NULL -> value;
            case TRIM -> value.toString().trim();
            case UPPERCASE -> value.toString().toUpperCase(Locale.ROOT);
            case LOWERCASE -> value.toString().toLowerCase(Locale.ROOT);
            case TO_STRING -> value.toString();
            case PARSE_NUMBER -> parseNumber(value.toString());
            case PARSE_DATE -> parseDate(value.toString(), parameter, target);
            case TRUNCATE_TO_DATE -> truncate(value);
        };
    }

    private static Object parseNumber(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ApplicationException(ErrorCode.DATA_CONV_001, "Not a number: " + text,
                    java.util.List.of("The text '" + text + "' is not a number."));
        }
    }

    private static Object parseDate(String text, String pattern, TypeCategory target) {
        try {
            if (pattern == null || pattern.isBlank()) {
                if (target == TypeCategory.DATE) {
                    return Date.valueOf(LocalDate.parse(text.trim()));
                }
                return Timestamp.valueOf(LocalDateTime.parse(text.trim()));
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
            if (target == TypeCategory.DATE) {
                return Date.valueOf(LocalDate.parse(text.trim(), fmt));
            }
            return Timestamp.valueOf(LocalDateTime.parse(text.trim(), fmt));
        } catch (RuntimeException e) {
            throw new ApplicationException(ErrorCode.DATA_CONV_001, "Not a date: " + text,
                    java.util.List.of("The text '" + text + "' is not a valid date or date-time."));
        }
    }

    private static Object truncate(Object value) {
        if (value instanceof Timestamp ts) {
            return Date.valueOf(ts.toLocalDateTime().toLocalDate());
        }
        if (value instanceof java.util.Date d) {
            return new Date(d.getTime());
        }
        return value;
    }

    private static Object parseDefault(String parameter, TypeCategory target) {
        if (parameter == null) {
            return null;
        }
        String v = parameter.trim();
        if (target.isNumeric()) {
            return new BigDecimal(v);
        }
        if (target == TypeCategory.DATE) {
            return Date.valueOf(LocalDate.parse(v));
        }
        if (target == TypeCategory.BOOLEAN) {
            return "1".equals(v) || "true".equalsIgnoreCase(v);
        }
        return v;
    }
}
