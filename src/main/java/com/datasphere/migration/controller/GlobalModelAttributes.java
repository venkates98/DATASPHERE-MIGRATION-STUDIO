package com.datasphere.migration.controller;

import com.datasphere.migration.config.AppProperties;
import com.datasphere.migration.dto.CurrentUser;
import com.datasphere.migration.dto.MenuGroup;
import com.datasphere.migration.dto.MenuItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the model attributes shared by every page: branding, menu, active item and current user.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private static final List<MenuGroup> MENU = Arrays.stream(MenuItem.Group.values())
            .map(g -> new MenuGroup(g.label(), MenuItem.inGroup(g)))
            .toList();

    private final AppProperties properties;

    public GlobalModelAttributes(AppProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute
    public void populate(Model model, HttpServletRequest request) {
        model.addAttribute("appName", "DataSphere Migration Studio");
        model.addAttribute("appTagline", "Connect. Map. Validate. Migrate.");
        model.addAttribute("appVersion", properties.version());
        model.addAttribute("menuGroups", MENU);
        model.addAttribute("implementedPhase", MenuItem.IMPLEMENTED_PHASE);

        MenuItem.forPath(pathOf(request)).ifPresent(item -> {
            model.addAttribute("activeMenu", item);
            model.addAttribute("pageTitle", item.getTitle());
            model.addAttribute("pageDescription", item.getDescription());
        });

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            model.addAttribute("currentUser", toCurrentUser(auth));
        }
    }

    private CurrentUser toCurrentUser(Authentication auth) {
        String username = auth.getName();
        AppProperties.Admin admin = properties.security().admin();
        String display = admin.username() != null && admin.username().equalsIgnoreCase(username)
                ? admin.displayName() : username;
        return new CurrentUser(username, display, initials(display));
    }

    static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        // Only letters/digits count, so "Venkat (dev)" yields "VD" rather than "V(".
        String[] parts = name.trim().split("[^\\p{L}\\p{N}]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.appendCodePoint(part.codePointAt(0));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString().toUpperCase(Locale.ROOT);
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
