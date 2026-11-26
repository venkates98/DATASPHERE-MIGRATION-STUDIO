package com.datasphere.migration.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where migration projects (mappings, approvals, metadata snapshots) are stored.
 * Database passwords are <strong>never</strong> written there.
 */
@Validated
@ConfigurationProperties(prefix = "datasphere.storage")
public record StorageProperties(@NotBlank @DefaultValue("./data") String dataDir) {
}
