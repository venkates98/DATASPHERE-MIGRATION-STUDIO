package com.datasphere.migration.controller;

import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.service.FeatureGuideCatalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CompatibilityPageController {

    private final FeatureGuideCatalog catalog;

    public CompatibilityPageController(FeatureGuideCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/schema-comparison")
    public String page(Model model) {
        model.addAttribute("guide", catalog.guideFor(MenuItem.COMPARISON));
        return "compatibility";
    }
}
