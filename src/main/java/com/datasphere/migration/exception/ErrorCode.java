package com.datasphere.migration.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes with a user-safe message and suggested corrective action.
 * Codes are part of the public contract: never renumber or reuse them.
 */
public enum ErrorCode {

    // Authentication / authorisation
    AUTH_LOGIN_001("AUTH-LOGIN-001", ErrorCategory.AUTHENTICATION, HttpStatus.UNAUTHORIZED,
            "Sign-in failed.", "Check your username and password and try again."),
    AUTH_LOCK_001("AUTH-LOCK-001", ErrorCategory.AUTHENTICATION, HttpStatus.TOO_MANY_REQUESTS,
            "Too many failed sign-in attempts.", "Wait for the lock-out period to end, then try again."),
    AUTH_SESSION_001("AUTH-SESSION-001", ErrorCategory.AUTHENTICATION, HttpStatus.UNAUTHORIZED,
            "Your session has expired.", "Sign in again to continue."),
    PERM_ACCESS_001("PERM-ACCESS-001", ErrorCategory.PERMISSION, HttpStatus.FORBIDDEN,
            "You do not have permission to perform this action.",
            "Sign in with an account that has access, or ask an administrator."),
    PERM_DB_001("PERM-DB-001", ErrorCategory.PERMISSION, HttpStatus.UNPROCESSABLE_ENTITY,
            "The database account lacks a required permission.",
            "Grant the missing SELECT, INSERT or UPDATE permission to the database user and retry."),

    // Configuration / request validation
    CFG_VAL_001("CFG-VAL-001", ErrorCategory.CONFIGURATION, HttpStatus.BAD_REQUEST,
            "The submitted data is invalid.", "Correct the highlighted fields and submit again."),
    CFG_REQ_001("CFG-REQ-001", ErrorCategory.CONFIGURATION, HttpStatus.BAD_REQUEST,
            "The request could not be understood.", "Reload the page and try again."),
    CFG_NOTFOUND_001("CFG-NOTFOUND-001", ErrorCategory.CONFIGURATION, HttpStatus.NOT_FOUND,
            "The requested page or item was not found.", "Use the menu to navigate to an available page."),
    CFG_METHOD_001("CFG-METHOD-001", ErrorCategory.CONFIGURATION, HttpStatus.METHOD_NOT_ALLOWED,
            "This operation is not supported for the requested address.", "Return to the previous page."),
    CFG_MIG_001("CFG-MIG-001", ErrorCategory.CONFIGURATION, HttpStatus.UNPROCESSABLE_ENTITY,
            "The migration configuration is incomplete or invalid.",
            "Review the migration plan and resolve every blocking validation error."),

    // Connectivity
    DB_CONN_001("DB-CONN-001", ErrorCategory.CONNECTIVITY, HttpStatus.BAD_GATEWAY,
            "Connection failed.", "Verify the hostname, port, network access, and database availability."),
    DB_AUTH_001("DB-AUTH-001", ErrorCategory.AUTHENTICATION, HttpStatus.UNAUTHORIZED,
            "Database authentication failed.", "Verify the database username and password."),
    DB_DRIVER_001("DB-DRIVER-001", ErrorCategory.CONFIGURATION, HttpStatus.UNPROCESSABLE_ENTITY,
            "The JDBC driver for this database is not available.",
            "Check that the driver is on the application classpath and the database type is supported."),
    DB_TIMEOUT_001("DB-TIMEOUT-001", ErrorCategory.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT,
            "The database did not respond in time.", "Check network latency and increase the timeout if appropriate."),
    DB_META_001("DB-META-001", ErrorCategory.METADATA, HttpStatus.UNPROCESSABLE_ENTITY,
            "Database metadata could not be read.",
            "Verify the account can read the catalog, then refresh the metadata."),

    DB_CRED_001("DB-CRED-001", ErrorCategory.CONNECTIVITY, HttpStatus.CONFLICT,
            "The database password is not available in this session.",
            "Passwords are never saved to disk. Re-enter the password on the Connections step and save the connection."),
    DB_HOST_001("DB-HOST-001", ErrorCategory.PERMISSION, HttpStatus.FORBIDDEN,
            "This database host is not permitted by the security policy.",
            "Ask an administrator to add the host or network to the allowed database hosts."),

    // Projects and mapping workflow
    PRJ_NOTFOUND_001("PRJ-NOTFOUND-001", ErrorCategory.CONFIGURATION, HttpStatus.NOT_FOUND,
            "The migration project was not found.", "Return to the project list and open an existing project."),
    PRJ_CONFLICT_001("PRJ-CONFLICT-001", ErrorCategory.CONFIGURATION, HttpStatus.CONFLICT,
            "The mapping was changed by another request since you loaded it.",
            "Reload the project to see the latest version, then repeat your change."),
    PRJ_STATE_001("PRJ-STATE-001", ErrorCategory.CONFIGURATION, HttpStatus.CONFLICT,
            "This step is not available in the project's current state.",
            "Complete the previous workflow steps first."),
    MAP_VAL_002("MAP-VAL-002", ErrorCategory.CONFIGURATION, HttpStatus.UNPROCESSABLE_ENTITY,
            "The column mapping did not pass validation.",
            "Resolve every error listed in the validation report, then validate again."),
    MAP_APPR_001("MAP-APPR-001", ErrorCategory.CONFIGURATION, HttpStatus.CONFLICT,
            "The mapping has not been approved.",
            "Review the mapping, validate it and click Approve Mapping & Continue."),
    MAP_APPR_002("MAP-APPR-002", ErrorCategory.CONFIGURATION, HttpStatus.CONFLICT,
            "The mapping approval is outdated because the mapping changed after it was approved.",
            "Review the changed mapping and approve it again."),
    MAP_META_001("MAP-META-001", ErrorCategory.METADATA, HttpStatus.CONFLICT,
            "The source or target database structure changed after the mapping was prepared.",
            "Refresh the metadata, review the mapping again and approve it again."),
    MIG_NOTIMPL_001("MIG-NOTIMPL-001", ErrorCategory.CONFIGURATION, HttpStatus.NOT_IMPLEMENTED,
            "The migration engine is not available in this build.",
            "All pre-migration checks passed. Data movement is delivered in a later development phase."),

    // Schema / mapping / data
    SCHEMA_VAL_001("SCHEMA-VAL-001", ErrorCategory.SCHEMA, HttpStatus.UNPROCESSABLE_ENTITY,
            "The source and target schemas are not compatible.",
            "Review the schema comparison and resolve the listed differences."),
    MAP_VAL_001("MAP-VAL-001", ErrorCategory.CONFIGURATION, HttpStatus.UNPROCESSABLE_ENTITY,
            "A target column has no valid source mapping.", "Complete the mapping before continuing."),
    DATA_CONV_001("DATA-CONV-001", ErrorCategory.DATA_CONVERSION, HttpStatus.UNPROCESSABLE_ENTITY,
            "A source value cannot be converted to the target column type.",
            "Review the affected column and conversion rule."),
    DATA_DUP_001("DATA-DUP-001", ErrorCategory.DUPLICATE_OR_CONSTRAINT, HttpStatus.CONFLICT,
            "A duplicate key exists in the target database.",
            "Choose an approved duplicate-handling strategy."),

    // Migration execution
    MIG_TXN_001("MIG-TXN-001", ErrorCategory.TRANSACTION, HttpStatus.INTERNAL_SERVER_ERROR,
            "A migration batch failed.",
            "Review the batch error details and transaction status before retrying."),
    MIG_CANCEL_001("MIG-CANCEL-001", ErrorCategory.TRANSACTION, HttpStatus.CONFLICT,
            "The migration was cancelled.",
            "Review the job report to see which batches were committed before cancellation."),
    MIG_EXEC_001("MIG-EXEC-001", ErrorCategory.TRANSACTION, HttpStatus.INTERNAL_SERVER_ERROR,
            "The migration could not be completed.", "Review the job log and correct the reported issue."),

    // Capacity and internal
    RES_LIMIT_001("RES-LIMIT-001", ErrorCategory.RESOURCE_EXHAUSTION, HttpStatus.SERVICE_UNAVAILABLE,
            "A configured resource limit was reached.",
            "Wait for running jobs to finish or reduce the size of the request."),
    SYS_UNEXPECTED_001("SYS-UNEXPECTED-001", ErrorCategory.UNEXPECTED, HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.",
            "Try again. If the problem persists, contact support and quote the correlation ID.");

    private final String code;
    private final ErrorCategory category;
    private final HttpStatus httpStatus;
    private final String userMessage;
    private final String suggestedAction;

    ErrorCode(String code, ErrorCategory category, HttpStatus httpStatus, String userMessage,
              String suggestedAction) {
        this.code = code;
        this.category = category;
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
        this.suggestedAction = suggestedAction;
    }

    public String code() {
        return code;
    }

    public ErrorCategory category() {
        return category;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String userMessage() {
        return userMessage;
    }

    public String suggestedAction() {
        return suggestedAction;
    }
}
