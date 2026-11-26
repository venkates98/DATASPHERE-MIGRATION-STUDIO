package com.datasphere.migration.exception;

/** A table or column mapping is incomplete, ambiguous, duplicated or otherwise invalid. */
public class InvalidMappingException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidMappingException(String technicalDetail) {
        super(ErrorCode.MAP_VAL_001, technicalDetail);
    }

    public InvalidMappingException(ErrorCode errorCode, String technicalDetail) {
        super(errorCode, technicalDetail);
    }
}
