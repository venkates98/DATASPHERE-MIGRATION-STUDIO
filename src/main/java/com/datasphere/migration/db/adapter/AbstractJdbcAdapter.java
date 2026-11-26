package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.SqlDialect;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Shared connection-form fields and property helpers. */
public abstract class AbstractJdbcAdapter implements DatabaseAdapter {

    private final DatabaseType type;
    private final String displayName;
    private final String driverClassName;
    private final int defaultPort;
    private final String databaseFieldLabel;
    private final boolean databaseIsSchema;
    private final Set<String> systemSchemas;
    private final SqlDialect dialect;
    private final AdapterCapabilities capabilities;

    protected AbstractJdbcAdapter(DatabaseType type, String displayName, String driverClassName, int defaultPort,
                                  String databaseFieldLabel, boolean databaseIsSchema, Set<String> systemSchemas,
                                  SqlDialect dialect, AdapterCapabilities capabilities) {
        this.type = type;
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
        this.databaseFieldLabel = databaseFieldLabel;
        this.databaseIsSchema = databaseIsSchema;
        this.systemSchemas = Set.copyOf(systemSchemas);
        this.dialect = dialect;
        this.capabilities = capabilities;
    }

    @Override
    public final DatabaseType type() {
        return type;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final String driverClassName() {
        return driverClassName;
    }

    @Override
    public final int defaultPort() {
        return defaultPort;
    }

    @Override
    public final String databaseFieldLabel() {
        return databaseFieldLabel;
    }

    @Override
    public final boolean databaseIsSchema() {
        return databaseIsSchema;
    }

    @Override
    public final Set<String> systemSchemas() {
        return systemSchemas;
    }

    @Override
    public final SqlDialect dialect() {
        return dialect;
    }

    @Override
    public final AdapterCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public List<ConnectionField> connectionFields() {
        List<ConnectionField> fields = new ArrayList<>();
        fields.add(new ConnectionField("host", "Host", ConnectionField.Kind.TEXT, true, "db.example.internal",
                "Host name or IPv4 address. IPv6 is not supported."));
        fields.add(new ConnectionField("port", "Port", ConnectionField.Kind.NUMBER, true, String.valueOf(defaultPort),
                "TCP port. The default is " + defaultPort + " and can be changed."));
        fields.add(new ConnectionField("database", databaseFieldLabel, ConnectionField.Kind.TEXT, true, null, null));
        if (!databaseIsSchema) {
            fields.add(new ConnectionField("schema", "Schema (optional)", ConnectionField.Kind.TEXT, false, null,
                    "Leave blank to use the product default after connecting."));
        }
        fields.add(new ConnectionField("username", "User name", ConnectionField.Kind.TEXT, true, null, null));
        fields.add(new ConnectionField("password", "Password", ConnectionField.Kind.PASSWORD, true, null,
                "Kept in memory only; never written to disk."));
        extraFields(fields);
        return List.copyOf(fields);
    }

    protected void extraFields(List<ConnectionField> fields) {
        // subclasses add vendor-only options
    }

    protected static Properties userAndPassword(String user, char[] password) {
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", new String(password));
        return p;
    }
}
