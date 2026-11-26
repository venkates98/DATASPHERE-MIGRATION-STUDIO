package com.datasphere.migration.controller;

import com.datasphere.migration.db.ConnectionRequest;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.ConnectionTestResult;
import com.datasphere.migration.mapping.engine.TablePairSuggester;
import com.datasphere.migration.mapping.type.Transformation;
import com.datasphere.migration.mapping.type.TypeCategory;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.metadata.TableRef;
import com.datasphere.migration.metadata.TableSummary;
import com.datasphere.migration.project.DiscoveryService;
import com.datasphere.migration.project.MappingChangeRequest;
import com.datasphere.migration.project.MappingWorkflowService;
import com.datasphere.migration.db.adapter.CompatibilityMatrixService;
import com.datasphere.migration.engine.MigrationEngine;
import com.datasphere.migration.engine.MigrationRun;
import com.datasphere.migration.engine.StartMigrationRequest;
import com.datasphere.migration.project.MigrationGateService;
import com.datasphere.migration.project.MigrationPreview;
import com.datasphere.migration.project.ProjectService;
import com.datasphere.migration.validation.ReconciliationReport;
import com.datasphere.migration.validation.ReconciliationService;
import com.datasphere.migration.project.ProjectView;
import com.datasphere.migration.project.ProjectViewFactory;
import com.datasphere.migration.project.TablePairRequest;
import com.datasphere.migration.project.ValidationReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for projects, discovery, mapping review and the migration gate. Every endpoint is authenticated
 * and CSRF-protected by the security configuration; none accepts SQL.
 */
@RestController
@RequestMapping("/api")
@Validated
public class ProjectApiController {

    public record CreateProjectRequest(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "[^\\p{Cntrl}<>]+", message = "The project name contains characters that are not allowed.")
            String name) {
    }

    public record SuggestRequest(boolean overwrite) {
    }

    public record ApproveRequest(int expectedVersion, boolean confirmReviewed) {
    }

    private final ProjectService projects;
    private final DiscoveryService discovery;
    private final MappingWorkflowService workflow;
    private final MigrationGateService gate;
    private final MigrationEngine engine;
    private final ReconciliationService reconciliation;
    private final CompatibilityMatrixService compatibility;
    private final ProjectViewFactory views;

    public ProjectApiController(ProjectService projects, DiscoveryService discovery, MappingWorkflowService workflow,
                                MigrationGateService gate, MigrationEngine engine,
                                ReconciliationService reconciliation, CompatibilityMatrixService compatibility,
                                ProjectViewFactory views) {
        this.projects = projects;
        this.discovery = discovery;
        this.workflow = workflow;
        this.gate = gate;
        this.engine = engine;
        this.reconciliation = reconciliation;
        this.compatibility = compatibility;
        this.views = views;
    }

    @GetMapping("/databases")
    public List<CompatibilityMatrixService.DatabaseCatalogEntry> databases() {
        return compatibility.catalog();
    }

    @GetMapping("/compatibility")
    public List<com.datasphere.migration.db.adapter.PairSupport> compatibility() {
        return compatibility.matrix();
    }

    // ------------------------------------------------------------------ connections

    @PostMapping("/connections/{role}/test")
    public ConnectionTestResult testConnection(@PathVariable String role, @Valid @RequestBody ConnectionRequest body) {
        return projects.test(ConnectionRole.parse(role), body);
    }

    // ------------------------------------------------------------------ projects

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView create(@Valid @RequestBody CreateProjectRequest body) {
        return views.view(projects.create(body.name()));
    }

    @GetMapping("/projects")
    public List<ProjectView.Summary> list() {
        return projects.list().stream().map(views::summary).toList();
    }

    @GetMapping("/projects/{id}")
    public ProjectView get(@PathVariable String id) {
        return views.view(projects.require(id));
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        projects.delete(id);
    }

    @PutMapping("/projects/{id}/connections/{role}")
    public ProjectView saveConnection(@PathVariable String id, @PathVariable String role,
                                      @Valid @RequestBody ConnectionRequest body) {
        return views.view(projects.saveConnection(id, ConnectionRole.parse(role), body));
    }

    // ------------------------------------------------------------------ discovery

    @GetMapping("/projects/{id}/schema/{role}/schemas")
    public List<String> schemas(@PathVariable String id, @PathVariable String role) {
        return discovery.schemas(id, ConnectionRole.parse(role));
    }

    @GetMapping("/projects/{id}/schema/{role}/tables")
    public List<TableSummary> tables(@PathVariable String id, @PathVariable String role,
                                     @RequestParam(defaultValue = "") @Size(max = 256) String schema) {
        return discovery.tables(id, ConnectionRole.parse(role), schema);
    }

    @GetMapping("/projects/{id}/schema/{role}/columns")
    public TableMetadata columns(@PathVariable String id, @PathVariable String role,
                                 @RequestParam(defaultValue = "") @Size(max = 256) String schema,
                                 @RequestParam @NotBlank @Size(max = 256) String table) {
        return discovery.table(id, ConnectionRole.parse(role), new TableRef(schema, table));
    }

    @PostMapping("/projects/{id}/tables/suggest")
    public List<TablePairSuggester.Suggestion> suggestTablePairs(
            @PathVariable String id,
            @RequestParam(defaultValue = "") @Size(max = 256) String sourceSchema,
            @RequestParam(defaultValue = "") @Size(max = 256) String targetSchema) {
        return discovery.suggestTablePairs(id, sourceSchema, targetSchema);
    }

    @PutMapping("/projects/{id}/tables")
    public ProjectView selectTables(@PathVariable String id, @Valid @RequestBody TablePairRequest body) {
        return views.view(discovery.selectTables(id, body));
    }

    @PostMapping("/projects/{id}/metadata/refresh")
    public Map<String, Object> refreshMetadata(@PathVariable String id) {
        DiscoveryService.RefreshResult result = discovery.refreshMetadata(id);
        return Map.of("changed", result.changed(), "changedTables", result.changedTables(),
                "project", views.view(projects.require(id)));
    }

    // ------------------------------------------------------------------ mapping

    /** The conversions that can be applied when reading {@code source} values into a {@code target} column. */
    @GetMapping("/mapping/transformations")
    public List<Map<String, Object>> transformations(@RequestParam TypeCategory source,
                                                     @RequestParam TypeCategory target) {
        return Arrays.stream(Transformation.values()).filter(t -> t.isApplicable(source, target))
                .map(t -> Map.<String, Object>of("name", t.name(), "label", t.label(),
                        "description", t.description(), "parameterRequired", t.parameterRequired(),
                        "parameterAllowed", t.parameterAllowed()))
                .toList();
    }

    @GetMapping("/mapping/{id}")
    public ProjectView mapping(@PathVariable String id) {
        return views.view(projects.require(id));
    }

    @PostMapping("/mapping/{id}/suggest")
    public ProjectView suggest(@PathVariable String id, @RequestBody(required = false) SuggestRequest body) {
        return views.view(workflow.suggest(id, body != null && body.overwrite()));
    }

    @PutMapping("/mapping/{id}")
    public ProjectView change(@PathVariable String id, @Valid @RequestBody MappingChangeRequest body) {
        return views.view(workflow.applyChanges(id, body));
    }

    @PostMapping("/mapping/{id}/validate")
    public Map<String, Object> validate(@PathVariable String id) {
        ValidationReport report = workflow.validate(id);
        return Map.of("report", report, "project", views.view(projects.require(id)));
    }

    @PostMapping("/mapping/{id}/approve")
    public ProjectView approve(@PathVariable String id, @RequestBody ApproveRequest body,
                               Authentication authentication) {
        return views.view(workflow.approve(id, body.expectedVersion(), body.confirmReviewed(),
                authentication == null ? "unknown" : authentication.getName()));
    }

    // ------------------------------------------------------------------ migration gate

    @PostMapping("/migration/{id}/preview")
    public MigrationPreview preview(@PathVariable String id) {
        return gate.preview(id);
    }

    @PostMapping("/migration/{id}/start")
    public MigrationRun start(@PathVariable String id, @RequestBody(required = false) StartMigrationRequest body) {
        StartMigrationRequest request = body == null ? new StartMigrationRequest(null, null, false) : body;
        return engine.start(id, request);
    }

    @PostMapping("/migration/{id}/cancel")
    public void cancel(@PathVariable String id) {
        engine.cancel(id);
    }

    @GetMapping("/migration/{id}/status")
    public MigrationRun status(@PathVariable String id) {
        return engine.status(id);
    }

    @PostMapping("/migration/{id}/validate")
    public ReconciliationReport reconcile(@PathVariable String id) {
        return reconciliation.validate(id);
    }
}
