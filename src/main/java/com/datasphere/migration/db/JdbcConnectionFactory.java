package com.datasphere.migration.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens a short-lived JDBC connection. Callers must close it (try-with-resources). */
public interface JdbcConnectionFactory {

    Connection open(ConnectionProfile profile, char[] password) throws SQLException;

    default Connection open(ConnectionProfile profile, char[] password, AccessMode mode) throws SQLException {
        Connection connection = open(profile, password);
        applyAccessMode(connection, mode);
        return connection;
    }

    static void applyAccessMode(Connection connection, AccessMode mode) throws SQLException {
        if (mode == AccessMode.READ) {
            try {
                connection.setReadOnly(true);
            } catch (SQLException ignored) {
                // some drivers refuse the hint
            }
        } else {
            try {
                connection.setReadOnly(false);
            } catch (SQLException ignored) {
                // some drivers refuse the hint
            }
        }
    }
}
