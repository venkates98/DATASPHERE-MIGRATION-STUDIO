package com.datasphere.migration.security;

import com.datasphere.migration.util.RequestUtils;
import com.datasphere.migration.util.SensitiveDataMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * Login/logout outcome handlers. They update the throttle and write audit events — never the password.
 */
public final class LoginAuditHandlers {

    private static final Logger auditLog = LoggerFactory.getLogger("com.datasphere.migration.audit.login");

    private LoginAuditHandlers() {
    }

    public static class Success extends SavedRequestAwareAuthenticationSuccessHandler {

        private final LoginAttemptService attempts;

        public Success(LoginAttemptService attempts, String defaultTargetUrl) {
            this.attempts = attempts;
            setDefaultTargetUrl(defaultTargetUrl);
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException, jakarta.servlet.ServletException {
            String ip = RequestUtils.clientAddress(request);
            attempts.recordSuccess(LoginAttemptService.ipKey(ip), LoginAttemptService.userKey(authentication.getName()));
            auditLog.info("Login succeeded user={} ip={} result=SUCCESS",
                    SensitiveDataMasker.sanitizeForLog(authentication.getName(), 64), ip);
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    public static class Failure implements AuthenticationFailureHandler {

        private final LoginAttemptService attempts;

        public Failure(LoginAttemptService attempts) {
            this.attempts = attempts;
        }

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                            AuthenticationException exception) throws IOException {
            String ip = RequestUtils.clientAddress(request);
            String username = request.getParameter("username");
            boolean locked = attempts.recordFailure(LoginAttemptService.ipKey(ip),
                    LoginAttemptService.userKey(username));
            // Only the exception type is logged; never the credentials, and never any message that could echo them.
            auditLog.warn("Login failed user={} ip={} reason={} result={}",
                    SensitiveDataMasker.sanitizeForLog(username, 64), ip, exception.getClass().getSimpleName(),
                    locked ? "LOCKED" : "FAILURE");
            response.sendRedirect(request.getContextPath() + (locked ? "/login?locked" : "/login?error"));
        }
    }

    public static class Logout implements LogoutSuccessHandler {

        @Override
        public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                    Authentication authentication) throws IOException {
            if (authentication != null) {
                auditLog.info("Logout user={} ip={} result=SUCCESS",
                        SensitiveDataMasker.sanitizeForLog(authentication.getName(), 64),
                        RequestUtils.clientAddress(request));
            }
            response.sendRedirect(request.getContextPath() + "/login?logout");
        }
    }
}
