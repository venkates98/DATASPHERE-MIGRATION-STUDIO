package com.datasphere.migration.dto;

import java.util.List;

/**
 * Describes a feature page: what it does, when to use it, required inputs, operations, risks and expected results.
 */
public record FeatureGuide(
        String whatItDoes,
        String whenToUse,
        List<String> requiredInputs,
        List<Operation> operations,
        List<String> risks,
        List<String> expectedResults) {

    /**
     * An operation offered by the page. A disabled operation always carries the reason it is unavailable.
     */
    public record Operation(String label, String icon, String description, boolean enabled, String disabledReason) {
    }
}
