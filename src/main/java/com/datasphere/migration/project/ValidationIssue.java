package com.datasphere.migration.project;

/**
 * One finding of mapping validation, in plain language.
 *
 * @param pairId       the table pair concerned, or {@code null} for project-wide findings
 * @param sourceColumn the source column concerned, if any
 * @param targetColumn the target column concerned, if any
 */
public record ValidationIssue(Severity severity, String code, String pairId, String sourceColumn,
                              String targetColumn, String message) {

    public enum Severity { ERROR, WARNING, INFO }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
