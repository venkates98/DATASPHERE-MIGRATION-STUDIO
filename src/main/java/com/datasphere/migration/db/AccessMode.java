package com.datasphere.migration.db;

/** Whether the JDBC connection may issue writes. Source connections are always {@link #READ}. */
public enum AccessMode {
    READ,
    WRITE
}
