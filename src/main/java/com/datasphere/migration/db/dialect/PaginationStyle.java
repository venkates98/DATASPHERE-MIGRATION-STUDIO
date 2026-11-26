package com.datasphere.migration.db.dialect;

/** How a dialect pages through a result set. The engine prefers a streaming {@code ResultSet} either way. */
public enum PaginationStyle {
    OFFSET_FETCH,
    LIMIT_OFFSET,
    KEYSET
}
