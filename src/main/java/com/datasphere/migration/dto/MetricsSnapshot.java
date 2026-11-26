package com.datasphere.migration.dto;

/** Point-in-time counters for the current application session (in memory, reset on restart). */
public record MetricsSnapshot(
        int connectionProfiles,
        long connectionTestsSucceeded,
        long connectionTestsFailed,
        int configuredJobs,
        int runningJobs,
        long recordsRead,
        long recordsInserted,
        long recordsUpdated,
        long recordsSkipped,
        long recordsFailed) {

    public static MetricsSnapshot empty() {
        return new MetricsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
