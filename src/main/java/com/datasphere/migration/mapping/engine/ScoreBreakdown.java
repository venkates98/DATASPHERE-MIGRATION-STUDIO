package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.mapping.type.TypeCompatibility;
import java.util.List;

/**
 * Transparent record of how a score was reached, one line per kind of evidence.
 *
 * @param penalty points removed because the data types are incompatible
 * @param total   0..100 after the penalty, rounded to one decimal
 */
public record ScoreBreakdown(
        double exact,
        double synonym,
        double similarity,
        double dataType,
        double length,
        double constraint,
        double penalty,
        double total,
        ConfidenceClass confidence,
        EvidenceTier tier,
        TypeCompatibility compatibility,
        List<String> reasons,
        List<String> warnings) {
}
