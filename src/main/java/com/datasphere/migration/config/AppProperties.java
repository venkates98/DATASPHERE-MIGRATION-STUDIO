package com.datasphere.migration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings bound from the {@code datasphere.*} namespace.
 */
@Validated
@ConfigurationProperties(prefix = "datasphere")
public record AppProperties(
        @DefaultValue("0.0.0") String version,
        @Valid @NotNull @DefaultValue Security security,
        @Valid @NotNull @DefaultValue Limits limits) {

    public record Security(
            @Valid @NotNull @DefaultValue Admin admin,
            @Valid @NotNull @DefaultValue Login login) {
    }

    /** Initial administrator. The password hash is supplied externally, never committed. */
    public record Admin(
            @DefaultValue("") String username,
            @DefaultValue("Administrator") String displayName,
            @DefaultValue("") String passwordHash) {

        @Override
        public String toString() {
            // Never include the hash in any string rendering of the properties object.
            return "Admin[username=" + username + ", displayName=" + displayName + ", passwordHash=****]";
        }
    }

    public record Login(
            @Min(1) @DefaultValue("5") int maxAttempts,
            @NotNull @DefaultValue("15m") Duration lockoutDuration) {
    }

    public record Limits(
            @Min(1) @DefaultValue("50") int maxTablesPerJob,
            @Min(1) @DefaultValue("10000") int maxBatchSize,
            @Min(1) @DefaultValue("1000") int defaultBatchSize,
            @Min(1) @DefaultValue("1000") int defaultFetchSize,
            @Min(1) @DefaultValue("2") int maxConcurrentJobs) {
    }
}
