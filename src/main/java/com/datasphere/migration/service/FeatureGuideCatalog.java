package com.datasphere.migration.service;

import com.datasphere.migration.dto.FeatureGuide;
import com.datasphere.migration.dto.FeatureGuide.Operation;
import com.datasphere.migration.dto.MenuItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Descriptive content shown on every menu page. Operations are listed with their real availability:
 * anything not implemented in this build is disabled and says why, instead of pretending to work.
 */
@Service
public class FeatureGuideCatalog {

    private final Map<MenuItem, FeatureGuide> guides = new EnumMap<>(MenuItem.class);

    public FeatureGuideCatalog() {
        guides.put(MenuItem.DASHBOARD, new FeatureGuide(
                "Summarises what has happened in this application session: configured connection profiles, "
                        + "connection test outcomes, jobs, and record counters.",
                "Use it as the starting point and to see at a glance whether anything needs attention.",
                List.of("None."),
                List.of(),
                List.of("Figures are held in memory only and disappear when the application restarts. "
                        + "They are not an audit record."),
                List.of("Zero values and empty states until you create profiles and run jobs.")));

        guides.put(MenuItem.PROJECTS, new FeatureGuide(
                "A migration project ties one source database to one target database. You connect both, pick the "
                        + "tables, review the mapping the engine suggests, approve it, and preview the migration. "
                        + "Any supported database can be the source or the target (SQL Server, Oracle, PostgreSQL, "
                        + "MySQL/MariaDB).",
                "Use it for every migration. Projects are saved on disk and survive a restart.",
                List.of("Source and target connection details", "The tables to migrate",
                        "Your decision on every suggested column mapping"),
                List.of(),
                List.of("Passwords are never saved to disk. After a restart, enter the passwords again.",
                        "Suggestions are heuristic. Confidence scores are rules of thumb, not probabilities; "
                                + "you must review the mapping before approving it.",
                        "Data copy uses INSERT only. It never drops, truncates or overwrites target rows.",
                        "Automated tests use in-memory H2 stand-ins, not live vendor servers."),
                List.of("An approved mapping, a read-only preview, optional batch copy and a partial validation report.")));

        guides.put(MenuItem.CONNECTIONS, new FeatureGuide(
                "Source and target connections are now part of each migration project. They are tested before "
                        + "they are saved and may use different database products.",
                "Open a project and use step 1, Connections.",
                List.of("Database type, host, port, database or service name, user name and password"),
                List.of(),
                List.of("Passwords are kept in memory only. Use a least-privilege, read-only account for sources.",
                        "Only allow-listed hosts may be targeted when a host allow-list is configured."),
                List.of("A success or failure message with a classified error code and corrective action.")));

        guides.put(MenuItem.COMPARISON, new FeatureGuide(
                "Shows which source and target database products can be paired, based on registered adapters, "
                        + "JDBC drivers, type conversion coverage and the data-copy engine. Pairings are marked "
                        + "PARTIAL SUPPORT until they have been verified against live vendor servers.",
                "Use it to see what this build can connect, map, convert and copy — and what it cannot claim.",
                List.of("None. The matrix is generated from the adapters in this build."),
                List.of(new Operation("View compatibility matrix", "bi-columns-gap",
                        "Generated from adapters and conversion rules.", true, null)),
                List.of("PARTIAL SUPPORT is not a guarantee. Live SQL Server, Oracle, PostgreSQL and MySQL were "
                        + "not used in automated tests.",
                        "MariaDB, DB2 and SQLite are not registered and must not be selected."),
                List.of("A cell for every registered source×target pair with capability flags and limitations.")));

        guides.put(MenuItem.MAPPING, new FeatureGuide(
                "Column mapping is part of each migration project. The engine suggests a target column for every "
                        + "source column with a score and plain-language reasons; you accept, change, reject or leave "
                        + "columns unmapped, then approve.",
                "Open a project and use step 3, Mapping.",
                List.of("Selected source and target tables", "Your review of every suggestion"),
                List.of(),
                List.of("Similar names are never assumed equivalent without your confirmation.",
                        "Changing an approved mapping withdraws the approval."),
                List.of("Complete, unambiguous mappings, or precise messages for anything missing or conflicting.")));

        guides.put(MenuItem.RELATIONSHIPS, new FeatureGuide(
                "Inspects primary keys, foreign keys and dependencies to decide a valid load order, and lets you "
                        + "define explicit joins and routing rules.",
                "Use it when tables reference each other, or when combining or splitting tables.",
                List.of("Mapped tables", "Join conditions for multi-source mappings", "Routing rules for multi-target mappings"),
                List.of(planned("Analyse dependencies", "bi-share", "Compute a parent-before-child order.", 5),
                        planned("Define join / routing rule", "bi-sign-merge-right", "Explicit rules only.", 5)),
                List.of("Constraints are never disabled or dropped. Circular dependencies that cannot be ordered "
                        + "safely stop validation."),
                List.of("A table execution order, or a clear explanation of why none exists.")));

        guides.put(MenuItem.VALIDATION, new FeatureGuide(
                "Runs preflight checks and a dry run that reads source data and tests conversion and constraints "
                        + "without writing to the target.",
                "Use it before every real migration, and again after changing any mapping.",
                List.of("Complete mapping", "Migration options (duplicate strategy, batch size, filters)"),
                List.of(planned("Run preflight validation", "bi-shield-check", "Check metadata, permissions, mapping.", 5),
                        planned("Run dry run", "bi-play-circle", "Read-only; target is never modified.", 5)),
                List.of("Large tables are sampled; records not inspected are stated explicitly. "
                        + "Passing validation does not guarantee every record will migrate."),
                List.of("Blocking errors and warnings listed separately. Success is shown only when all mandatory "
                        + "checks ran and passed.")));

        guides.put(MenuItem.MIGRATION, new FeatureGuide(
                "A guided wizard: choose connections, tables, mapping scenario, conversions, order and options, "
                        + "then validate, dry run, confirm, execute and reconcile.",
                "Use it for each migration once connections and mappings exist.",
                List.of("Source and target profiles", "Tables and mapping", "Duplicate and transaction settings",
                        "Explicit confirmation before target data is modified"),
                List.of(planned("Start migration wizard", "bi-rocket-takeoff", "Create a migration plan.", 6)),
                List.of("Source and target are separate databases: a single transaction cannot span both. "
                        + "Committed batches stay committed if a later batch fails or the job is cancelled."),
                List.of("A job with counters, logs, reconciliation and a downloadable report.")));

        guides.put(MenuItem.JOBS, new FeatureGuide(
                "Lists migration jobs and their states, and lets you cancel a running job at a safe batch boundary.",
                "Use it to see what is running, what finished, and to cancel when needed.",
                List.of("A job created through the migration wizard"),
                List.of(planned("Cancel job", "bi-x-octagon", "Stop at the next safe batch boundary.", 6)),
                List.of("Cancelling does not roll back batches that were already committed."),
                List.of("State transitions: CREATED, VALIDATING, READY, RUNNING, CANCELLING, CANCELLED, COMPLETED, "
                        + "COMPLETED_WITH_ERRORS, FAILED.")));

        guides.put(MenuItem.LOGS, new FeatureGuide(
                "Shows live progress and per-job log events: current table, batch, counters, retries and errors.",
                "Use it while a job runs, or afterwards to understand what happened.",
                List.of("A running or finished job"),
                List.of(planned("Watch live progress", "bi-activity", "Live updates with polling fallback.", 7)),
                List.of("Logs displayed here are in memory and not durable or compliant audit storage. "
                        + "Server log files are configured separately and are not exposed in the browser."),
                List.of("Timestamped events with job ID, table, batch, result and error code.")));

        guides.put(MenuItem.REPORTS, new FeatureGuide(
                "Produces a per-job report and reconciles source against target using counts, keys and optional "
                        + "checksums.",
                "Use it after a migration to evidence what was validated and transferred.",
                List.of("A finished job"),
                List.of(planned("Run reconciliation", "bi-check2-square", "Compare source and target.", 7),
                        planned("Download CSV report", "bi-filetype-csv", "Export without sensitive values.", 7)),
                List.of("Matching row counts do not prove that all values are identical. The report states "
                        + "exactly which checks were performed."),
                List.of("Reconciliation status with the checks performed and any discrepancies.")));

        guides.put(MenuItem.SETTINGS, new FeatureGuide(
                "Displays the effective limits and security policy the application is running with.",
                "Use it to confirm session, login-throttling and migration limits.",
                List.of("None. Values are set through configuration or environment variables."),
                List.of(),
                List.of("Settings are read-only in this build to prevent unsafe changes from the browser."),
                List.of("The values currently enforced by the server.")));

        guides.put(MenuItem.HELP, new FeatureGuide(
                "A user guide for this build: sign in, connect any source and target, select tables, review "
                        + "mapping suggestions, approve, preview, copy rows with INSERT, and read post-copy counts.",
                "Open it when you are new to the product, or when an error code needs a plain-language explanation.",
                List.of("None."),
                List.of(),
                List.of("The written guide describes only behaviour that is implemented. Phase-badged menu items "
                        + "are not available yet.",
                        "Matching row counts do not prove identical values."),
                List.of("You can complete a project from connections through an approved mapping and a guarded copy "
                        + "without guessing what a button does.")));
    }

    public FeatureGuide guideFor(MenuItem item) {
        return guides.get(item);
    }

    private static Operation planned(String label, String icon, String description, int phase) {
        return new Operation(label, icon, description, false,
                "Not available yet. This capability is delivered in development phase " + phase
                        + " and requires its backend to be implemented and verified first.");
    }
}
