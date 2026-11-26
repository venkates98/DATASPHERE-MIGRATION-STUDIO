package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.DatabaseType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static registry of built-in adapters. Adding a vendor later means: driver dependency, adapter class, dialect
 * type mappings, one {@code register} call here, and tests. The mapping engine does not change.
 *
 * <p>MariaDB, DB2 and SQLite are not registered and must not be selected.
 */
public final class DatabaseAdapters {

    private static final Map<DatabaseType, DatabaseAdapter> BY_TYPE = new EnumMap<>(DatabaseType.class);

    static {
        register(new SqlServerAdapter());
        register(new OracleAdapter());
        register(new PostgreSqlAdapter());
        register(new MySqlAdapter());
    }

    private DatabaseAdapters() {
    }

    private static void register(DatabaseAdapter adapter) {
        BY_TYPE.put(adapter.type(), adapter);
    }

    public static DatabaseAdapter require(DatabaseType type) {
        DatabaseAdapter adapter = BY_TYPE.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter is registered for " + type);
        }
        return adapter;
    }

    public static List<DatabaseAdapter> all() {
        return List.copyOf(BY_TYPE.values());
    }
}
