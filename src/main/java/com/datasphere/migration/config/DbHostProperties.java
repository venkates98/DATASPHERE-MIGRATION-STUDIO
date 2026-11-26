package com.datasphere.migration.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Outbound database host policy ({@code datasphere.security.db-hosts.*}) that limits SSRF exposure.
 *
 * @param allowed          host names ({@code db.corp.example}), wildcard names ({@code *.corp.example}),
 *                         IP addresses or CIDR ranges ({@code 10.20.0.0/16}). Empty = no allow-list.
 * @param requireAllowList refuse to start without an allow-list (enabled by the {@code prod} profile)
 */
@Validated
@ConfigurationProperties(prefix = "datasphere.security.db-hosts")
public record DbHostProperties(
        @DefaultValue List<String> allowed,
        @DefaultValue("false") boolean requireAllowList) {
}
