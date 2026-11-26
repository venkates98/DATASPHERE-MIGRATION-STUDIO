package com.datasphere.migration.mapping.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits column names into upper-case words so that {@code EMPLOYEE_ID}, {@code employeeId},
 * {@code Employee Id} and {@code EMPLOYEE-ID} all compare equal.
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    public static List<String> tokens(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String s = name.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")      // employeeId  -> employee Id
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")   // XMLParser   -> XML Parser
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")      // ADDR1       -> ADDR 1
                .replaceAll("([0-9])([A-Za-z])", "$1 $2");
        List<String> result = new ArrayList<>();
        for (String part : s.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                result.add(part.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(result);
    }

    /** All words joined without separators: the "normalized column name". */
    public static String key(String name) {
        return String.join("", tokens(name));
    }

    /** Light plural handling (EMPLOYEES = EMPLOYEE) applied identically to both sides before comparing. */
    public static String stem(String word) {
        if (word.length() > 3 && word.endsWith("S") && !word.endsWith("SS") && !word.endsWith("US")
                && !word.endsWith("IS")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
