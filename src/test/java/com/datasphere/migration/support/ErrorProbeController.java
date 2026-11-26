package com.datasphere.migration.support;

import com.datasphere.migration.exception.DatabaseConnectionException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.exception.InvalidMappingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that raise each class of failure so the global handler can be verified. */
@RestController
@Profile("test")
public class ErrorProbeController {

    public record Form(@NotBlank(message = "Profile name is required") String name) {
    }

    @GetMapping("/probe/app-error")
    public String appError() {
        throw new DatabaseConnectionException(ErrorCode.DB_CONN_001,
                "Cannot open jdbc:sqlserver://10.1.1.1:1433;databaseName=hr;user=sa;password=LeakyPassw0rd",
                new java.sql.SQLException("login failed password=LeakyPassw0rd"));
    }

    @GetMapping("/probe/mapping-error")
    public String mappingError() {
        throw new InvalidMappingException("Target EMPLOYEE.MOBILE has no source");
    }

    @GetMapping("/probe/unexpected")
    public String unexpected() {
        throw new IllegalStateException("internal failure with secret token=abc123");
    }

    @PostMapping("/probe/validate")
    public String validate(@Valid @RequestBody Form form) {
        return form.name();
    }
}
