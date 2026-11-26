package com.datasphere.migration.dto;

import java.util.List;

public record DashboardSummary(
        MetricsSnapshot metrics,
        String currentStatus,
        List<ActivityEvent> recentActivity,
        List<QuickAction> quickActions) {
}
