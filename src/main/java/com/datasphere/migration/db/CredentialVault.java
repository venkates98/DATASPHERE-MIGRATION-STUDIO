package com.datasphere.migration.db;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Holds database passwords in memory only, per project and role. Nothing here is ever written to disk,
 * logged or returned to the browser; after a restart users re-enter their passwords.
 */
@Component
public class CredentialVault {

    private final ConcurrentMap<String, char[]> secrets = new ConcurrentHashMap<>();

    public void put(String projectId, ConnectionRole role, char[] password) {
        char[] previous = secrets.put(key(projectId, role), password.clone());
        wipe(previous);
    }

    /** Returns a copy; callers should {@link #wipe} it after use. */
    public Optional<char[]> get(String projectId, ConnectionRole role) {
        char[] stored = secrets.get(key(projectId, role));
        return stored == null ? Optional.empty() : Optional.of(stored.clone());
    }

    public boolean has(String projectId, ConnectionRole role) {
        return secrets.containsKey(key(projectId, role));
    }

    public void remove(String projectId, ConnectionRole role) {
        wipe(secrets.remove(key(projectId, role)));
    }

    public void removeProject(String projectId) {
        for (ConnectionRole role : ConnectionRole.values()) {
            remove(projectId, role);
        }
    }

    public static void wipe(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }

    private static String key(String projectId, ConnectionRole role) {
        return projectId + ":" + role.name();
    }

    @Override
    public String toString() {
        return "CredentialVault[entries=" + secrets.size() + "]";
    }
}
