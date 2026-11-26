package com.datasphere.migration.validation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ReconciliationReport {

    private CheckOutcome overall = CheckOutcome.NOT_RUN;
    private Instant generatedAt;
    private List<TableCheck> tables = new ArrayList<>();
    private List<String> notes = new ArrayList<>();

    public CheckOutcome getOverall() {
        return overall;
    }

    public void setOverall(CheckOutcome overall) {
        this.overall = overall;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<TableCheck> getTables() {
        return tables;
    }

    public List<String> getNotes() {
        return notes;
    }

    public record TableCheck(String pairId, String sourceTable, String targetTable, CheckOutcome outcome,
                             long sourceRows, long targetRows, Long sourceNulls, Long targetNulls,
                             String detail) {
    }
}
