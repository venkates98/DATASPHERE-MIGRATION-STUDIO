package com.datasphere.migration.project;

/** Workflow state of a migration project. Migration is only possible from the last two states. */
public enum ProjectState {
    CREATED("Created", "Connections and tables are not complete yet."),
    DISCOVERED("Discovered", "Both databases were read and tables were selected."),
    SUGGESTIONS_GENERATED("Suggestions generated", "Mapping suggestions were produced."),
    MAPPING_REVIEW_REQUIRED("Mapping review required", "Review every mapping, then validate and approve."),
    MAPPING_MODIFIED("Mapping modified", "The mapping changed; validate it and approve it again."),
    MAPPING_VALIDATION_FAILED("Mapping validation failed", "Resolve the listed errors, then validate again."),
    MAPPING_APPROVED("Mapping approved", "The mapping is approved; the migration preview is available."),
    READY_FOR_MIGRATION("Ready for migration", "Approval and the pre-migration check passed."),
    MIGRATING("Migrating", "Rows are being copied in batches."),
    MIGRATED("Migrated", "The copy finished. Review the job report and run validation."),
    MIGRATION_FAILED("Migration failed", "The copy stopped. Committed batches were not rolled back."),
    VALIDATED("Validated", "Post-migration checks were run. See which checks actually executed.");

    private final String label;
    private final String description;

    ProjectState(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean isApproved() {
        return this == MAPPING_APPROVED || this == READY_FOR_MIGRATION || this == MIGRATING || this == MIGRATED
                || this == MIGRATION_FAILED || this == VALIDATED;
    }
}
