package com.datasphere.migration.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts credentials and secrets from free text before it is logged or shown.
 * This is a defence-in-depth measure: callers must still avoid passing secrets to loggers.
 */
public final class SensitiveDataMasker {

    public static final String MASK = "****";

    /** key=value, key: value, "key":"value" and JDBC property style (key=value;) for sensitive key names. */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(\"?(?:password|passwd|pwd|secret|token|api[_-]?key|credentials?|authorization|"
                    + "trustStorePassword|keyStorePassword|passwordHash|password-hash)\"?\\s*[=:]\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s;,&\"')]+)");

    /** user:password@host inside URLs. */
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://[^/\\s:@]+:)([^@\\s/]+)(@)");

    /** Oracle thin style jdbc:oracle:thin:user/password@host. */
    private static final Pattern ORACLE_INLINE = Pattern.compile("(?i)(jdbc:oracle:thin:[^/@\\s]+/)([^@\\s]+)(@)");

    /** Bearer tokens. */
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");

    /** BCrypt hashes should never appear in logs. */
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    private SensitiveDataMasker() {
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Bearer tokens first: otherwise "Authorization: Bearer <token>" would only have the word "Bearer" masked.
        String result = BEARER.matcher(input).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + MASK));
        result = KEY_VALUE.matcher(result).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + MASK));
        result = URL_USERINFO.matcher(result)
                .replaceAll(m -> Matcher.quoteReplacement(m.group(1) + MASK + m.group(3)));
        result = ORACLE_INLINE.matcher(result)
                .replaceAll(m -> Matcher.quoteReplacement(m.group(1) + MASK + m.group(3)));
        result = BCRYPT.matcher(result).replaceAll(MASK);
        return result;
    }

    /**
     * Makes user-controlled text safe for single-line log output (prevents log forging)
     * and limits its length.
     */
    public static String sanitizeForLog(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(input.length(), maxLength));
        for (int i = 0; i < input.length() && sb.length() < maxLength; i++) {
            char c = input.charAt(i);
            sb.append(Character.isISOControl(c) ? '_' : c);
        }
        return mask(sb.toString());
    }
}
