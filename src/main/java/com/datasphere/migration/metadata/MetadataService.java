package com.datasphere.migration.metadata;

import com.datasphere.migration.db.ConnectionProfile;
import com.datasphere.migration.db.ConnectionService;
import com.datasphere.migration.db.CredentialVault;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Live, read-only discovery against a project's saved connections. */
@Service
public class MetadataService {

    private final ConnectionService connections;
    private final CredentialVault vault;

    public MetadataService(ConnectionService connections, CredentialVault vault) {
        this.connections = connections;
        this.vault = vault;
    }

    public List<String> schemas(String projectId, ConnectionProfile profile) {
        return withPassword(projectId, profile, pw ->
                connections.execute(profile, pw, c -> JdbcMetadataReader.listSchemas(c, profile.type())));
    }

    public List<TableSummary> tables(String projectId, ConnectionProfile profile, String schema) {
        return withPassword(projectId, profile, pw ->
                connections.execute(profile, pw, c -> JdbcMetadataReader.listTables(c, profile.type(), schema)));
    }

    /** Reads the structure of each table on one connection. Every table must exist. */
    public Map<String, TableMetadata> readTables(String projectId, ConnectionProfile profile, List<TableRef> refs) {
        Map<String, TableMetadata> found = readTablesIfPresent(projectId, profile, refs);
        for (TableRef ref : refs) {
            if (!found.containsKey(ref.key())) {
                throw new ApplicationException(ErrorCode.DB_META_001, "Table not found: " + ref.key(),
                        List.of("The " + profile.role().name().toLowerCase() + " table '" + ref.key()
                                + "' was not found. It may have been renamed or dropped."));
            }
        }
        return found;
    }

    /** Like {@link #readTables} but tables that no longer exist are simply absent from the result. */
    public Map<String, TableMetadata> readTablesIfPresent(String projectId, ConnectionProfile profile,
                                                          List<TableRef> refs) {
        return withPassword(projectId, profile, pw -> connections.execute(profile, pw, c -> {
            Map<String, TableMetadata> result = new LinkedHashMap<>();
            for (TableRef ref : refs) {
                JdbcMetadataReader.readTable(c, profile.type(), ref).ifPresent(t -> result.put(ref.key(), t));
            }
            return result;
        }));
    }

    public TableMetadata readTable(String projectId, ConnectionProfile profile, TableRef ref) {
        return readTables(projectId, profile, List.of(ref)).get(ref.key());
    }

    /**
     * Counts the rows of a table that was already discovered. The statement is assembled by the server from
     * quoted, verified identifiers (never from user text) and runs on a read-only connection.
     */
    public long rowCount(String projectId, ConnectionProfile profile, TableRef ref) {
        String sql = "SELECT COUNT(*) FROM " + profile.type().qualified(ref.schema(), ref.name());
        return withPassword(projectId, profile, pw -> connections.execute(profile, pw, c -> {
            try (java.sql.Statement st = c.createStatement()) {
                st.setQueryTimeout(60);
                try (java.sql.ResultSet rs = st.executeQuery(sql)) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        }));
    }

    private <T> T withPassword(String projectId, ConnectionProfile profile, java.util.function.Function<char[], T> work) {
        char[] password = vault.get(projectId, profile.role()).orElseThrow(() ->
                new ApplicationException(ErrorCode.DB_CRED_001,
                        "No in-memory credentials for project " + projectId + " role " + profile.role()));
        try {
            return work.apply(password);
        } finally {
            CredentialVault.wipe(password);
        }
    }
}
