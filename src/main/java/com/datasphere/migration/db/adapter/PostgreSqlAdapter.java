package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.PaginationStyle;
import com.datasphere.migration.db.dialect.PostgreSqlDialect;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class PostgreSqlAdapter extends AbstractJdbcAdapter {

    public PostgreSqlAdapter() {
        super(DatabaseType.POSTGRESQL, "PostgreSQL", "org.postgresql.Driver", 5432, "Database name", false,
                Set.of("INFORMATION_SCHEMA", "PG_CATALOG", "PG_TOAST"),
                new PostgreSqlDialect(),
                AdapterCapabilities.jdbcBaseline(PaginationStyle.LIMIT_OFFSET, false, true, List.of(
                        "Live PostgreSQL servers have not been used in automated tests (H2 compatibility mode is).")));
    }

    @Override
    public String jdbcUrl(String host, int port, String database, boolean trustServerCertificate, int loginTimeoutSec) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    public Properties connectionProperties(String user, char[] password, int connectTimeoutSec, int readTimeoutSec) {
        Properties p = userAndPassword(user, password);
        p.setProperty("connectTimeout", String.valueOf(connectTimeoutSec));
        p.setProperty("loginTimeout", String.valueOf(connectTimeoutSec));
        p.setProperty("socketTimeout", String.valueOf(readTimeoutSec));
        p.setProperty("ApplicationName", "DataSphere Migration Studio");
        return p;
    }

    @Override
    public String defaultSchema(String database, String username) {
        return "public";
    }
}
