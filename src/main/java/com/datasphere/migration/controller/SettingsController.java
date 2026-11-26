package com.datasphere.migration.controller;

import com.datasphere.migration.config.AppProperties;
import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.service.FeatureGuideCatalog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Read-only view of the effective configuration. Secrets and hashes are never shown. */
@Controller
public class SettingsController {

    private final AppProperties properties;
    private final FeatureGuideCatalog catalog;
    private final Environment environment;
    private final Duration sessionTimeout;
    private final boolean secureCookie;

    public SettingsController(AppProperties properties, FeatureGuideCatalog catalog, Environment environment,
                              @Value("${server.servlet.session.timeout:30m}") Duration sessionTimeout,
                              @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.properties = properties;
        this.catalog = catalog;
        this.environment = environment;
        this.sessionTimeout = sessionTimeout;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/settings")
    public String settings(HttpServletRequest request, Model model) {
        model.addAttribute("guide", catalog.guideFor(MenuItem.SETTINGS));
        model.addAttribute("limits", properties.limits());
        model.addAttribute("login", properties.security().login());
        model.addAttribute("sessionTimeoutMinutes", sessionTimeout.toMinutes());
        model.addAttribute("secureCookie", secureCookie);
        model.addAttribute("httpsRequest", request.isSecure());
        model.addAttribute("lockoutMinutes", properties.security().login().lockoutDuration().toMinutes());
        model.addAttribute("devProfileActive", environment.acceptsProfiles(Profiles.of("dev")));
        return "settings";
    }
}
