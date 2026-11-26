package com.datasphere.migration.repository;

import com.datasphere.migration.dto.MetricsSnapshot;

/**
 * Session-level counters. The in-memory implementation can be replaced by a persistent one without
 * touching business logic.
 */
public interface MetricsRepository {

    MetricsSnapshot snapshot();

    void setConnectionProfiles(int count);

    void recordConnectionTest(boolean success);

    void jobCreated();

    void setRunningJobs(int count);

    void addRecordCounts(long read, long inserted, long updated, long skipped, long failed);

    /** Clears all counters (used by tests and to model an application restart). */
    void clear();
}
