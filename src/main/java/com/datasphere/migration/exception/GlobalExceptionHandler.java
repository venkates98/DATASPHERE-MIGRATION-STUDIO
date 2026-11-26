package com.datasphere.migration.exception;

import com.datasphere.migration.dto.ErrorResponse;
import com.datasphere.migration.dto.ErrorResponse.FieldIssue;
import com.datasphere.migration.logging.CorrelationIdFilter;
import com.datasphere.migration.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts every failure into a safe, classified response. Technical detail goes to the protected
 * server log together with the correlation ID; the browser only receives {@link ErrorResponse}.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public Object handleApplication(ApplicationException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        String cid = CorrelationIdFilter.resolve(request);
        if (code.httpStatus().is5xxServerError()) {
            log.error("Operation failed code={} category={} cid={} detail={}", code.code(), code.category(), cid,
                    ex.getMessage(), ex);
        } else {
            log.warn("Operation rejected code={} category={} cid={} detail={}", code.code(), code.category(), cid,
                    ex.getMessage());
        }
        return respond(request, ErrorResponse.of(code, cid, List.of(), ex.getUserDetails()));
    }

    @ExceptionHandler(BindException.class)
    public Object handleBinding(BindException ex, HttpServletRequest request) {
        List<FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toIssue)
                .toList();
        return validation(request, issues, ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldIssue> issues = ex.getConstraintViolations().stream()
                .map(v -> new FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(request, issues, "constraint violation");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Object handleBadRequest(Exception ex, HttpServletRequest request) {
        String cid = CorrelationIdFilter.resolve(request);
        log.warn("Bad request code={} cid={} type={}", ErrorCode.CFG_REQ_001.code(), cid, ex.getClass().getSimpleName());
        return respond(request, ErrorResponse.of(ErrorCode.CFG_REQ_001, cid));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String cid = CorrelationIdFilter.resolve(request);
        log.warn("Method not allowed code={} cid={} method={}", ErrorCode.CFG_METHOD_001.code(), cid, ex.getMethod());
        return respond(request, ErrorResponse.of(ErrorCode.CFG_METHOD_001, cid));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        String cid = CorrelationIdFilter.resolve(request);
        log.warn("Not found code={} cid={}", ErrorCode.CFG_NOTFOUND_001.code(), cid);
        return respond(request, ErrorResponse.of(ErrorCode.CFG_NOTFOUND_001, cid));
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        // Security exceptions must reach Spring Security's own handlers (login redirect / 403).
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        String cid = CorrelationIdFilter.resolve(request);
        log.error("Unexpected error code={} cid={}", ErrorCode.SYS_UNEXPECTED_001.code(), cid, ex);
        return respond(request, ErrorResponse.of(ErrorCode.SYS_UNEXPECTED_001, cid));
    }

    private Object validation(HttpServletRequest request, List<FieldIssue> issues, String technical) {
        String cid = CorrelationIdFilter.resolve(request);
        log.warn("Validation failed code={} cid={} fields={} detail={}", ErrorCode.CFG_VAL_001.code(), cid,
                issues.stream().map(FieldIssue::field).toList(), technical);
        return respond(request, ErrorResponse.of(ErrorCode.CFG_VAL_001, cid, issues));
    }

    private static FieldIssue toIssue(FieldError error) {
        return new FieldIssue(error.getField(), error.getDefaultMessage());
    }

    private Object respond(HttpServletRequest request, ErrorResponse body) {
        HttpStatusCode status = HttpStatusCode.valueOf(body.status());
        if (RequestUtils.wantsJson(request)) {
            return ResponseEntity.status(status).body(body);
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("error", body);
        return mav;
    }
}
