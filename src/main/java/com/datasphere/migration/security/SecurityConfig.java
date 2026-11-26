package com.datasphere.migration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.SimpleRedirectInvalidSessionStrategy;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService attempts)
            throws Exception {

        SimpleRedirectInvalidSessionStrategy invalidSession = new SimpleRedirectInvalidSessionStrategy("/login?expired");
        invalidSession.setCreateNewSession(true);

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/login", "/error", "/css/**", "/js/**", "/img/**",
                                "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(new LoginAuditHandlers.Success(attempts, "/dashboard"))
                        .failureHandler(new LoginAuditHandlers.Failure(attempts))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")   // POST only (CSRF protected)
                        .logoutSuccessHandler(new LoginAuditHandlers.Logout())
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("DSMSESSION"))
                .sessionManagement(sm -> sm
                        .sessionFixation(fix -> fix.changeSessionId())
                        .invalidSessionStrategy(invalidSession))
                .csrf(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .addFilterBefore(new LoginThrottleFilter(attempts), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
