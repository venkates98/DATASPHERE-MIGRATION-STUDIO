package com.datasphere.migration.controller;

import com.datasphere.migration.dto.ErrorResponse;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.logging.CorrelationIdFilter;
import com.datasphere.migration.util.RequestUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles errors raised outside controllers (container errors, security filters, 403/404 dispatches).
 * Always renders the same safe, classified response — never a stack trace.
 */
@Controller
public class AppErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(AppErrorController.class);

    @RequestMapping("/error")
    public Object error(HttpServletRequest request) {
        int statusValue = status(request);
        HttpStatus status = HttpStatus.resolve(statusValue);
        ErrorCode code = classify(status);
        String cid = CorrelationIdFilter.resolve(request);
        log.warn("Request error dispatched status={} code={} cid={}", statusValue, code.code(), cid);

        ErrorResponse body = new ErrorResponse(code.code(), code.category().label(), code.userMessage(),
                code.suggestedAction(), cid, java.time.Instant.now(), statusValue, java.util.List.of(),
                java.util.List.of());
        if (RequestUtils.wantsJson(request)) {
            return ResponseEntity.status(statusValue).body(body);
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("error", body);
        return mav;
    }

    private static int status(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return attr instanceof Integer i ? i : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static ErrorCode classify(HttpStatus status) {
        if (status == null) {
            return ErrorCode.SYS_UNEXPECTED_001;
        }
        return switch (status) {
            case UNAUTHORIZED -> ErrorCode.AUTH_SESSION_001;
            case FORBIDDEN -> ErrorCode.PERM_ACCESS_001;
            case NOT_FOUND -> ErrorCode.CFG_NOTFOUND_001;
            case METHOD_NOT_ALLOWED -> ErrorCode.CFG_METHOD_001;
            case BAD_REQUEST -> ErrorCode.CFG_REQ_001;
            default -> ErrorCode.SYS_UNEXPECTED_001;
        };
    }
}
