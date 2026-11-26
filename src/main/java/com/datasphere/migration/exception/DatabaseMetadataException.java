package com.datasphere.migration.exception;

/** Schema, table or column metadata could not be discovered. */
public class DatabaseMetadataException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DatabaseMetadataException(String technicalDetail, Throwable cause) {
        super(ErrorCode.DB_META_001, technicalDetail, cause);
    }
}
