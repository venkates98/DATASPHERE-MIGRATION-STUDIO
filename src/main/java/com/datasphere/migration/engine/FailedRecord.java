package com.datasphere.migration.engine;

/** A row that could not be inserted. Source values are not stored. */
public record FailedRecord(long sourceRowNumber, String key, String reason) {
}
