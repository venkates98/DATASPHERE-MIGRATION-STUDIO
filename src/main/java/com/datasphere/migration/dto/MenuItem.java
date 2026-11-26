package com.datasphere.migration.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Application menu. {@code plannedPhase} is the development phase in which the page's functionality is
 * delivered; a page whose phase is later than {@link #IMPLEMENTED_PHASE} is clearly marked as not yet available.
 */
public enum MenuItem {

    DASHBOARD("Dashboard", "/dashboard", "bi-speedometer2", Group.OVERVIEW, 1,
            "Session overview: connections, jobs and migration counters held in memory."),
    PROJECTS("Migration Projects", "/projects", "bi-folder2-open", Group.CONNECT, 1,
            "Connect any source and target database, select tables, review the suggested mapping and approve it."),
    CONNECTIONS("Database Connections", "/connections", "bi-plug", Group.CONNECT, 1,
            "Define, test and manage source and target database connection profiles."),
    MIGRATION("Database Migration", "/migration", "bi-arrow-left-right", Group.MIGRATE, 6,
            "Guided wizard that takes a migration from connection selection to reconciliation."),
    MAPPING("Table & Column Mapping", "/mapping", "bi-diagram-3", Group.MIGRATE, 1,
            "Map source tables and columns to target tables and columns, with conversions."),
    RELATIONSHIPS("Relationship Mapping", "/relationships", "bi-share", Group.MIGRATE, 5,
            "Review keys and dependencies and determine a safe table load order."),
    COMPARISON("Schema Comparison", "/schema-comparison", "bi-columns-gap", Group.CONNECT, 1,
            "Compare source and target schemas and classify every difference."),
    VALIDATION("Migration Validation", "/validation", "bi-shield-check", Group.MIGRATE, 5,
            "Run preflight checks and read-only dry runs before any data is changed."),
    JOBS("Migration Jobs", "/jobs", "bi-list-task", Group.MONITOR, 6,
            "Start, monitor and cancel migration jobs."),
    LOGS("Migration Logs", "/logs", "bi-journal-text", Group.MONITOR, 7,
            "Live progress and per-job log events."),
    REPORTS("Reports & Reconciliation", "/reports", "bi-clipboard-data", Group.MONITOR, 7,
            "Final job reports and source-versus-target reconciliation results."),
    SETTINGS("Settings", "/settings", "bi-gear", Group.ADMIN, 1,
            "Effective application limits and security policy (read-only)."),
    HELP("Help & Documentation", "/help", "bi-question-circle", Group.ADMIN, 1,
            "How DataSphere works, mapping scenarios, transaction boundaries and limitations.");

    /** Highest development phase delivered by this build. */
    public static final int IMPLEMENTED_PHASE = 1;

    public enum Group {
        OVERVIEW("Overview"), CONNECT("Connect & Inspect"), MIGRATE("Map & Migrate"),
        MONITOR("Monitor & Report"), ADMIN("Administration");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String title;
    private final String path;
    private final String icon;
    private final Group group;
    private final int plannedPhase;
    private final String description;

    MenuItem(String title, String path, String icon, Group group, int plannedPhase, String description) {
        this.title = title;
        this.path = path;
        this.icon = icon;
        this.group = group;
        this.plannedPhase = plannedPhase;
        this.description = description;
    }

    public String getKey() {
        return name();
    }

    public String getTitle() {
        return title;
    }

    public String getPath() {
        return path;
    }

    public String getIcon() {
        return icon;
    }

    public Group getGroup() {
        return group;
    }

    public int getPlannedPhase() {
        return plannedPhase;
    }

    public String getDescription() {
        return description;
    }

    public boolean isImplemented() {
        return plannedPhase <= IMPLEMENTED_PHASE;
    }

    public static Optional<MenuItem> forPath(String requestPath) {
        if (requestPath == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(m -> requestPath.equals(m.path) || requestPath.startsWith(m.path + "/"))
                .findFirst();
    }

    public static List<MenuItem> inGroup(Group group) {
        return Arrays.stream(values()).filter(m -> m.group == group).toList();
    }
}
