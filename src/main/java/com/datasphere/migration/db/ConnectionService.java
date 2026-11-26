package com.datasphere.migration.db;

import com.datasphere.migration.dto.ActivityEvent;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.repository.ActivityRepository;
import com.datasphere.migration.repository.MetricsRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Opens connections and translates driver failures into classified, user-safe application errors.
 * Raw driver messages (which may echo host names or user names) go to the protected log only.
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final JdbcConnectionFactory factory;
    private final MetricsRepository metrics;
    private final ActivityRepository activity;

    public ConnectionService(JdbcConnectionFactory factory, MetricsRepository metrics, ActivityRepository activity) {
        this.factory = factory;
        this.metrics = metrics;
        this.activity = activity;
    }

    /** Opens a connection, reads basic server information and closes it again. */
    public ConnectionTestResult test(ConnectionProfile profile, char[] password) {
        long start = System.nanoTime();
        try (Connection c = open(profile, password)) {
            DatabaseMetaData md = c.getMetaData();
            String schema = profile.schema() != null ? profile.schema()
                    : profile.type().defaultSchema(profile.database(), profile.username());
            ConnectionTestResult result = new ConnectionTestResult(true, md.getDatabaseProductName(),
                    firstLine(md.getDatabaseProductVersion()), safe(md.getUserName(), profile.username()), schema,
                    (System.nanoTime() - start) / 1_000_000);
            metrics.recordConnectionTest(true);
            activity.add(new ActivityEvent(Instant.now(), "CONNECTION",
                    profile.role() + " connection test succeeded: " + profile.display(), "OK", null));
            return result;
        } catch (SQLException e) {
            metrics.recordConnectionTest(false);
            activity.add(new ActivityEvent(Instant.now(), "CONNECTION",
                    profile.role() + " connection test failed: " + profile.display(), "FAILED", null));
            throw translateOpen(e, profile);
        } catch (ApplicationException e) {
            metrics.recordConnectionTest(false);
            throw e;
        }
    }

    /** Runs {@code work} on a fresh read-only connection that is closed afterwards. */
    public <T> T execute(ConnectionProfile profile, char[] password, SqlFunction<Connection, T> work) {
        return execute(profile, password, AccessMode.READ, work);
    }

    public <T> T execute(ConnectionProfile profile, char[] password, AccessMode mode,
                         SqlFunction<Connection, T> work) {
        try (Connection c = open(profile, password, mode)) {
            try {
                return work.apply(c);
            } catch (SQLException e) {
                throw translateMetadata(e, profile);
            }
        } catch (SQLException e) {
            throw translateOpen(e, profile);
        }
    }

    private Connection open(ConnectionProfile profile, char[] password) throws SQLException {
        return open(profile, password, AccessMode.READ);
    }

    private Connection open(ConnectionProfile profile, char[] password, AccessMode mode) throws SQLException {
        return factory.open(profile, password, mode);
    }

    ApplicationException translateOpen(SQLException e, ConnectionProfile profile) {
        log.warn("Connection to {} failed sqlState={} vendorCode={} reason={}", profile.display(), e.getSQLState(),
                e.getErrorCode(), shorten(e.getMessage()));
        String text = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        ErrorCode code;
        if (text.contains("no suitable driver")) {
            code = ErrorCode.DB_DRIVER_001;
        } else if (isAuthFailure(state, e.getErrorCode(), text)) {
            code = ErrorCode.DB_AUTH_001;
        } else if (text.contains("timed out") || text.contains("timeout")) {
            code = ErrorCode.DB_TIMEOUT_001;
        } else {
            code = ErrorCode.DB_CONN_001;
        }
        return new ApplicationException(code, "Connect failed for " + profile.display() + ": " + shorten(e.getMessage()), e);
    }

    ApplicationException translateMetadata(SQLException e, ConnectionProfile profile) {
        log.warn("Metadata read on {} failed sqlState={} vendorCode={} reason={}", profile.display(), e.getSQLState(),
                e.getErrorCode(), shorten(e.getMessage()));
        String text = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        boolean permission = (e.getSQLState() != null && e.getSQLState().startsWith("42"))
                || text.contains("permission") || text.contains("insufficient privileges")
                || text.contains("not authorized");
        return new ApplicationException(permission ? ErrorCode.PERM_DB_001 : ErrorCode.DB_META_001,
                "Metadata operation failed on " + profile.display() + ": " + shorten(e.getMessage()), e);
    }

    private static boolean isAuthFailure(String state, int vendorCode, String text) {
        return state.startsWith("28")          // invalid authorisation specification (PostgreSQL, MySQL, ANSI)
                || vendorCode == 18456         // SQL Server: login failed
                || vendorCode == 1017          // Oracle: invalid username/password
                || vendorCode == 1045          // MySQL: access denied
                || text.contains("login failed") || text.contains("password authentication failed")
                || text.contains("invalid username/password") || text.contains("access denied");
    }

    private static String firstLine(String value) {
        if (value == null) {
            return "";
        }
        int nl = value.indexOf('\n');
        return (nl > 0 ? value.substring(0, nl) : value).trim();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String shorten(String message) {
        if (message == null) {
            return "";
        }
        String single = message.replaceAll("\\s+", " ");
        return single.length() > 300 ? single.substring(0, 300) + "..." : single;
    }
}
