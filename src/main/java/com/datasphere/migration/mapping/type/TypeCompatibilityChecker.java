package com.datasphere.migration.mapping.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic data-type compatibility rules between any two databases. The rules work on
 * {@link TypeCategory} plus length / precision / scale, never on vendor names, so any source can be paired
 * with any target. Messages are written for end users.
 */
public final class TypeCompatibilityChecker {

    private TypeCompatibilityChecker() {
    }

    public static TypeCompatibility check(NormalizedType src, NormalizedType tgt) {
        Verdict v = new Verdict();
        compare(src, tgt, v);
        if (v.level == CompatibilityLevel.COMPATIBLE && v.warnings.isEmpty() && identicalDefinition(src, tgt)) {
            v.level = CompatibilityLevel.IDENTICAL;
        }
        return new TypeCompatibility(v.level, v.size, List.copyOf(v.notes), List.copyOf(v.warnings), v.suggested);
    }

    /** Checks the pair as it will look after the user's conversion has been applied. */
    public static TypeCompatibility check(NormalizedType src, NormalizedType tgt, Transformation transformation) {
        if (transformation == null || transformation == Transformation.NONE) {
            return check(src, tgt);
        }
        if (!transformation.isApplicable(src.category(), tgt.category())) {
            return new TypeCompatibility(CompatibilityLevel.INCOMPATIBLE, 0, List.of(),
                    List.of("The conversion '" + transformation.label() + "' cannot be used to turn "
                            + src.category().plainName() + " into " + tgt.category().plainName() + "."),
                    Transformation.suggestFor(src.category(), tgt.category()));
        }
        TypeCompatibility result = check(transformation.effectiveSource(src), tgt);
        List<String> notes = new ArrayList<>(result.notes());
        List<String> warnings = new ArrayList<>(result.warnings());
        notes.add("Conversion applied: " + transformation.label() + ".");
        CompatibilityLevel level = result.level();
        if ((transformation == Transformation.PARSE_NUMBER || transformation == Transformation.PARSE_DATE)
                && !level.isWorseThan(CompatibilityLevel.CONDITIONAL)) {
            warnings.add("Values that cannot be converted will be reported during the dry run and migration.");
            level = CompatibilityLevel.CONDITIONAL;
        }
        if (level == CompatibilityLevel.IDENTICAL) {
            level = CompatibilityLevel.COMPATIBLE;
        }
        return new TypeCompatibility(level, result.sizeFactor(), notes, warnings, result.suggestedTransformation());
    }

    private static void compare(NormalizedType s, NormalizedType t, Verdict v) {
        TypeCategory sc = s.category();
        TypeCategory tc = t.category();

        if (sc.isExactNumeric() && tc.isExactNumeric()) {
            exactToExact(s, t, v);
        } else if (sc.isExactNumeric() && tc == TypeCategory.FLOAT) {
            if (s.precision() == null || s.precision() > 15) {
                v.condition(0.5, "A floating-point column keeps about 15 significant digits; longer numbers lose precision.");
            }
        } else if (sc == TypeCategory.FLOAT && tc == TypeCategory.FLOAT) {
            if (s.precision() != null && t.precision() != null && t.precision() < s.precision()) {
                v.condition(0.5, "The target floating-point type is less precise than the source.");
            }
        } else if (sc == TypeCategory.FLOAT && tc.isExactNumeric()) {
            v.condition(t.precision() == null ? 1.0 : 0.5,
                    "Floating-point values will be rounded to the target's digits and decimal places.");
        } else if (sc.isText() && tc.isText()) {
            textToText(s, t, v);
        } else if (sc.isDateTime() && tc.isDateTime() || sc == TypeCategory.TIME && tc == TypeCategory.TIME) {
            dateTime(s, t, v);
        } else if (sc.isBinary() && tc.isBinary()) {
            binary(s, t, v);
        } else if (sc == TypeCategory.BOOLEAN && tc == TypeCategory.BOOLEAN) {
            return;
        } else if (sc == TypeCategory.BOOLEAN && tc.isExactNumeric()) {
            v.condition(1.0, "TRUE and FALSE will be stored as 1 and 0.");
        } else if (sc.isExactNumeric() && tc == TypeCategory.BOOLEAN) {
            v.condition(1.0, "Only the values 0 and 1 can be stored in a yes/no column.");
        } else if (sc == TypeCategory.UUID && tc == TypeCategory.UUID) {
            return;
        } else if (sc == TypeCategory.UUID && tc.isText()) {
            v.condition(t.length() != null && t.length() < 36 ? 0.0 : 1.0,
                    t.length() != null && t.length() < 36
                            ? "A unique identifier needs 36 characters of text but the target allows " + t.length() + "."
                            : "The unique identifier will be stored as 36 characters of text.");
        } else if (sc.isText() && tc == TypeCategory.UUID) {
            v.condition(1.0, "Every text value must be a valid unique identifier.");
        } else if (sc == TypeCategory.UUID && tc == TypeCategory.BINARY) {
            v.condition(t.length() != null && t.length() < 16 ? 0.0 : 1.0,
                    "The unique identifier will be stored as 16 bytes of binary data.");
        } else if (sc == TypeCategory.XML && tc == TypeCategory.XML || sc == TypeCategory.JSON && tc == TypeCategory.JSON) {
            return;
        } else if ((sc == TypeCategory.XML || sc == TypeCategory.JSON) && tc.isText()) {
            v.condition(tc == TypeCategory.CLOB ? 1.0 : 0.5,
                    "The " + sc.plainName() + " will be stored as plain text.");
        } else if (sc.isText() && (tc == TypeCategory.XML || tc == TypeCategory.JSON)) {
            v.condition(1.0, "Every text value must be a valid " + tc.plainName() + ".");
        } else if (sc == TypeCategory.OTHER && tc == TypeCategory.OTHER
                && Objects.equals(s.nativeName(), t.nativeName())) {
            return;
        } else {
            v.incompatible(s, t);
        }
    }

    private static void exactToExact(NormalizedType s, NormalizedType t, Verdict v) {
        if (t.precision() == null) {
            v.notes.add("The target accepts numbers of any size.");
            return;
        }
        if (s.precision() == null) {
            v.condition(0.5, "The source number has no fixed size, so some values may not fit in " + t.display() + ".");
            return;
        }
        int sourceInt = s.integerDigits();
        int targetInt = t.integerDigits();
        int sourceScale = s.scale() == null ? 0 : s.scale();
        int targetScale = t.scale() == null ? 0 : t.scale();
        if (targetInt < sourceInt) {
            v.condition(0.5, "The target holds at most " + targetInt + " digits before the decimal point but the source "
                    + "can hold " + sourceInt + ". Larger values would be rejected.");
        }
        if (targetScale < sourceScale) {
            v.condition(0.5, "The target keeps " + targetScale + " decimal place(s) but the source has " + sourceScale
                    + ". Extra decimals would be rounded off.");
        }
    }

    private static void textToText(NormalizedType s, NormalizedType t, Verdict v) {
        if (t.category() == TypeCategory.STRING) {
            if (s.category() == TypeCategory.CLOB || s.length() == null) {
                v.condition(0.5, "The source text can be any length but the target holds at most " + t.length()
                        + " characters.");
            } else if (s.length() > t.length()) {
                v.condition(0.5, "The source allows up to " + s.length() + " characters but the target holds only "
                        + t.length() + ". Longer values would be rejected or cut.");
            }
        }
        if (s.unicode() && !t.unicode()) {
            v.condition(1.0, "The source stores international (Unicode) characters; the target may not be able to "
                    + "store all of them.");
        }
    }

    private static void dateTime(NormalizedType s, NormalizedType t, Verdict v) {
        TypeCategory sc = s.category();
        TypeCategory tc = t.category();
        if (sc == tc) {
            fractional(s, t, v);
            return;
        }
        if (sc == TypeCategory.DATE && tc == TypeCategory.TIMESTAMP) {
            v.notes.add("The time of day will be set to midnight.");
        } else if (sc == TypeCategory.DATE && tc == TypeCategory.TIMESTAMP_TZ) {
            v.condition(1.0, "The time of day will be set to midnight in the session time zone.");
        } else if ((sc == TypeCategory.TIMESTAMP || sc == TypeCategory.TIMESTAMP_TZ) && tc == TypeCategory.DATE) {
            v.condition(1.0, "The time of day will be dropped; only the date is kept.");
        } else if (sc == TypeCategory.TIMESTAMP && tc == TypeCategory.TIMESTAMP_TZ) {
            v.condition(1.0, "The source has no time zone; the session time zone will be assumed.");
            fractional(s, t, v);
        } else if (sc == TypeCategory.TIMESTAMP_TZ && tc == TypeCategory.TIMESTAMP) {
            v.condition(1.0, "The time zone will be dropped.");
            fractional(s, t, v);
        } else {
            v.incompatible(s, t);
        }
    }

    private static void fractional(NormalizedType s, NormalizedType t, Verdict v) {
        if (s.fractionalDigits() != null && t.fractionalDigits() != null && s.fractionalDigits() > t.fractionalDigits()) {
            v.condition(0.5, "The source keeps " + s.fractionalDigits() + " fractional-second digits but the target "
                    + "keeps " + t.fractionalDigits() + ". Extra digits would be rounded.");
        } else if (s.category() == TypeCategory.TIMESTAMP || s.category() == TypeCategory.TIMESTAMP_TZ) {
            v.notes.add("The date and time will be converted using the standard timestamp rules.");
        }
    }

    private static void binary(NormalizedType s, NormalizedType t, Verdict v) {
        if (s.category() == TypeCategory.BLOB && t.category() == TypeCategory.BLOB) {
            return;
        }
        boolean sameFamily = Objects.equals(s.nativeName(), t.nativeName());
        boolean tooSmall = t.category() == TypeCategory.BINARY
                && (s.category() == TypeCategory.BLOB || s.length() == null || s.length() > t.length());
        if (tooSmall) {
            v.condition(0.5, "The source binary data can be larger than the " + t.length() + " bytes the target holds.");
        } else if (!sameFamily) {
            v.condition(1.0, "Binary data is copied byte for byte; verify it with a checksum after migration.");
        }
    }

    private static boolean identicalDefinition(NormalizedType s, NormalizedType t) {
        return s.category() == t.category()
                && Objects.equals(s.nativeName(), t.nativeName())
                && Objects.equals(s.length(), t.length())
                && Objects.equals(s.precision(), t.precision())
                && Objects.equals(s.scale(), t.scale())
                && Objects.equals(s.fractionalDigits(), t.fractionalDigits())
                && s.unicode() == t.unicode();
    }

    /** Mutable accumulator for one comparison. */
    private static final class Verdict {
        CompatibilityLevel level = CompatibilityLevel.COMPATIBLE;
        double size = 1.0;
        final List<String> notes = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        Transformation suggested;

        void condition(double sizeFactor, String warning) {
            if (!level.isWorseThan(CompatibilityLevel.CONDITIONAL)) {
                level = CompatibilityLevel.CONDITIONAL;
            }
            size = Math.min(size, sizeFactor);
            warnings.add(warning);
        }

        void incompatible(NormalizedType s, NormalizedType t) {
            level = CompatibilityLevel.INCOMPATIBLE;
            size = 0;
            suggested = Transformation.suggestFor(s.category(), t.category());
            warnings.add("A " + s.category().plainName() + " (" + s.display() + ") cannot be stored directly in a "
                    + t.category().plainName() + " column (" + t.display() + ").");
            if (suggested != null) {
                warnings.add("Choose the conversion '" + suggested.label() + "' if you want to map these columns.");
            }
        }
    }
}
