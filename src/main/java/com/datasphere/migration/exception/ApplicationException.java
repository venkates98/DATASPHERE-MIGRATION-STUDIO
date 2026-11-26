package com.datasphere.migration.exception;

import java.util.List;
import java.util.Objects;

/**
 * Base class for all application-specific failures.
 *
 * <p>{@link #getMessage()} carries <em>technical</em> diagnostics for protected server logs only.
 * What the user sees is derived from the {@link ErrorCode}, optionally extended with
 * {@link #getUserDetails()}: short, application-authored sentences (never database output, SQL or
 * credentials) that explain exactly what must be fixed.
 */
public class ApplicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;
    private final transient List<String> userDetails;

    public ApplicationException(ErrorCode errorCode, String technicalDetail) {
        this(errorCode, technicalDetail, null, List.of());
    }

    public ApplicationException(ErrorCode errorCode, String technicalDetail, Throwable cause) {
        this(errorCode, technicalDetail, cause, List.of());
    }

    public ApplicationException(ErrorCode errorCode, String technicalDetail, List<String> userDetails) {
        this(errorCode, technicalDetail, null, userDetails);
    }

    public ApplicationException(ErrorCode errorCode, String technicalDetail, Throwable cause,
                                List<String> userDetails) {
        super(technicalDetail, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.userDetails = userDetails == null ? List.of() : List.copyOf(userDetails);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Safe, user-facing explanations to show beneath the classified error message. */
    public List<String> getUserDetails() {
        return userDetails;
    }
}
