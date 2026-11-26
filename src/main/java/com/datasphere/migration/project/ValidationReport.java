package com.datasphere.migration.project;

import java.time.Instant;
import java.util.List;

/** Outcome of validating a mapping. {@code valid} is true only when there is no error-level issue. */
public record ValidationReport(boolean valid, int errors, int warnings, int infos, int mappingVersion,
                               Instant validatedAt, List<ValidationIssue> issues) {

    public static ValidationReport of(List<ValidationIssue> issues, int mappingVersion) {
        int errors = (int) issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count();
        int warnings = (int) issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count();
        int infos = issues.size() - errors - warnings;
        return new ValidationReport(errors == 0, errors, warnings, infos, mappingVersion, Instant.now(),
                List.copyOf(issues));
    }
}
