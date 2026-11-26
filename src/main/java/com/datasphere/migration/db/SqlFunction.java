package com.datasphere.migration.db;

import java.sql.SQLException;

/** A unit of work against an open JDBC resource. */
@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T input) throws SQLException;
}
