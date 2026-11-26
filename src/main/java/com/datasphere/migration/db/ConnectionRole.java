package com.datasphere.migration.db;

import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import java.util.Locale;

/** Which side of the migration a database plays. */
public enum ConnectionRole {
    SOURCE, TARGET;

    /** Parses a URL path segment such as {@code source} or {@code TARGET}. */
    public static ConnectionRole parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to the classified error below
            }
        }
        throw new ApplicationException(ErrorCode.CFG_REQ_001, "Unknown connection role");
    }
}
