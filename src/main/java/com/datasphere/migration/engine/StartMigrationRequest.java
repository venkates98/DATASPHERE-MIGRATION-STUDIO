package com.datasphere.migration.engine;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StartMigrationRequest(
        @Min(1) @Max(10000) Integer batchSize,
        InsertMode insertMode,
        boolean confirmWrite) {
}
