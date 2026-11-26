package com.datasphere.migration.mapping.model;

/** Review state of one source column's mapping. */
public enum MappingStatus {
    /** Proposed by the engine with strong, unambiguous evidence; waits for the user's decision. */
    SUGGESTED("Suggested"),
    /** Proposed by the engine but weaker, partial or carrying warnings; must be checked. */
    NEEDS_REVIEW("Needs review"),
    /** Several targets look about equally likely; nothing is selected until the user chooses. */
    AMBIGUOUS("Ambiguous"),
    /** The user confirmed the mapping. */
    ACCEPTED("Accepted"),
    /** The user chose or changed the target or the conversion by hand. */
    MODIFIED("Modified"),
    /** The user rejected the engine's suggestion. */
    REJECTED("Rejected"),
    /** No target (nothing suitable found, or cleared by the user). The column will not be migrated. */
    UNMAPPED("Unmapped");

    private final String label;

    MappingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True while the user still has to decide about this mapping. */
    public boolean awaitsReview() {
        return this == SUGGESTED || this == NEEDS_REVIEW || this == AMBIGUOUS;
    }
}
