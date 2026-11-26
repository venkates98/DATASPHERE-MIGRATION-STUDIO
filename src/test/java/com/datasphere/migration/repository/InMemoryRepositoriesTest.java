package com.datasphere.migration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasphere.migration.dto.ActivityEvent;
import com.datasphere.migration.dto.MetricsSnapshot;
import com.datasphere.migration.repository.memory.InMemoryActivityRepository;
import com.datasphere.migration.repository.memory.InMemoryMetricsRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryRepositoriesTest {

    private static ActivityEvent event(int n) {
        return new ActivityEvent(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(n), "TEST", "event " + n, "OK", null);
    }

    @Test
    void activityIsReturnedNewestFirstAndLimited() {
        ActivityRepository repo = new InMemoryActivityRepository();
        for (int i = 0; i < 5; i++) {
            repo.add(event(i));
        }
        List<ActivityEvent> recent = repo.recent(3);
        assertThat(recent).extracting(ActivityEvent::message).containsExactly("event 4", "event 3", "event 2");
    }

    @Test
    void activityIsBounded() {
        ActivityRepository repo = new InMemoryActivityRepository();
        for (int i = 0; i < 500; i++) {
            repo.add(event(i));
        }
        List<ActivityEvent> all = repo.recent(1000);
        assertThat(all).hasSize(200);
        assertThat(all.get(0).message()).isEqualTo("event 499");
    }

    @Test
    void clearingModelsAnApplicationRestart() {
        InMemoryActivityRepository activity = new InMemoryActivityRepository();
        InMemoryMetricsRepository metrics = new InMemoryMetricsRepository();
        activity.add(event(1));
        metrics.jobCreated();
        metrics.recordConnectionTest(true);
        metrics.addRecordCounts(10, 8, 1, 1, 0);

        activity.clear();
        metrics.clear();

        assertThat(activity.recent(10)).isEmpty();
        assertThat(metrics.snapshot()).isEqualTo(MetricsSnapshot.empty());
    }

    @Test
    void emptyRepositoriesReportZeros() {
        assertThat(new InMemoryMetricsRepository().snapshot()).isEqualTo(MetricsSnapshot.empty());
        assertThat(new InMemoryActivityRepository().recent(10)).isEmpty();
    }

    @Test
    void metricsAreThreadSafe() throws Exception {
        InMemoryMetricsRepository metrics = new InMemoryMetricsRepository();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    metrics.addRecordCounts(2, 1, 0, 1, 0);
                    metrics.recordConnectionTest(i % 2 == 0);
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        MetricsSnapshot s = metrics.snapshot();
        assertThat(s.recordsRead()).isEqualTo(16_000);
        assertThat(s.recordsInserted()).isEqualTo(8_000);
        assertThat(s.recordsSkipped()).isEqualTo(8_000);
        assertThat(s.connectionTestsSucceeded()).isEqualTo(4_000);
        assertThat(s.connectionTestsFailed()).isEqualTo(4_000);
    }

    @Test
    void activityIsThreadSafe() throws Exception {
        InMemoryActivityRepository repo = new InMemoryActivityRepository();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    repo.add(event(i));
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(repo.recent(1000).size()).isLessThanOrEqualTo(200);
    }
}
