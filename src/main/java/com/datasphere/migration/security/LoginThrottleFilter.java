package com.datasphere.migration.security;

import com.datasphere.migration.util.RequestUtils;
import com.datasphere.migration.util.SensitiveDataMasker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects login submissions from locked-out clients/usernames <em>before</em> credentials are checked,
 * so a correct password cannot be used to bypass throttling.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger auditLog = LoggerFactory.getLogger("com.datasphere.migration.audit.login");

    private final LoginAttemptService attempts;

    public LoginThrottleFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(servletPath(request)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = RequestUtils.clientAddress(request);
        String username = request.getParameter("username");
        if (attempts.isBlocked(LoginAttemptService.ipKey(ip), LoginAttemptService.userKey(username))) {
            auditLog.warn("Login rejected: throttled ip={} username={} result=LOCKED", ip,
                    SensitiveDataMasker.sanitizeForLog(username, 64));
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
