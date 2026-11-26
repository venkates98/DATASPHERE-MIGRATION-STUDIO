package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.OracleDialect;
import com.datasphere.migration.db.dialect.PaginationStyle;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class OracleAdapter extends AbstractJdbcAdapter {

    public OracleAdapter() {
        super(DatabaseType.ORACLE, "Oracle Database", "oracle.jdbc.OracleDriver", 1521, "Service name", false,
                Set.of("SYS", "SYSTEM", "OUTLN", "XDB", "CTXSYS", "MDSYS", "ORDSYS", "ORDDATA", "ORDPLUGINS", "WMSYS",
                        "APPQOSSYS", "DBSNMP", "GSMADMIN_INTERNAL", "LBACSYS", "OJVMSYS", "DVSYS", "DVF", "AUDSYS",
                        "DIP", "ANONYMOUS", "XS$NULL", "OLAPSYS", "SI_INFORMTN_SCHEMA", "REMOTE_SCHEDULER_AGENT",
                        "SYSBACKUP", "SYSDG", "SYSKM", "SYSRAC", "SYS$UMF", "GGSYS", "ORACLE_OCM", "PDBADMIN",
                        "GSMCATUSER", "GSMUSER", "GSMROOTUSER", "FLOWS_FILES", "APEX_PUBLIC_USER", "MDDATA",
                        "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "EXFSYS", "DMSYS", "TSMSYS"),
                new OracleDialect(),
                AdapterCapabilities.jdbcBaseline(PaginationStyle.OFFSET_FETCH, false, true, List.of(
                        "Connects by service name only (no SID, no TNS alias).",
                        "Live Oracle servers have not been used in automated tests (H2 compatibility mode is).")));
    }

    @Override
    public String jdbcUrl(String host, int port, String database, boolean trustServerCertificate, int loginTimeoutSec) {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
    }

    @Override
    public Properties connectionProperties(String user, char[] password, int connectTimeoutSec, int readTimeoutSec) {
        Properties p = userAndPassword(user, password);
        p.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(connectTimeoutSec * 1000));
        p.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(readTimeoutSec * 1000));
        return p;
    }

    @Override
    public String defaultSchema(String database, String username) {
        return username == null ? null : username.toUpperCase(Locale.ROOT);
    }
}
