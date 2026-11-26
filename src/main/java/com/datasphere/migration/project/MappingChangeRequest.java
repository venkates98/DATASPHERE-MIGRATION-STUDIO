package com.datasphere.migration.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A batch of user edits applied atomically against a known mapping version. If the version no longer matches,
 * nothing is applied (another tab or request changed the mapping first).
 */
public record MappingChangeRequest(
        int expectedVersion,
        @NotEmpty @Size(max = 5000) List<@Valid @NotNull Change> changes) {

    public enum Action {
        /** Confirm the shown mapping. */
        ACCEPT,
        /** Reject the engine's suggestion; the column stays unmapped. */
        REJECT,
        /** Leave the column unmapped. */
        CLEAR,
        /** Choose a target column by hand. */
        SET_TARGET,
        /** Choose a conversion (and optional value) for the mapping. */
        SET_TRANSFORMATION,
        /** Accept every mapping the engine marked as a clean suggestion. */
        ACCEPT_ALL_SUGGESTED,
        /** Discard decisions for a table pair and ask the engine again. */
        RESET_PAIR
    }

    public record Change(
            @NotNull Action action,
            @Size(max = 64) String pairId,
            @Size(max = 256) String sourceColumn,
            @Size(max = 256) String targetColumn,
            @Size(max = 40) String transformation,
            @Size(max = 100) String transformationParameter) {
    }
}
