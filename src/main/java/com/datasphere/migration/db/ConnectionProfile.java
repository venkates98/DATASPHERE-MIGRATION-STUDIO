package com.datasphere.migration.db;

import java.util.Locale;

/**
 * A saved reference to a database. It deliberately has no password field: secrets are held only in memory
 * by {@link CredentialVault} and are therefore never persisted with a project.
 */
public record ConnectionProfile(
        ConnectionRole role,
        DatabaseType type,
        String host,
        int port,
        String database,
        String schema,
        String username,
        boolean trustServerCertificate) {

    /** Identity used to decide whether re-saving a connection points at a different database. */
    public String identity() {
        return String.join("|", type.name(), host.toLowerCase(Locale.ROOT), String.valueOf(port),
                database.toLowerCase(Locale.ROOT), schema == null ? "" : schema.toLowerCase(Locale.ROOT),
                username.toLowerCase(Locale.ROOT));
    }

    /** Short description safe for logs and the UI. */
    public String display() {
        return type.label() + " " + host + ":" + port + "/" + database;
    }
}
