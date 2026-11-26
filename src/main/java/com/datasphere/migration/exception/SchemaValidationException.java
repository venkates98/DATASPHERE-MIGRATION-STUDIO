package com.datasphere.migration.exception;

/** Source and target schemas are incompatible for the requested operation. */
public class SchemaValidationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public SchemaValidationException(String technicalDetail) {
        super(ErrorCode.SCHEMA_VAL_001, technicalDetail);
    }
}
