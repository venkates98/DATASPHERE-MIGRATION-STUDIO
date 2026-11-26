package com.datasphere.migration.controller;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.project.MigrationProject;
import com.datasphere.migration.project.ProjectService;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Pages for the project list and the project workspace. All data is loaded through {@code /api}. */
@Controller
public class ProjectPageController {

    private final ProjectService projects;

    public ProjectPageController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping("/projects")
    public String list() {
        return "projects";
    }

    @GetMapping("/projects/{id}")
    public String workspace(@PathVariable String id, Model model) {
        MigrationProject project = projects.require(id); // 404 page for an unknown id
        model.addAttribute("projectId", project.getId());
        model.addAttribute("projectName", project.getName());
        model.addAttribute("databaseTypes", DatabaseType.values());
        return "project";
    }

    /** The old menu entries lead to the project workspace, where connections and mapping now live. */
    @GetMapping({"/connections", "/mapping"})
    public String legacyEntry() {
        Optional<MigrationProject> latest = projects.list().stream()
                .max(Comparator.comparing(MigrationProject::getUpdatedAt));
        return latest.map(p -> "redirect:/projects/" + p.getId()).orElse("redirect:/projects");
    }
}
