package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.MySqlDialect;
import com.datasphere.migration.db.dialect.PaginationStyle;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class MySqlAdapter extends AbstractJdbcAdapter {

    public MySqlAdapter() {
        super(DatabaseType.MYSQL, "MySQL", "com.mysql.cj.jdbc.Driver", 3306, "Database name", true,
                Set.of("INFORMATION_SCHEMA", "MYSQL", "PERFORMANCE_SCHEMA", "SYS"),
                new MySqlDialect(),
                AdapterCapabilities.jdbcBaseline(PaginationStyle.LIMIT_OFFSET, true, false, List.of(
                        "MySQL has no schema below the database; the database name is the namespace.",
                        "MariaDB is not a separate adapter. A MariaDB server may work through the MySQL driver but is not tested.",
                        "Live MySQL servers have not been used in automated tests (H2 compatibility mode is).")));
    }

    @Override
    public String jdbcUrl(String host, int port, String database, boolean trustServerCertificate, int loginTimeoutSec) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    @Override
    public Properties connectionProperties(String user, char[] password, int connectTimeoutSec, int readTimeoutSec) {
        Properties p = userAndPassword(user, password);
        p.setProperty("connectTimeout", String.valueOf(connectTimeoutSec * 1000));
        p.setProperty("socketTimeout", String.valueOf(readTimeoutSec * 1000));
        return p;
    }

    @Override
    public String defaultSchema(String database, String username) {
        return database;
    }
}
