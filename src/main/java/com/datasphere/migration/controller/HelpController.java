package com.datasphere.migration.controller;

import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.service.FeatureGuideCatalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HelpController {

    private final FeatureGuideCatalog catalog;

    public HelpController(FeatureGuideCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/help")
    public String help(Model model) {
        model.addAttribute("guide", catalog.guideFor(MenuItem.HELP));
        model.addAttribute("errorCodes", ErrorCode.values());
        return "help";
    }
}
