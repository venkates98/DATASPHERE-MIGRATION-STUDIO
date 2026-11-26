package com.datasphere.migration.exception;

/**
 * Central classification of every error the application can report.
 */
public enum ErrorCategory {
    AUTHENTICATION("Authentication error"),
    CONFIGURATION("Configuration error"),
    CONNECTIVITY("Connectivity error"),
    PERMISSION("Permission error"),
    METADATA("Metadata error"),
    SCHEMA("Schema error"),
    DATA_CONVERSION("Data conversion error"),
    DUPLICATE_OR_CONSTRAINT("Duplicate or constraint error"),
    TRANSACTION("Transaction error"),
    TIMEOUT("Timeout error"),
    RESOURCE_EXHAUSTION("Resource exhaustion"),
    UNEXPECTED("Unexpected internal error");

    private final String label;

    ErrorCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
