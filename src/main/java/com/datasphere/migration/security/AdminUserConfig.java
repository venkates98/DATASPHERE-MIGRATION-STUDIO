package com.datasphere.migration.security;

import com.datasphere.migration.config.AppProperties;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Builds the single initial administrator from externally supplied settings.
 *
 * <p>The application refuses to start when no administrator hash is configured, when the hash is not BCrypt,
 * or when the development profile is combined with the production profile. This guarantees that the
 * development account can never be an enabled production administrator by accident.
 */
@Configuration
public class AdminUserConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminUserConfig.class);

    /** {bcrypt} prefix optional; cost factor two digits; 53 chars of hash. */
    private static final Pattern BCRYPT_HASH = Pattern.compile("^(\\{bcrypt\\})?\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Only BCrypt is registered: {noop}, {md5} and other weak formats are rejected outright.
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    public UserDetailsService userDetailsService(AppProperties properties, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("dev & prod"))) {
            throw new IllegalStateException(
                    "The 'dev' profile contains a development-only account and must not be combined with 'prod'.");
        }
        AppProperties.Admin admin = properties.security().admin();
        if (admin.username() == null || admin.username().isBlank()) {
            throw new IllegalStateException("No administrator configured. Set DATASPHERE_ADMIN_USERNAME and "
                    + "DATASPHERE_ADMIN_PASSWORD_HASH (see .env.example), or run locally with the 'dev' profile.");
        }
        String hash = admin.passwordHash();
        if (hash == null || !BCRYPT_HASH.matcher(hash.trim()).matches()) {
            throw new IllegalStateException(
                    "DATASPHERE_ADMIN_PASSWORD_HASH must be a BCrypt hash (optionally prefixed with {bcrypt}). "
                            + "Plain-text passwords are not accepted.");
        }
        String normalised = hash.trim().startsWith("{bcrypt}") ? hash.trim() : "{bcrypt}" + hash.trim();
        UserDetails user = User.withUsername(admin.username().trim())
                .password(normalised)
                .roles("ADMIN")
                .build();
        log.info("Administrator account configured username={} (password hash not logged)", user.getUsername());
        return new InMemoryUserDetailsManager(user);
    }
}
