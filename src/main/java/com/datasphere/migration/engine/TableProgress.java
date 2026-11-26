package com.datasphere.migration.engine;

import java.util.ArrayList;
import java.util.List;

public class TableProgress {

    private String pairId;
    private String sourceTable;
    private String targetTable;
    private long rowsRead;
    private long rowsInserted;
    private long rowsSkipped;
    private long rowsFailed;
    private String lastKey;
    private String status = "PENDING";
    private String error;
    private List<FailedRecord> failures = new ArrayList<>();

    public TableProgress() {
    }

    public TableProgress(String pairId, String sourceTable, String targetTable) {
        this.pairId = pairId;
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
    }

    public String getPairId() {
        return pairId;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public long getRowsRead() {
        return rowsRead;
    }

    public void addRead(int n) {
        rowsRead += n;
    }

    public long getRowsInserted() {
        return rowsInserted;
    }

    public void addInserted(int n) {
        rowsInserted += n;
    }

    public long getRowsSkipped() {
        return rowsSkipped;
    }

    public void addSkipped(int n) {
        rowsSkipped += n;
    }

    public long getRowsFailed() {
        return rowsFailed;
    }

    public String getLastKey() {
        return lastKey;
    }

    public void setLastKey(String lastKey) {
        this.lastKey = lastKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<FailedRecord> getFailures() {
        return failures;
    }

    public void recordFailure(long rowNumber, String key, String reason) {
        rowsFailed++;
        if (failures.size() < 50) {
            failures.add(new FailedRecord(rowNumber, key, reason));
        }
    }
}
