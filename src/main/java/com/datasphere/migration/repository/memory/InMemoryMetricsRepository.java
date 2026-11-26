package com.datasphere.migration.repository.memory;

import com.datasphere.migration.dto.MetricsSnapshot;
import com.datasphere.migration.repository.MetricsRepository;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory counters. Lost on restart; not shared between instances; not an audit store.
 */
@Repository
public class InMemoryMetricsRepository implements MetricsRepository {

    private final AtomicInteger profiles = new AtomicInteger();
    private final AtomicLong testsOk = new AtomicLong();
    private final AtomicLong testsFailed = new AtomicLong();
    private final AtomicInteger jobs = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicLong read = new AtomicLong();
    private final AtomicLong inserted = new AtomicLong();
    private final AtomicLong updated = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    @Override
    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(profiles.get(), testsOk.get(), testsFailed.get(), jobs.get(), running.get(),
                read.get(), inserted.get(), updated.get(), skipped.get(), failed.get());
    }

    @Override
    public void setConnectionProfiles(int count) {
        profiles.set(Math.max(0, count));
    }

    @Override
    public void recordConnectionTest(boolean success) {
        (success ? testsOk : testsFailed).incrementAndGet();
    }

    @Override
    public void jobCreated() {
        jobs.incrementAndGet();
    }

    @Override
    public void setRunningJobs(int count) {
        running.set(Math.max(0, count));
    }

    @Override
    public void addRecordCounts(long r, long i, long u, long s, long f) {
        read.addAndGet(r);
        inserted.addAndGet(i);
        updated.addAndGet(u);
        skipped.addAndGet(s);
        failed.addAndGet(f);
    }

    @Override
    public void clear() {
        profiles.set(0);
        testsOk.set(0);
        testsFailed.set(0);
        jobs.set(0);
        running.set(0);
        read.set(0);
        inserted.set(0);
        updated.set(0);
        skipped.set(0);
        failed.set(0);
    }
}
