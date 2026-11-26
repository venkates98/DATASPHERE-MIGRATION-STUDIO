package com.datasphere.migration.exception;

/** A database could not be reached, authenticated against, or its driver is unavailable. */
public class DatabaseConnectionException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DatabaseConnectionException(ErrorCode errorCode, String technicalDetail, Throwable cause) {
        super(errorCode, technicalDetail, cause);
    }

    public DatabaseConnectionException(String technicalDetail, Throwable cause) {
        super(ErrorCode.DB_CONN_001, technicalDetail, cause);
    }
}
