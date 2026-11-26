package com.datasphere.migration.engine;

import com.datasphere.migration.config.AppProperties;
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
import com.datasphere.migration.mapping.type.TypeNormalizer;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.project.MigrationGateService;
import com.datasphere.migration.project.MigrationProject;
import com.datasphere.migration.project.ProjectService;
import com.datasphere.migration.project.ProjectState;
import com.datasphere.migration.project.TablePairMapping;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Copies rows from a source adapter to a target adapter in batches. It never DROPs, TRUNCATEs or UPDATEs.
 * Source connections are read-only. Target connections are used only for INSERT.
 */
@Service
public class MigrationEngine {

    private static final Logger audit = LoggerFactory.getLogger("com.datasphere.migration.audit.mapping");
    private static final int MAX_RETRIES = 3;
    private static final int FAILURE_CAP = 50;

    private final ProjectService projects;
    private final MigrationGateService gate;
    private final ConnectionService connections;
    private final CredentialVault vault;
    private final AppProperties properties;
    private final Semaphore slots;
    private final ConcurrentHashMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public MigrationEngine(ProjectService projects, MigrationGateService gate, ConnectionService connections,
                           CredentialVault vault, AppProperties properties) {
        this.projects = projects;
        this.gate = gate;
        this.connections = connections;
        this.vault = vault;
        this.properties = properties;
        this.slots = new Semaphore(properties.limits().maxConcurrentJobs());
    }

    public MigrationRun start(String projectId, StartMigrationRequest request) {
        if (request == null || !request.confirmWrite()) {
            throw new ApplicationException(ErrorCode.CFG_VAL_001, "Migration start without confirmWrite",
                    List.of("Tick the confirmation. The application will INSERT into the target. "
                            + "It will not drop, truncate or overwrite existing rows."));
        }
        int batch = request.batchSize() == null ? properties.limits().defaultBatchSize()
                : Math.min(request.batchSize(), properties.limits().maxBatchSize());
        InsertMode mode = request.insertMode() == null ? InsertMode.FAIL_ON_DUPLICATE : request.insertMode();

        projects.modify(projectId, project -> {
            gate.verify(project);
            if (project.getState() != ProjectState.READY_FOR_MIGRATION && project.getState() != ProjectState.MIGRATED
                    && project.getState() != ProjectState.MIGRATION_FAILED) {
                throw new ApplicationException(ErrorCode.PRJ_STATE_001, "Cannot start in " + project.getState(),
                        List.of("Run the migration preview first."));
            }
            if (project.getLastRun() != null && project.getLastRun().running()) {
                throw new ApplicationException(ErrorCode.PRJ_STATE_001, "Already running " + projectId,
                        List.of("A migration is already running for this project."));
            }
            MigrationRun run = new MigrationRun();
            run.setBatchSize(batch);
            run.setInsertMode(mode);
            run.setMappingVersion(project.getMappingVersion());
            for (TablePairMapping pair : orderByDependency(project)) {
                run.getTables().add(new TableProgress(pair.getId(), pair.getSourceTable().key(),
                        pair.getTargetTable().key()));
            }
            project.setLastRun(run);
            project.transition(ProjectState.MIGRATING, "Migration started");
            projects.save(project);
            return project;
        });

        if (!slots.tryAcquire()) {
            failRun(projectId, "Too many migrations are running. Wait and retry.");
            throw new ApplicationException(ErrorCode.RES_LIMIT_001, "Concurrent job limit",
                    List.of("The configured maximum number of concurrent migrations is already running."));
        }
        cancelFlags.put(projectId, new AtomicBoolean(false));
        try {
            return execute(projectId);
        } finally {
            slots.release();
            cancelFlags.remove(projectId);
        }
    }

    public void cancel(String projectId) {
        AtomicBoolean flag = cancelFlags.get(projectId);
        if (flag == null) {
            throw new ApplicationException(ErrorCode.PRJ_STATE_001, "No running migration " + projectId,
                    List.of("There is no running migration to cancel."));
        }
        flag.set(true);
    }

    public MigrationRun status(String projectId) {
        MigrationProject p = projects.require(projectId);
        if (p.getLastRun() == null) {
            throw new ApplicationException(ErrorCode.CFG_NOTFOUND_001, "No run for " + projectId,
                    List.of("No migration has been started for this project."));
        }
        return p.getLastRun();
    }

    private MigrationRun execute(String projectId) {
        MigrationProject snapshot = projects.require(projectId);
        ConnectionProfile source = snapshot.connection(ConnectionRole.SOURCE);
        ConnectionProfile target = snapshot.connection(ConnectionRole.TARGET);
        char[] sourcePw = password(projectId, ConnectionRole.SOURCE);
        char[] targetPw = password(projectId, ConnectionRole.TARGET);
        try {
            connections.execute(source, sourcePw, AccessMode.READ, sourceConn -> {
                connections.execute(target, targetPw, AccessMode.WRITE, targetConn -> {
                    copyAll(projectId, snapshot, sourceConn, targetConn);
                    return null;
                });
                return null;
            });
        } catch (ApplicationException e) {
            if (e.getErrorCode() == ErrorCode.MIG_CANCEL_001) {
                finish(projectId, MigrationRunStatus.CANCELLED, "Cancelled by the user.");
            } else {
                finish(projectId, MigrationRunStatus.FAILED, e.getErrorCode().userMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            finish(projectId, MigrationRunStatus.FAILED, "The migration stopped because of a database error.");
            throw new ApplicationException(ErrorCode.MIG_EXEC_001, "Migration failed: " + e.getMessage(), e,
                    List.of("The migration stopped. Batches that had already been committed were not rolled back."));
        } finally {
            CredentialVault.wipe(sourcePw);
            CredentialVault.wipe(targetPw);
        }
        return projects.require(projectId).getLastRun();
    }

    private void copyAll(String projectId, MigrationProject snapshot, Connection sourceConn, Connection targetConn)
            throws SQLException {
        SqlDialect sourceDialect = DatabaseAdapters.require(snapshot.connection(ConnectionRole.SOURCE).type()).dialect();
        SqlDialect targetDialect = DatabaseAdapters.require(snapshot.connection(ConnectionRole.TARGET).type()).dialect();
        targetConn.setAutoCommit(false);
        for (TablePairMapping pair : orderByDependency(snapshot)) {
            throwIfCancelled(projectId);
            TableProgress progress = snapshot.getLastRun().getTables().stream()
                    .filter(t -> pair.getId().equals(t.getPairId())).findFirst().orElseThrow();
            progress.setStatus("RUNNING");
            persistProgress(projectId, progress);
            try {
                copyTable(projectId, snapshot, pair, progress, sourceConn, targetConn, sourceDialect, targetDialect);
                progress.setStatus("COMPLETED");
            } catch (ApplicationException e) {
                if (e.getErrorCode() == ErrorCode.MIG_CANCEL_001) {
                    progress.setStatus("CANCELLED");
                    persistProgress(projectId, progress);
                    throw e;
                }
                progress.setStatus("FAILED");
                progress.setError(e.getErrorCode().userMessage());
                persistProgress(projectId, progress);
                throw e;
            }
            persistProgress(projectId, progress);
        }
        boolean anyFailed = snapshot.getLastRun().getTables().stream()
                .anyMatch(t -> "FAILED".equals(t.getStatus()) || t.getRowsFailed() > 0);
        finish(projectId, anyFailed ? MigrationRunStatus.COMPLETED_WITH_ERRORS : MigrationRunStatus.COMPLETED, null);
    }

    private void copyTable(String projectId, MigrationProject project, TablePairMapping pair, TableProgress progress,
                           Connection sourceConn, Connection targetConn, SqlDialect sourceDialect,
                           SqlDialect targetDialect) throws SQLException {
        TableMetadata sourceTable = project.metadata(ConnectionRole.SOURCE).get(pair.getSourceTable().key());
        TableMetadata targetTable = project.metadata(ConnectionRole.TARGET).get(pair.getTargetTable().key());
        List<ColumnMapping> mapped = pair.getColumns().stream().filter(ColumnMapping::isMapped).toList();
        List<String> sourceCols = mapped.stream().map(ColumnMapping::getSourceColumn).toList();
        List<String> targetCols = mapped.stream().map(ColumnMapping::getTargetColumn).toList();
        List<String> pk = sourceTable.columns().stream().filter(ColumnMetadata::primaryKey).map(ColumnMetadata::name)
                .toList();
        String select = sourceDialect.selectColumns(pair.getSourceTable(), sourceCols,
                pk.isEmpty() ? List.of(sourceCols.get(0)) : pk);
        int batch = project.getLastRun().getBatchSize();
        InsertMode mode = project.getLastRun().getInsertMode();
        String insert = targetDialect.insert(pair.getTargetTable(), targetCols);

        try (PreparedStatement read = sourceConn.prepareStatement(select, ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)) {
            read.setFetchSize(batch);
            try (ResultSet rs = read.executeQuery();
                 PreparedStatement write = targetConn.prepareStatement(insert)) {
                int inBatch = 0;
                while (rs.next()) {
                    throwIfCancelled(projectId);
                    progress.addRead(1);
                    Object[] values = new Object[mapped.size()];
                    try {
                        for (int i = 0; i < mapped.size(); i++) {
                            ColumnMapping m = mapped.get(i);
                            Object raw = rs.getObject(i + 1);
                            ColumnMetadata tgt = targetTable.column(m.getTargetColumn()).orElseThrow();
                            values[i] = ValueConverter.apply(raw, m.getTransformation(), m.getTransformationParameter(),
                                    TypeNormalizer.normalize(tgt, project.connection(ConnectionRole.TARGET).type())
                                            .category());
                            write.setObject(i + 1, values[i]);
                        }
                    } catch (ApplicationException e) {
                        progress.recordFailure(progress.getRowsRead(), key(rs, sourceCols, pk),
                                e.getErrorCode().userMessage());
                        if (progress.getFailures().size() >= FAILURE_CAP
                                && mode == InsertMode.FAIL_ON_DUPLICATE) {
                            throw e;
                        }
                        continue;
                    }
                    write.addBatch();
                    inBatch++;
                    if (!pk.isEmpty()) {
                        progress.setLastKey(String.valueOf(rs.getObject(sourceCols.indexOf(pk.get(0)) + 1)));
                    }
                    if (inBatch >= batch) {
                        flushBatch(write, targetConn, targetDialect, progress, mode, inBatch);
                        inBatch = 0;
                        persistProgress(projectId, progress);
                    }
                }
                if (inBatch > 0) {
                    flushBatch(write, targetConn, targetDialect, progress, mode, inBatch);
                    persistProgress(projectId, progress);
                }
            }
        }
    }

    private void flushBatch(PreparedStatement write, Connection targetConn, SqlDialect dialect, TableProgress progress,
                            InsertMode mode, int size) throws SQLException {
        SQLException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                int[] counts = write.executeBatch();
                targetConn.commit();
                int inserted = 0;
                int skipped = 0;
                for (int c : counts) {
                    if (c == java.sql.Statement.SUCCESS_NO_INFO || c > 0) {
                        inserted++;
                    } else if (c == java.sql.Statement.EXECUTE_FAILED) {
                        skipped++;
                    } else {
                        inserted++;
                    }
                }
                progress.addInserted(inserted);
                progress.addSkipped(skipped);
                write.clearBatch();
                return;
            } catch (SQLException e) {
                try {
                    targetConn.rollback();
                } catch (SQLException ignored) {
                    // keep the original error
                }
                write.clearBatch();
                if (isDuplicate(e) && mode == InsertMode.SKIP_DUPLICATE) {
                    progress.addSkipped(size);
                    return;
                }
                if (isDuplicate(e)) {
                    throw new ApplicationException(ErrorCode.DATA_DUP_001, e.getMessage(), e,
                            List.of("A row already exists in the target. Choose 'skip duplicates' or clear the "
                                    + "conflicting target rows. Existing target rows were not overwritten."));
                }
                last = e;
                if (attempt == MAX_RETRIES || !dialect.isTransient(e.getSQLState(), e.getErrorCode())) {
                    throw new ApplicationException(ErrorCode.MIG_TXN_001, e.getMessage(), e,
                            List.of("A batch could not be inserted. Earlier committed batches were left as they are."));
                }
            }
        }
        throw new ApplicationException(ErrorCode.MIG_TXN_001, last == null ? "batch failed" : last.getMessage(), last,
                List.of("A batch could not be inserted after retries."));
    }

    private static boolean isDuplicate(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23")
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique"));
    }

    private static String key(ResultSet rs, List<String> sourceCols, List<String> pk) {
        try {
            if (pk.isEmpty()) {
                return null;
            }
            int idx = sourceCols.indexOf(pk.get(0));
            Object v = idx >= 0 ? rs.getObject(idx + 1) : null;
            return v == null ? null : String.valueOf(v);
        } catch (SQLException e) {
            return null;
        }
    }

    static List<TablePairMapping> orderByDependency(MigrationProject project) {
        List<TablePairMapping> pairs = new ArrayList<>(project.getTablePairs());
        Map<String, TablePairMapping> byTarget = new LinkedHashMap<>();
        for (TablePairMapping p : pairs) {
            byTarget.put(p.getTargetTable().key(), p);
        }
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (TablePairMapping p : pairs) {
            TableMetadata target = project.metadata(ConnectionRole.TARGET).get(p.getTargetTable().key());
            java.util.HashSet<String> need = new java.util.HashSet<>();
            if (target != null) {
                for (ColumnMetadata c : target.columns()) {
                    if (c.foreignKey() && c.referencedTable() != null && byTarget.containsKey(c.referencedTable())
                            && !c.referencedTable().equals(p.getTargetTable().key())) {
                        need.add(c.referencedTable());
                    }
                }
            }
            deps.put(p.getTargetTable().key(), need);
        }
        List<TablePairMapping> ordered = new ArrayList<>();
        java.util.HashSet<String> remaining = new java.util.HashSet<>(byTarget.keySet());
        while (!remaining.isEmpty()) {
            String next = remaining.stream()
                    .filter(k -> deps.get(k).stream().noneMatch(remaining::contains))
                    .findFirst().orElse(null);
            if (next == null) {
                remaining.forEach(k -> ordered.add(byTarget.get(k)));
                break;
            }
            remaining.remove(next);
            ordered.add(byTarget.get(next));
        }
        return ordered;
    }

    private void persistProgress(String projectId, TableProgress progress) {
        projects.modify(projectId, project -> {
            List<TableProgress> tables = project.getLastRun().getTables();
            for (int i = 0; i < tables.size(); i++) {
                if (progress.getPairId().equals(tables.get(i).getPairId())) {
                    tables.set(i, progress);
                }
            }
            projects.save(project);
            return project;
        });
    }

    private void finish(String projectId, MigrationRunStatus status, String error) {
        projects.modify(projectId, project -> {
            MigrationRun run = project.getLastRun();
            if (run != null) {
                run.setStatus(status);
                run.setFinishedAt(Instant.now());
                run.setError(error);
            }
            if (status == MigrationRunStatus.COMPLETED || status == MigrationRunStatus.COMPLETED_WITH_ERRORS) {
                project.transition(ProjectState.MIGRATED, "Migration " + status.name().toLowerCase());
            } else if (status == MigrationRunStatus.CANCELLED) {
                project.transition(ProjectState.MIGRATION_FAILED, "Migration cancelled");
            } else {
                project.transition(ProjectState.MIGRATION_FAILED, "Migration failed");
            }
            projects.save(project);
            audit.info("Migration finished project={} status={} inserted={} failed={}", projectId, status,
                    run == null ? 0 : run.getRowsInserted(), run == null ? 0 : run.getRowsFailed());
            return project;
        });
    }

    private void failRun(String projectId, String message) {
        finish(projectId, MigrationRunStatus.FAILED, message);
    }

    private void throwIfCancelled(String projectId) {
        AtomicBoolean flag = cancelFlags.get(projectId);
        if (flag != null && flag.get()) {
            throw new ApplicationException(ErrorCode.MIG_CANCEL_001, "Cancelled " + projectId,
                    List.of("The migration was cancelled. Committed batches were not rolled back."));
        }
    }

    private char[] password(String projectId, ConnectionRole role) {
        return vault.get(projectId, role).orElseThrow(() ->
                new ApplicationException(ErrorCode.DB_CRED_001, "Missing password for " + role));
    }
}
