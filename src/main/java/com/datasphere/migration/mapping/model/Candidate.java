package com.datasphere.migration.mapping.model;

import com.datasphere.migration.mapping.engine.ConfidenceClass;
import com.datasphere.migration.mapping.type.CompatibilityLevel;

/** An alternative target column considered for a source column, shown to help manual selection. */
public record Candidate(
        String targetColumn,
        double score,
        ConfidenceClass confidence,
        CompatibilityLevel compatibility,
        /** Name of the conversion that would make an incompatible candidate usable, or {@code null}. */
        String suggestedTransformation) {
}
