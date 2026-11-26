package com.datasphere.migration.db;

import com.datasphere.migration.db.adapter.DatabaseAdapter;
import com.datasphere.migration.db.adapter.DatabaseAdapters;
import java.util.Properties;
import java.util.Set;

/**
 * Identifiers for registered database products. Vendor behaviour lives in {@link DatabaseAdapter}
 * implementations; this enum is the stable ID used in JSON, forms and APIs.
 *
 * <p>Adding a product: driver dependency, adapter + dialect, register in {@link DatabaseAdapters},
 * {@link TypeNormalizer} only if native names are unusual, and tests. Do not add an enum value without an adapter.
 */
public enum DatabaseType {

    SQLSERVER("Microsoft SQL Server", 1433, "Database name", false, '[', ']',
            Set.of("INFORMATION_SCHEMA", "SYS", "GUEST", "DB_OWNER", "DB_ACCESSADMIN", "DB_SECURITYADMIN",
                    "DB_DDLADMIN", "DB_BACKUPOPERATOR", "DB_DATAREADER", "DB_DATAWRITER", "DB_DENYDATAREADER",
                    "DB_DENYDATAWRITER")),

    ORACLE("Oracle Database", 1521, "Service name", false, '"', '"',
            Set.of("SYS", "SYSTEM", "OUTLN", "XDB", "CTXSYS", "MDSYS", "ORDSYS", "ORDDATA", "ORDPLUGINS", "WMSYS",
                    "APPQOSSYS", "DBSNMP", "GSMADMIN_INTERNAL", "LBACSYS", "OJVMSYS", "DVSYS", "DVF", "AUDSYS", "DIP",
                    "ANONYMOUS", "XS$NULL", "OLAPSYS", "SI_INFORMTN_SCHEMA", "REMOTE_SCHEDULER_AGENT", "SYSBACKUP",
                    "SYSDG", "SYSKM", "SYSRAC", "SYS$UMF", "GGSYS", "ORACLE_OCM", "PDBADMIN", "GSMCATUSER",
                    "GSMUSER", "GSMROOTUSER", "FLOWS_FILES", "APEX_PUBLIC_USER", "MDDATA", "SPATIAL_CSW_ADMIN_USR",
                    "SPATIAL_WFS_ADMIN_USR", "EXFSYS", "DMSYS", "TSMSYS")),

    POSTGRESQL("PostgreSQL", 5432, "Database name", false, '"', '"',
            Set.of("INFORMATION_SCHEMA", "PG_CATALOG", "PG_TOAST")),

    /** MySQL has no schema level below the database, so the database name doubles as the schema. */
    MYSQL("MySQL / MariaDB", 3306, "Database name", true, '`', '`',
            Set.of("INFORMATION_SCHEMA", "MYSQL", "PERFORMANCE_SCHEMA", "SYS"));

    private final String label;
    private final int defaultPort;
    private final String databaseLabel;
    private final boolean databaseIsSchema;
    private final char quoteOpen;
    private final char quoteClose;
    private final Set<String> systemSchemas;

    DatabaseType(String label, int defaultPort, String databaseLabel, boolean databaseIsSchema,
                 char quoteOpen, char quoteClose, Set<String> systemSchemas) {
        this.label = label;
        this.defaultPort = defaultPort;
        this.databaseLabel = databaseLabel;
        this.databaseIsSchema = databaseIsSchema;
        this.quoteOpen = quoteOpen;
        this.quoteClose = quoteClose;
        this.systemSchemas = systemSchemas;
    }

    public String label() {
        return label;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** Caption for the "database / service name" field of this product. */
    public String databaseLabel() {
        return databaseLabel;
    }

    /** True when JDBC "catalog" is the only namespace level (MySQL). */
    public boolean databaseIsSchema() {
        return databaseIsSchema;
    }

    public boolean isSystemSchema(String schema) {
        return adapter().isSystemSchema(schema);
    }

    /** The registered adapter for this product. */
    public DatabaseAdapter adapter() {
        return DatabaseAdapters.require(this);
    }

    /**
     * Builds the JDBC URL. The password never appears in it; user and password travel in
     * {@link #connectionProperties}. Inputs have already been validated against strict patterns.
     */
    public String jdbcUrl(String host, int port, String database, boolean trustServerCertificate, int loginTimeoutSec) {
        return adapter().jdbcUrl(host, port, database, trustServerCertificate, loginTimeoutSec);
    }

    /** Credentials plus connect/read time-outs, so a silent network can never hang a request thread. */
    public Properties connectionProperties(String user, char[] password, int connectTimeoutSec, int readTimeoutSec) {
        return adapter().connectionProperties(user, password, connectTimeoutSec, readTimeoutSec);
    }

    /** Quotes one identifier for use in generated SQL text (never built from raw user text). */
    public String quote(String identifier) {
        return adapter().quote(identifier);
    }

    /** {@code schema.table} with each part quoted. */
    public String qualified(String schema, String table) {
        return adapter().qualified(schema, table);
    }

    /** Schema used when the user does not choose one. */
    public String defaultSchema(String database, String username) {
        return adapter().defaultSchema(database, username);
    }
}
