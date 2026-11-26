package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.config.MappingProperties;
import java.util.List;

/**
 * What the two column names have in common.
 *
 * @param coverage   0..1 share of the words of both names that were matched
 * @param similarity 0..1 spelling similarity of the expanded names (supporting evidence only)
 * @param reasons    plain-language sentences describing the evidence
 */
public record NameEvidence(EvidenceTier tier, double coverage, double similarity, List<String> reasons) {

    /** Points per evidence kind for these two names. */
    public record Points(double exact, double synonym, double similarity) {
        public double total() {
            return exact + synonym + similarity;
        }
    }

    /**
     * Awards the name points. Evidence tiers do not stack: an exact match earns the full name block
     * (exact + synonym + similarity weights) because identical names necessarily satisfy the lesser tiers
     * as well, whereas partial evidence earns the synonym weight in proportion to the words matched.
     */
    public Points points(MappingProperties.Weights w, MappingProperties.Thresholds t) {
        return switch (tier) {
            case EXACT -> new Points(w.exact(), w.synonym(), w.similarity());
            case REORDERED_WORDS -> new Points(w.exact() * t.reorderedWordsFactor(), w.synonym(), w.similarity());
            case ABBREVIATION_OR_SYNONYM, PARTIAL ->
                    new Points(0, w.synonym() * coverage, w.similarity() * similarity);
            case SIMILARITY_ONLY -> new Points(0, 0, w.similarity() * similarity);
            case NONE -> new Points(0, 0, 0);
        };
    }

    public static NameEvidence none() {
        return new NameEvidence(EvidenceTier.NONE, 0, 0, List.of());
    }
}
