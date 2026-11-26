package com.datasphere.migration.exception;

/** A source value cannot be converted to the target type under the configured rule. */
public class DataConversionException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DataConversionException(String technicalDetail, Throwable cause) {
        super(ErrorCode.DATA_CONV_001, technicalDetail, cause);
    }
}
