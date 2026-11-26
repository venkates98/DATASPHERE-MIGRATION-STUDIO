package com.datasphere.migration.metadata;

/** A table or view listed in the table picker (no column detail yet). */
public record TableSummary(String schema, String name, String type) {
}
