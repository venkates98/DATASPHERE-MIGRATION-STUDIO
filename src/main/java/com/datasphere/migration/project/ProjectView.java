package com.datasphere.migration.project;

import com.datasphere.migration.engine.MigrationRun;
import com.datasphere.migration.mapping.model.ColumnMapping;
import java.time.Instant;
import java.util.List;

/**
 * What the browser receives for a project. It carries the mappings plus just enough column detail to render
 * dropdowns and the unmapped lists, and server-computed capability flags so the UI never decides on its own
 * whether a step is allowed (the backend enforces the same rules independently).
 */
public record ProjectView(
        String id,
        String name,
        String state,
        String stateLabel,
        String stateDescription,
        int mappingVersion,
        Instant createdAt,
        Instant updatedAt,
        ConnectionView source,
        ConnectionView target,
        List<TablePairView> tablePairs,
        ApprovalRecord approval,
        ValidationReport validation,
        boolean validationIsCurrent,
        Instant previewVerifiedAt,
        MigrationRun lastRun,
        Capabilities capabilities,
        List<StateTransition> history) {

    /** A saved connection, without any secret. */
    public record ConnectionView(String role, String type, String typeLabel, String host, int port, String database,
                                 String schema, String username, boolean trustServerCertificate,
                                 boolean credentialsAvailable) {
    }

    public record ColumnInfo(String name, String type, String category, int ordinal, boolean nullable,
                             boolean primaryKey, boolean unique, boolean foreignKey, boolean required,
                             boolean autoIncrement) {
    }

    public record TablePairView(String id, String sourceSchema, String sourceTable, String targetSchema,
                                String targetTable, List<ColumnInfo> sourceColumns, List<ColumnInfo> targetColumns,
                                List<ColumnMapping> mappings) {
    }

    /** Which workflow steps are currently allowed. */
    public record Capabilities(boolean canSelectTables, boolean canSuggest, boolean canEditMapping,
                               boolean canValidate, boolean canApprove, boolean canPreview, boolean canStart,
                               boolean canCancel, boolean canReconcile, String startBlockedReason) {
    }

    /** Compact row for the project list. */
    public record Summary(String id, String name, String state, String stateLabel, int mappingVersion,
                          Instant createdAt, Instant updatedAt, String sourceDatabase, String targetDatabase,
                          int tablePairs, boolean approved) {
    }
}
