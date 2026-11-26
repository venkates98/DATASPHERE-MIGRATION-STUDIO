package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.config.MappingProperties;

/** Heuristic confidence band for a score. It is a rule-of-thumb, not a calibrated probability. */
public enum ConfidenceClass {
    HIGH("High confidence"),
    GOOD("Good candidate"),
    REVIEW("Needs review"),
    WEAK("Weak candidate");

    private final String label;

    ConfidenceClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ConfidenceClass of(double score, MappingProperties.Thresholds t) {
        if (score >= t.high()) {
            return HIGH;
        }
        if (score >= t.good()) {
            return GOOD;
        }
        return score >= t.review() ? REVIEW : WEAK;
    }
}
