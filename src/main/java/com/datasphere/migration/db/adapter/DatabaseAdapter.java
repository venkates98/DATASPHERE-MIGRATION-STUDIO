package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.SqlDialect;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Vendor plug-in: connection details, JDBC driver, quoting, dialect and capability flags.
 * Discovery stays on {@code DatabaseMetaData} unless an adapter overrides it.
 */
public interface DatabaseAdapter {

    DatabaseType type();

    String displayName();

    String driverClassName();

    int defaultPort();

    String databaseFieldLabel();

    boolean databaseIsSchema();

    Set<String> systemSchemas();

    SqlDialect dialect();

    AdapterCapabilities capabilities();

    List<ConnectionField> connectionFields();

    String jdbcUrl(String host, int port, String database, boolean trustServerCertificate, int loginTimeoutSec);

    Properties connectionProperties(String user, char[] password, int connectTimeoutSec, int readTimeoutSec);

    String defaultSchema(String database, String username);

    default boolean driverAvailable() {
        try {
            Class.forName(driverClassName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    default boolean isSystemSchema(String schema) {
        return schema != null && systemSchemas().contains(schema.toUpperCase(Locale.ROOT));
    }

    default String quote(String identifier) {
        return dialect().quote(identifier);
    }

    default String qualified(String schema, String table) {
        return dialect().qualified(schema, table);
    }
}
