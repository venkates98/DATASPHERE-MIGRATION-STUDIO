package com.datasphere.migration.db;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Connection details typed by the user. Every field that ends up in a JDBC URL is restricted to a strict
 * character set so a value can never inject extra driver properties (for example {@code ;socketFactory=...}).
 */
public record ConnectionRequest(
        @NotNull DatabaseType type,
        @NotBlank @Size(max = 253)
        @Pattern(regexp = "[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?", message = "Use a host name or IPv4 address.")
        String host,
        @Min(1) @Max(65535) int port,
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "[A-Za-z0-9_.$#-]+", message = "Use letters, digits and _ . $ # - only.")
        String database,
        @Size(max = 128)
        @Pattern(regexp = "[A-Za-z0-9_.$#-]*", message = "Use letters, digits and _ . $ # - only.")
        String schema,
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "[^\\p{Cntrl};=]+", message = "The user name contains characters that are not allowed.")
        String username,
        @NotNull @Size(min = 1, max = 256, message = "Enter the password.") char[] password,
        boolean trustServerCertificate) {

    public ConnectionProfile toProfile(ConnectionRole role) {
        String effectiveSchema = schema == null || schema.isBlank() ? null : schema.trim();
        return new ConnectionProfile(role, type, host.trim(), port, database.trim(), effectiveSchema, username.trim(),
                trustServerCertificate);
    }

    @Override
    public String toString() {
        // Never print the password, even by accident in a log statement.
        return "ConnectionRequest[type=" + type + ", host=" + host + ", port=" + port + ", database=" + database
                + ", username=" + username + ", password=****]";
    }
}
