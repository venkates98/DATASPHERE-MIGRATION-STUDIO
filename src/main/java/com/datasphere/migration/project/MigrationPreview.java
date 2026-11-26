package com.datasphere.migration.project;

import java.time.Instant;
import java.util.List;

/**
 * Read-only description of what a migration would do. Nothing has been written to the target database; the
 * statement text is for review only and is never executed.
 */
public record MigrationPreview(String projectId, int mappingVersion, Instant generatedAt, String sourceDatabase,
                               String targetDatabase, List<PairPreview> pairs, List<String> notes) {

    public record PairPreview(String pairId, String sourceTable, String targetTable, long sourceRowCount,
                              List<ColumnLine> columns, List<String> unmappedSourceColumns,
                              List<String> unmappedTargetColumns, List<String> warnings, String statementPreview,
                              String createTableSql) {
    }

    public record ColumnLine(String sourceColumn, String sourceType, String targetColumn, String targetType,
                             String transformation, double score) {
    }
}
