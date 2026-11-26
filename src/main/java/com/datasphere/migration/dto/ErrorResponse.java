package com.datasphere.migration.dto;

import com.datasphere.migration.exception.ErrorCode;
import java.time.Instant;
import java.util.List;

/**
 * Safe, user-facing description of an error. Never contains stack traces, SQL, or credentials.
 */
public record ErrorResponse(
        String code,
        String category,
        String message,
        String suggestedAction,
        String correlationId,
        Instant timestamp,
        int status,
        List<FieldIssue> fieldIssues,
        List<String> details) {

    /** A validation problem tied to a specific request field. */
    public record FieldIssue(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String correlationId) {
        return of(code, correlationId, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String correlationId, List<FieldIssue> issues) {
        return of(code, correlationId, issues, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String correlationId, List<FieldIssue> issues,
                                   List<String> details) {
        return new ErrorResponse(code.code(), code.category().label(), code.userMessage(), code.suggestedAction(),
                correlationId, Instant.now(), code.httpStatus().value(), issues, details);
    }
}
