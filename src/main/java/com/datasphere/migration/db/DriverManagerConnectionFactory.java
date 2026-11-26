package com.datasphere.migration.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Production connection factory: validates the host against {@link HostPolicy}, builds the JDBC URL from
 * validated fields only (never a user-supplied URL) and connects with bounded time-outs.
 */
@Component
public class DriverManagerConnectionFactory implements JdbcConnectionFactory {

    static final int CONNECT_TIMEOUT_SEC = 10;
    static final int READ_TIMEOUT_SEC = 120;

    private final HostPolicy hostPolicy;

    public DriverManagerConnectionFactory(HostPolicy hostPolicy) {
        this.hostPolicy = hostPolicy;
    }

    @Override
    public Connection open(ConnectionProfile profile, char[] password) throws SQLException {
        return open(profile, password, AccessMode.READ);
    }

    @Override
    public Connection open(ConnectionProfile profile, char[] password, AccessMode mode) throws SQLException {
        hostPolicy.check(profile.host());
        var adapter = profile.type().adapter();
        if (!adapter.driverAvailable()) {
            throw new SQLException("No suitable driver: " + adapter.driverClassName() + " is not on the classpath",
                    "08001");
        }
        String url = adapter.jdbcUrl(profile.host(), profile.port(), profile.database(),
                profile.trustServerCertificate(), CONNECT_TIMEOUT_SEC);
        Properties props = adapter.connectionProperties(profile.username(), password, CONNECT_TIMEOUT_SEC,
                READ_TIMEOUT_SEC);
        Connection connection = DriverManager.getConnection(url, props);
        props.clear();
        JdbcConnectionFactory.applyAccessMode(connection, mode);
        return connection;
    }
}
