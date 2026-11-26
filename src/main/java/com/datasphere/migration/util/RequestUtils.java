package com.datasphere.migration.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

public final class RequestUtils {

    private RequestUtils() {
    }

    /** True when the caller expects JSON (API path or JSON ranked ahead of HTML in Accept). */
    public static boolean wantsJson(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        if (path.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        int json = accept.indexOf(MediaType.APPLICATION_JSON_VALUE);
        int html = accept.indexOf(MediaType.TEXT_HTML_VALUE);
        return json >= 0 && (html < 0 || json < html);
    }

    /** Client address as seen by the servlet container (proxy headers are intentionally not trusted). */
    public static String clientAddress(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }
}
