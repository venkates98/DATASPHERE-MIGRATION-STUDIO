package com.datasphere.migration.controller;

import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.service.FeatureGuideCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the pages whose functionality is delivered in later phases. Each page documents the feature
 * and shows its operations as disabled with the reason, rather than offering controls that do nothing.
 * When a feature is implemented, move its path to a dedicated controller.
 */
@Controller
public class FeaturePageController {

    private final FeatureGuideCatalog catalog;

    public FeaturePageController(FeatureGuideCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping({"/migration", "/relationships", "/validation", "/jobs", "/logs", "/reports"})
    public String feature(HttpServletRequest request, Model model) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        MenuItem item = MenuItem.forPath(path).orElseThrow(
                () -> new ApplicationException(ErrorCode.CFG_NOTFOUND_001, "No menu item for path " + path));
        model.addAttribute("guide", catalog.guideFor(item));
        return "feature";
    }
}
