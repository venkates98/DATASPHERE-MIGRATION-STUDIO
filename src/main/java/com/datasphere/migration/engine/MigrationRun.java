package com.datasphere.migration.engine;

import com.datasphere.migration.validation.ReconciliationReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MigrationRun {

    private String id = UUID.randomUUID().toString();
    private MigrationRunStatus status = MigrationRunStatus.RUNNING;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    private int batchSize;
    private InsertMode insertMode = InsertMode.FAIL_ON_DUPLICATE;
    private int mappingVersion;
    private List<TableProgress> tables = new ArrayList<>();
    private String error;
    private ReconciliationReport reconciliation;

    public String getId() {
        return id;
    }

    public MigrationRunStatus getStatus() {
        return status;
    }

    public void setStatus(MigrationRunStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public InsertMode getInsertMode() {
        return insertMode;
    }

    public void setInsertMode(InsertMode insertMode) {
        this.insertMode = insertMode;
    }

    public int getMappingVersion() {
        return mappingVersion;
    }

    public void setMappingVersion(int mappingVersion) {
        this.mappingVersion = mappingVersion;
    }

    public List<TableProgress> getTables() {
        return tables;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ReconciliationReport getReconciliation() {
        return reconciliation;
    }

    public void setReconciliation(ReconciliationReport reconciliation) {
        this.reconciliation = reconciliation;
    }

    public long getRowsRead() {
        return tables.stream().mapToLong(TableProgress::getRowsRead).sum();
    }

    public long getRowsInserted() {
        return tables.stream().mapToLong(TableProgress::getRowsInserted).sum();
    }

    public long getRowsFailed() {
        return tables.stream().mapToLong(TableProgress::getRowsFailed).sum();
    }

    public boolean running() {
        return status == MigrationRunStatus.RUNNING;
    }
}
