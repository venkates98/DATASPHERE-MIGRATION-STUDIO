package com.datasphere.migration.db;

/** Outcome of a successful connection test (failures are reported as classified errors). */
public record ConnectionTestResult(
        boolean success,
        String productName,
        String productVersion,
        String currentUser,
        String defaultSchema,
        long latencyMillis) {
}
