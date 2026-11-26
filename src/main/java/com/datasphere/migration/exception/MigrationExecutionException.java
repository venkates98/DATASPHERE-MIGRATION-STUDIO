package com.datasphere.migration.exception;

/** A migration batch or job failed during execution. */
public class MigrationExecutionException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public MigrationExecutionException(ErrorCode errorCode, String technicalDetail, Throwable cause) {
        super(errorCode, technicalDetail, cause);
    }

    public MigrationExecutionException(String technicalDetail, Throwable cause) {
        super(ErrorCode.MIG_TXN_001, technicalDetail, cause);
    }
}
