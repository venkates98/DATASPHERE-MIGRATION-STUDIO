package com.datasphere.migration.mapping.type;

/** How safely a source type can be stored in a target type. Ordered from best to worst. */
public enum CompatibilityLevel {
    IDENTICAL("Identical types"),
    COMPATIBLE("Compatible"),
    CONDITIONAL("Compatible with conditions"),
    INCOMPATIBLE("Incompatible");

    private final String label;

    CompatibilityLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isWorseThan(CompatibilityLevel other) {
        return ordinal() > other.ordinal();
    }

    /** Share of the data-type points this level earns. */
    public double scoreFactor() {
        return switch (this) {
            case IDENTICAL, COMPATIBLE -> 1.0;
            case CONDITIONAL -> 0.5;
            case INCOMPATIBLE -> 0.0;
        };
    }
}
