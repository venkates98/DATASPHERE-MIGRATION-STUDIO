package com.datasphere.migration.mapping.type;

import java.util.List;

/**
 * Result of comparing a source type with a target type.
 *
 * @param sizeFactor            1 when length/precision/scale fit, 0.5 when values may be cut or rounded,
 *                              0 when the target is clearly too small or the types are incompatible
 * @param notes                 informational statements (no risk)
 * @param warnings              risks the user must read, in plain language
 * @param suggestedTransformation conversion that would resolve an incompatibility, or {@code null}
 */
public record TypeCompatibility(
        CompatibilityLevel level,
        double sizeFactor,
        List<String> notes,
        List<String> warnings,
        Transformation suggestedTransformation) {

    public boolean isIncompatible() {
        return level == CompatibilityLevel.INCOMPATIBLE;
    }
}
