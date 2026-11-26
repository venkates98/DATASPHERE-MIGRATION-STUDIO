package com.datasphere.migration.exception;

/** The migration plan or application configuration is invalid or incomplete. */
public class MigrationConfigurationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public MigrationConfigurationException(String technicalDetail) {
        super(ErrorCode.CFG_MIG_001, technicalDetail);
    }

    public MigrationConfigurationException(ErrorCode errorCode, String technicalDetail) {
        super(errorCode, technicalDetail);
    }
}
