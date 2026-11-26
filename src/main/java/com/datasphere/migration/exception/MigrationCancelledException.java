package com.datasphere.migration.exception;

/** Thrown at a safe batch boundary when a running migration has been cancelled by the user. */
public class MigrationCancelledException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public MigrationCancelledException(String technicalDetail) {
        super(ErrorCode.MIG_CANCEL_001, technicalDetail);
    }
}
