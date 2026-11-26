package com.datasphere.migration.service.impl;

import com.datasphere.migration.dto.DashboardSummary;
import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.dto.MetricsSnapshot;
import com.datasphere.migration.dto.QuickAction;
import com.datasphere.migration.repository.ActivityRepository;
import com.datasphere.migration.repository.MetricsRepository;
import com.datasphere.migration.service.DashboardService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final MetricsRepository metrics;
    private final ActivityRepository activity;

    public DashboardServiceImpl(MetricsRepository metrics, ActivityRepository activity) {
        this.metrics = metrics;
        this.activity = activity;
    }

    @Override
    public DashboardSummary summary() {
        MetricsSnapshot snapshot = metrics.snapshot();
        return new DashboardSummary(snapshot, statusOf(snapshot), activity.recent(RECENT_ACTIVITY_LIMIT),
                quickActions(snapshot));
    }

    static String statusOf(MetricsSnapshot m) {
        if (m.runningJobs() > 0) {
            return "Running (" + m.runningJobs() + (m.runningJobs() == 1 ? " job)" : " jobs)");
        }
        return m.configuredJobs() == 0 ? "Idle — no jobs created in this session" : "Idle";
    }

    private List<QuickAction> quickActions(MetricsSnapshot m) {
        boolean hasPair = m.connectionProfiles() >= 2;
        return List.of(
                action("Test a connection", "bi-lightning-charge", MenuItem.CONNECTIONS, true, null),
                action("Compare schemas", "bi-columns-gap", MenuItem.COMPARISON, hasPair,
                        "Requires a source and a target connection profile."),
                action("Create a mapping", "bi-diagram-3", MenuItem.MAPPING, hasPair,
                        "Requires a source and a target connection profile."),
                action("Start a migration", "bi-rocket-takeoff", MenuItem.MIGRATION, hasPair,
                        "Requires a source and a target connection profile and a validated mapping."));
    }

    /** An action is enabled only if its page is implemented in this build and its prerequisites hold. */
    private QuickAction action(String label, String icon, MenuItem target, boolean prerequisitesMet,
                               String prerequisiteReason) {
        if (!target.isImplemented()) {
            return new QuickAction(label, icon, target.getPath(), false,
                    "Not available yet. Delivered in development phase " + target.getPlannedPhase() + ".");
        }
        if (!prerequisitesMet) {
            return new QuickAction(label, icon, target.getPath(), false, prerequisiteReason);
        }
        return new QuickAction(label, icon, target.getPath(), true, null);
    }
}
