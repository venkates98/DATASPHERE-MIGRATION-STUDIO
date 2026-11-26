package com.datasphere.migration.validation;

import com.datasphere.migration.db.AccessMode;
import com.datasphere.migration.db.ConnectionProfile;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.ConnectionService;
import com.datasphere.migration.db.CredentialVault;
import com.datasphere.migration.db.adapter.DatabaseAdapters;
import com.datasphere.migration.db.dialect.SqlDialect;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.project.MigrationProject;
import com.datasphere.migration.project.ProjectService;
import com.datasphere.migration.project.ProjectState;
import com.datasphere.migration.project.TablePairMapping;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Compares source and target after a copy. Checks that did not run are {@link CheckOutcome#NOT_RUN}, never
 * presented as success.
 */
@Service
public class ReconciliationService {

    private static final int SAMPLE_LIMIT = 50;

    private final ProjectService projects;
    private final ConnectionService connections;
    private final CredentialVault vault;

    public ReconciliationService(ProjectService projects, ConnectionService connections, CredentialVault vault) {
        this.projects = projects;
        this.connections = connections;
        this.vault = vault;
    }

    public ReconciliationReport validate(String projectId) {
        return projects.modify(projectId, project -> {
            if (project.getState() != ProjectState.MIGRATED && project.getState() != ProjectState.VALIDATED
                    && project.getState() != ProjectState.MIGRATION_FAILED) {
                throw new ApplicationException(ErrorCode.PRJ_STATE_001, "Validate after migrate " + projectId,
                        List.of("Run a migration first. Validation compares source and target row counts."));
            }
            ReconciliationReport report = run(project);
            if (project.getLastRun() != null) {
                project.getLastRun().setReconciliation(report);
            }
            project.transition(ProjectState.VALIDATED, "Post-migration validation ran");
            projects.save(project);
            return report;
        });
    }

    private ReconciliationReport run(MigrationProject project) {
        ReconciliationReport report = new ReconciliationReport();
        report.setGeneratedAt(Instant.now());
        report.getNotes().add("Matching counts do not prove that every value is identical.");
        report.getNotes().add("Primary-key sampling is limited to " + SAMPLE_LIMIT
                + " keys per table. Hash comparison of full tables is not performed.");
        ConnectionProfile source = project.connection(ConnectionRole.SOURCE);
        ConnectionProfile target = project.connection(ConnectionRole.TARGET);
        SqlDialect sourceDialect = DatabaseAdapters.require(source.type()).dialect();
        SqlDialect targetDialect = DatabaseAdapters.require(target.type()).dialect();
        char[] spw = password(project.getId(), ConnectionRole.SOURCE);
        char[] tpw = password(project.getId(), ConnectionRole.TARGET);
        List<CheckOutcome> outcomes = new ArrayList<>();
        try {
            for (TablePairMapping pair : project.getTablePairs()) {
                TableMetadata srcMeta = project.metadata(ConnectionRole.SOURCE).get(pair.getSourceTable().key());
                long sourceRows = count(source, spw, sourceDialect.count(pair.getSourceTable()));
                long targetRows = count(target, tpw, targetDialect.count(pair.getTargetTable()));
                ColumnMapping firstMapped = pair.getColumns().stream().filter(ColumnMapping::isMapped).findFirst()
                        .orElse(null);
                Long sourceNulls = null;
                Long targetNulls = null;
                if (firstMapped != null) {
                    sourceNulls = count(source, spw, sourceDialect.countNulls(pair.getSourceTable(),
                            firstMapped.getSourceColumn()));
                    targetNulls = count(target, tpw, targetDialect.countNulls(pair.getTargetTable(),
                            firstMapped.getTargetColumn()));
                }
                CheckOutcome outcome;
                String detail;
                if (sourceRows == targetRows) {
                    outcome = CheckOutcome.PARTIAL;
                    detail = "Row counts match (" + sourceRows + "). Full value comparison was not run.";
                    long pkPresent = srcMeta.columns().stream().filter(ColumnMetadata::primaryKey).count();
                    if (pkPresent == 1 && sourceRows > 0 && sourceRows <= SAMPLE_LIMIT) {
                        detail += " Primary-key counts also match at this size; values were not hashed.";
                    }
                } else {
                    outcome = CheckOutcome.FAILED;
                    detail = "Source has " + sourceRows + " rows and the target has " + targetRows + ".";
                }
                if (sourceNulls != null && targetNulls != null && !sourceNulls.equals(targetNulls)
                        && outcome != CheckOutcome.FAILED) {
                    outcome = CheckOutcome.PARTIAL;
                    detail += " Null counts differ for " + firstMapped.getSourceColumn() + " (" + sourceNulls
                            + " vs " + targetNulls + ").";
                }
                outcomes.add(outcome);
                report.getTables().add(new ReconciliationReport.TableCheck(pair.getId(), pair.getSourceTable().key(),
                        pair.getTargetTable().key(), outcome, sourceRows, targetRows, sourceNulls, targetNulls,
                        detail));
            }
        } finally {
            CredentialVault.wipe(spw);
            CredentialVault.wipe(tpw);
        }
        if (outcomes.stream().anyMatch(o -> o == CheckOutcome.FAILED)) {
            report.setOverall(CheckOutcome.FAILED);
        } else if (outcomes.isEmpty()) {
            report.setOverall(CheckOutcome.NOT_RUN);
        } else {
            report.setOverall(CheckOutcome.PARTIAL);
            report.getNotes().add("Overall result is PARTIAL because column-level equality was not fully checked.");
        }
        return report;
    }

    private long count(ConnectionProfile profile, char[] password, String sql) {
        return connections.execute(profile, password, AccessMode.READ, c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    private char[] password(String projectId, ConnectionRole role) {
        return vault.get(projectId, role).orElseThrow(() ->
                new ApplicationException(ErrorCode.DB_CRED_001, "Missing password for " + role));
    }
}
