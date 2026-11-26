package com.datasphere.migration.engine;

/**
 * What to do when an insert hits a unique or primary-key constraint. Overwrite and truncate are deliberately
 * absent: the engine never drops, truncates or updates existing target rows.
 */
public enum InsertMode {
    FAIL_ON_DUPLICATE,
    SKIP_DUPLICATE
}
