package com.datasphere.migration.mapping.engine;

/** The strongest kind of name evidence found between two columns. */
public enum EvidenceTier {
    EXACT("Same name"),
    REORDERED_WORDS("Same words, different order"),
    ABBREVIATION_OR_SYNONYM("Abbreviation or synonym"),
    PARTIAL("Partly matching name"),
    SIMILARITY_ONLY("Similar spelling only"),
    NONE("No name evidence");

    private final String label;

    EvidenceTier(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Evidence strong enough for the engine to propose a mapping automatically. */
    public boolean canSuggest() {
        return this != SIMILARITY_ONLY && this != NONE;
    }

    /** The whole name is explained, so no unmatched words remain. */
    public boolean isFullyResolved() {
        return this == EXACT || this == REORDERED_WORDS || this == ABBREVIATION_OR_SYNONYM;
    }
}
