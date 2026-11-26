package com.datasphere.migration.project;

import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.engine.SchemaSqlService;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.validation.MappingValidator;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.MetadataService;
import com.datasphere.migration.metadata.TableMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The server-side gate in front of preview and execution. Whatever the browser shows or hides, a migration can
 * only proceed when the stored approval still describes exactly the current mapping and the databases still
 * have the structure that was approved.
 */
@Service
public class MigrationGateService {

    private static final Logger audit = LoggerFactory.getLogger("com.datasphere.migration.audit.mapping");

    private final ProjectService projects;
    private final DiscoveryService discovery;
    private final MappingValidator validator;
    private final MetadataService metadata;
    private final SchemaSqlService schemaSql;

    public MigrationGateService(ProjectService projects, DiscoveryService discovery, MappingValidator validator,
                                MetadataService metadata, SchemaSqlService schemaSql) {
        this.projects = projects;
        this.discovery = discovery;
        this.validator = validator;
        this.metadata = metadata;
        this.schemaSql = schemaSql;
    }

    /**
     * Runs every check that must hold before anything may be migrated. Throws a specific error for the first
     * check that fails. If the approval turns out to be outdated it is withdrawn and recorded.
     */
    public void verify(MigrationProject project) {
        ApprovalRecord approval = project.getApproval();
        if (approval == null || !project.getState().isApproved()) {
            throw new ApplicationException(ErrorCode.MAP_APPR_001, "Gate: no approval for " + project.getId(),
                    List.of("The mapping has not been approved. Review it, validate it and click "
                            + "\"Approve Mapping & Continue\"."));
        }
        if (approval.mappingVersion() != project.getMappingVersion()
                || !Objects.equals(approval.mappingHash(), project.mappingHash())) {
            withdraw(project, "Approved mapping no longer matches the current mapping");
            throw new ApplicationException(ErrorCode.MAP_APPR_002, "Gate: approval outdated for " + project.getId(),
                    List.of("The mapping changed after it was approved. Review and approve it again."));
        }
        ValidationReport report = validator.validate(project);
        project.setLastValidation(report);
        if (!report.valid()) {
            withdraw(project, report.errors() + " validation error(s) found at the pre-migration check");
            project.transition(ProjectState.MAPPING_VALIDATION_FAILED, "Validation failed at the migration gate");
            projects.save(project);
            throw new ApplicationException(ErrorCode.MAP_VAL_002, "Gate: validation failed for " + project.getId(),
                    MappingWorkflowService.firstErrors(report));
        }
        DiscoveryService.Drift drift = discovery.detectDrift(project);
        if (drift.changed() || !Objects.equals(drift.liveFingerprint(), approval.metadataFingerprint())) {
            withdraw(project, "Database structure changed since approval");
            projects.save(project);
            List<String> details = new ArrayList<>(drift.changedTables());
            details.add("The approval was withdrawn. Refresh the metadata, review the mapping and approve it again.");
            throw new ApplicationException(ErrorCode.MAP_META_001, "Gate: metadata drift for " + project.getId(),
                    details);
        }
    }

    /** Builds the read-only preview and marks the project ready for migration. Writes nothing to the databases. */
    public MigrationPreview preview(String projectId) {
        return projects.modify(projectId, project -> {
            verify(project);
            DatabaseType sourceDb = project.connection(ConnectionRole.SOURCE).type();
            DatabaseType targetDb = project.connection(ConnectionRole.TARGET).type();
            List<MigrationPreview.PairPreview> pairs = new ArrayList<>();
            for (TablePairMapping pair : project.getTablePairs()) {
                TableMetadata source = project.metadata(ConnectionRole.SOURCE).get(pair.getSourceTable().key());
                TableMetadata target = project.metadata(ConnectionRole.TARGET).get(pair.getTargetTable().key());
                long rows = metadata.rowCount(projectId, project.connection(ConnectionRole.SOURCE),
                        pair.getSourceTable());
                pairs.add(pairPreview(project, pair, source, target, rows));
            }
            project.setPreviewVerifiedAt(Instant.now());
            project.transition(ProjectState.READY_FOR_MIGRATION, "Pre-migration check passed");
            projects.save(project);
            audit.info("Migration preview built project={} version={} pairs={}", projectId,
                    project.getMappingVersion(), pairs.size());
            return new MigrationPreview(projectId, project.getMappingVersion(), project.getPreviewVerifiedAt(),
                    project.connection(ConnectionRole.SOURCE).display(),
                    project.connection(ConnectionRole.TARGET).display(), pairs, List.of(
                    "Nothing has been written to the target database.",
                    "The statements below are shown for review only. They are not run by this preview.",
                    "Source and target types differ between " + sourceDb.label() + " and " + targetDb.label()
                            + "; rows that cannot be converted are not silently changed."));
        });
    }

    // ------------------------------------------------------------------ helpers

    private void withdraw(MigrationProject project, String reason) {
        project.invalidateApproval();
        project.transition(ProjectState.MAPPING_MODIFIED, reason);
        projects.save(project);
        audit.warn("Approval withdrawn project={} reason={}", project.getId(), reason);
    }

    private MigrationPreview.PairPreview pairPreview(MigrationProject project, TablePairMapping pair,
                                                     TableMetadata source, TableMetadata target, long rows) {
        List<MigrationPreview.ColumnLine> lines = new ArrayList<>();
        List<String> unmappedSource = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> usedTargets = new ArrayList<>();
        for (ColumnMapping m : pair.getColumns()) {
            if (!m.isMapped()) {
                unmappedSource.add(m.getSourceColumn());
                continue;
            }
            ColumnMetadata s = source.column(m.getSourceColumn()).orElseThrow();
            ColumnMetadata t = target.column(m.getTargetColumn()).orElseThrow();
            usedTargets.add(t.name());
            lines.add(new MigrationPreview.ColumnLine(s.name(), s.displayType(), t.name(), t.displayType(),
                    m.getTransformation().label(), m.getScore()));
            warnings.addAll(m.getWarnings().stream().map(w -> s.name() + " \u2192 " + t.name() + ": " + w).toList());
        }
        List<String> unmappedTarget = target.columns().stream().map(ColumnMetadata::name)
                .filter(n -> !usedTargets.contains(n)).toList();
        String insert = schemaSql.insertSql(project, pair);
        String create = schemaSql.createTableSql(project, pair);
        return new MigrationPreview.PairPreview(pair.getId(), pair.getSourceTable().key(),
                pair.getTargetTable().key(), rows, lines, unmappedSource, unmappedTarget, warnings, insert, create);
    }
}
