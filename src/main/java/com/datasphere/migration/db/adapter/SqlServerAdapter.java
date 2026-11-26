package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.PaginationStyle;
import com.datasphere.migration.db.dialect.SqlServerDialect;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class SqlServerAdapter extends AbstractJdbcAdapter {

    public SqlServerAdapter() {
        super(DatabaseType.SQLSERVER, "Microsoft SQL Server",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "Database name", false,
                Set.of("INFORMATION_SCHEMA", "SYS", "GUEST", "DB_OWNER", "DB_ACCESSADMIN", "DB_SECURITYADMIN",
                        "DB_DDLADMIN", "DB_BACKUPOPERATOR", "DB_DATAREADER", "DB_DATAWRITER", "DB_DENYDATAREADER",
                        "DB_DENYDATAWRITER"),
                new SqlServerDialect(),
                AdapterCapabilities.jdbcBaseline(PaginationStyle.OFFSET_FETCH, true, false, List.of(
                        "Connections use encrypt=true. Tick 'Trust the server certificate' only for test servers.",
                        "IPv6 hosts are not supported.",
                        "Live SQL Server servers have not been used in automated tests (H2 compatibility mode is).")));
    }

    @Override
    protected void extraFields(List<ConnectionField> fields) {
        fields.add(new ConnectionField("trustServerCertificate", "Trust the server certificate",
                ConnectionField.Kind.CHECKBOX, false, null,
                "SQL Server only. Use for development servers with a self-signed certificate."));
    }

    @Override
    public String jdbcUrl(String host, int port, String database, boolean trustServerCertificate, int loginTimeoutSec) {
        return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=" + trustServerCertificate
                + ";loginTimeout=" + loginTimeoutSec;
    }

    @Override
    public Properties connectionProperties(String user, char[] password, int connectTimeoutSec, int readTimeoutSec) {
        Properties p = userAndPassword(user, password);
        p.setProperty("socketTimeout", String.valueOf(readTimeoutSec * 1000));
        return p;
    }

    @Override
    public String defaultSchema(String database, String username) {
        return "dbo";
    }
}
