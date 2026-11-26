package com.datasphere.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasphere.migration.dto.ActivityEvent;
import com.datasphere.migration.dto.DashboardSummary;
import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.dto.MetricsSnapshot;
import com.datasphere.migration.dto.QuickAction;
import com.datasphere.migration.repository.ActivityRepository;
import com.datasphere.migration.repository.MetricsRepository;
import com.datasphere.migration.service.impl.DashboardServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private MetricsRepository metrics;
    private ActivityRepository activity;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        metrics = mock(MetricsRepository.class);
        activity = mock(ActivityRepository.class);
        service = new DashboardServiceImpl(metrics, activity);
    }

    @Test
    void emptySessionShowsZerosAndNoFakeHistory() {
        when(metrics.snapshot()).thenReturn(MetricsSnapshot.empty());
        when(activity.recent(10)).thenReturn(List.of());

        DashboardSummary summary = service.summary();

        assertThat(summary.metrics()).isEqualTo(MetricsSnapshot.empty());
        assertThat(summary.recentActivity()).isEmpty();
        assertThat(summary.currentStatus()).startsWith("Idle");
    }

    @Test
    void unavailableQuickActionsExplainWhy() {
        when(metrics.snapshot()).thenReturn(MetricsSnapshot.empty());
        when(activity.recent(10)).thenReturn(List.of());

        List<QuickAction> actions = service.summary().quickActions();

        assertThat(actions).hasSize(4);
        assertThat(actions).allSatisfy(a -> {
            if (!a.enabled()) {
                assertThat(a.disabledReason()).isNotBlank();
            }
        });
        // Features whose backend is not delivered yet can never be enabled, even with profiles present.
        when(metrics.snapshot()).thenReturn(new MetricsSnapshot(2, 1, 0, 0, 0, 0, 0, 0, 0, 0));
        for (QuickAction a : service.summary().quickActions()) {
            MenuItem target = MenuItem.forPath(a.href()).orElseThrow();
            assertThat(a.enabled()).as(a.label()).isEqualTo(target.isImplemented());
            if (!a.enabled()) {
                assertThat(a.disabledReason()).contains("phase " + target.getPlannedPhase());
            }
        }
    }

    @Test
    void statusReflectsRunningJobs() {
        when(activity.recent(10)).thenReturn(List.of());
        when(metrics.snapshot()).thenReturn(new MetricsSnapshot(2, 0, 0, 3, 2, 0, 0, 0, 0, 0));
        assertThat(service.summary().currentStatus()).isEqualTo("Running (2 jobs)");
        when(metrics.snapshot()).thenReturn(new MetricsSnapshot(2, 0, 0, 3, 1, 0, 0, 0, 0, 0));
        assertThat(service.summary().currentStatus()).isEqualTo("Running (1 job)");
        when(metrics.snapshot()).thenReturn(new MetricsSnapshot(2, 0, 0, 3, 0, 0, 0, 0, 0, 0));
        assertThat(service.summary().currentStatus()).isEqualTo("Idle");
    }

    @Test
    void recentActivityIsPassedThrough() {
        ActivityEvent e = new ActivityEvent(Instant.parse("2026-01-01T10:00:00Z"), "JOB", "Job started", "OK", "J1");
        when(metrics.snapshot()).thenReturn(MetricsSnapshot.empty());
        when(activity.recent(10)).thenReturn(List.of(e));
        assertThat(service.summary().recentActivity()).containsExactly(e);
    }
}
