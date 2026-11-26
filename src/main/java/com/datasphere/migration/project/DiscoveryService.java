package com.datasphere.migration.project;

import com.datasphere.migration.config.AppProperties;
import com.datasphere.migration.db.ConnectionProfile;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.mapping.engine.TablePairSuggester;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.model.MappingStatus;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.MetadataService;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.metadata.TableRef;
import com.datasphere.migration.metadata.TableSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Schema discovery, table selection, metadata refresh and structure-drift detection. */
@Service
public class DiscoveryService {

    private static final Logger audit = LoggerFactory.getLogger("com.datasphere.migration.audit.mapping");

    /** Result of comparing the stored snapshot with the databases as they are now. */
    public record Drift(boolean changed, List<String> changedTables, String liveFingerprint) {
    }

    private final ProjectService projects;
    private final MetadataService metadata;
    private final TablePairSuggester suggester;
    private final AppProperties properties;

    public DiscoveryService(ProjectService projects, MetadataService metadata, TablePairSuggester suggester,
                            AppProperties properties) {
        this.projects = projects;
        this.metadata = metadata;
        this.suggester = suggester;
        this.properties = properties;
    }

    public List<String> schemas(String projectId, ConnectionRole role) {
        MigrationProject project = projects.require(projectId);
        return metadata.schemas(projectId, requireConnection(project, role));
    }

    public List<TableSummary> tables(String projectId, ConnectionRole role, String schema) {
        MigrationProject project = projects.require(projectId);
        return metadata.tables(projectId, requireConnection(project, role), schema);
    }

    public TableMetadata table(String projectId, ConnectionRole role, TableRef ref) {
        MigrationProject project = projects.require(projectId);
        return metadata.readTable(projectId, requireConnection(project, role), ref);
    }

    public List<TablePairSuggester.Suggestion> suggestTablePairs(String projectId, String sourceSchema,
                                                                 String targetSchema) {
        MigrationProject project = projects.require(projectId);
        List<TableSummary> sources = metadata.tables(projectId, requireConnection(project, ConnectionRole.SOURCE),
                sourceSchema);
        List<TableSummary> targets = metadata.tables(projectId, requireConnection(project, ConnectionRole.TARGET),
                targetSchema);
        return suggester.suggest(sources, targets);
    }

    /**
     * Stores the chosen table pairs together with a snapshot of both tables' structure. Replaces any previous
     * selection, mapping and approval.
     */
    public MigrationProject selectTables(String projectId, TablePairRequest request) {
        List<TablePairRequest.Pair> pairs = request.pairs();
        if (pairs.isEmpty()) {
            throw invalid("Select at least one source table and one target table.");
        }
        int max = properties.limits().maxTablesPerJob();
        if (pairs.size() > max) {
            throw invalid("At most " + max + " table pairs can be migrated in one project.");
        }
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (TablePairRequest.Pair p : pairs) {
            if (!sources.add(p.source().key())) {
                throw invalid("The source table " + p.source().key() + " is selected more than once.");
            }
            if (!targets.add(p.target().key())) {
                throw invalid("The target table " + p.target().key() + " is selected more than once.");
            }
        }

        return projects.modify(projectId, project -> {
            ConnectionProfile sourceProfile = requireConnection(project, ConnectionRole.SOURCE);
            ConnectionProfile targetProfile = requireConnection(project, ConnectionRole.TARGET);
            Map<String, TableMetadata> sourceMeta = metadata.readTables(projectId, sourceProfile,
                    pairs.stream().map(TablePairRequest.Pair::source).toList());
            Map<String, TableMetadata> targetMeta = metadata.readTables(projectId, targetProfile,
                    pairs.stream().map(TablePairRequest.Pair::target).toList());
            for (TableMetadata t : targetMeta.values()) {
                if (t.isView()) {
                    throw invalid(t.ref().key() + " is a view. Data can only be loaded into a table.");
                }
            }
            project.resetSelection();
            for (TablePairRequest.Pair p : pairs) {
                project.getTablePairs().add(new TablePairMapping(shortId(), p.source(), p.target()));
            }
            project.metadata(ConnectionRole.SOURCE).putAll(sourceMeta);
            project.metadata(ConnectionRole.TARGET).putAll(targetMeta);
            project.bumpVersion();
            project.transition(ProjectState.DISCOVERED, pairs.size() + " table pair(s) selected");
            projects.save(project);
            audit.info("Tables selected project={} pairs={}", projectId, pairs.size());
            return project;
        });
    }

    /**
     * Compares the stored snapshot with the live databases. Tables that disappeared count as changed.
     * Read-only; stores nothing.
     */
    public Drift detectDrift(MigrationProject project) {
        Map<String, TableMetadata> liveSource = readLive(project, ConnectionRole.SOURCE);
        Map<String, TableMetadata> liveTarget = readLive(project, ConnectionRole.TARGET);
        TreeSet<String> changed = new TreeSet<>();
        compare(project.metadata(ConnectionRole.SOURCE), liveSource, "source", changed);
        compare(project.metadata(ConnectionRole.TARGET), liveTarget, "target", changed);
        return new Drift(!changed.isEmpty(), List.copyOf(changed),
                MigrationProject.fingerprintOf(liveSource, liveTarget));
    }

    /**
     * Adopts the databases' current structure as the new snapshot. Mappings are reconciled (new source columns
     * appear as unmapped; mappings to vanished columns are cleared) and any approval is withdrawn.
     */
    public RefreshResult refreshMetadata(String projectId) {
        return projects.modify(projectId, project -> {
            if (project.getTablePairs().isEmpty()) {
                throw new ApplicationException(ErrorCode.PRJ_STATE_001, "No tables selected for " + projectId,
                        List.of("Select tables before refreshing metadata."));
            }
            Map<String, TableMetadata> liveSource = readLive(project, ConnectionRole.SOURCE);
            Map<String, TableMetadata> liveTarget = readLive(project, ConnectionRole.TARGET);
            TreeSet<String> changed = new TreeSet<>();
            compare(project.metadata(ConnectionRole.SOURCE), liveSource, "source", changed);
            compare(project.metadata(ConnectionRole.TARGET), liveTarget, "target", changed);
            if (changed.isEmpty()) {
                return new RefreshResult(false, List.of());
            }
            for (TablePairMapping pair : project.getTablePairs()) {
                TableMetadata s = liveSource.get(pair.getSourceTable().key());
                TableMetadata t = liveTarget.get(pair.getTargetTable().key());
                if (s != null && t != null) {
                    reconcile(pair, s, t);
                }
            }
            liveSource.forEach((k, v) -> project.metadata(ConnectionRole.SOURCE).put(k, v));
            liveTarget.forEach((k, v) -> project.metadata(ConnectionRole.TARGET).put(k, v));
            project.invalidateApproval();
            project.setLastValidation(null);
            project.bumpVersion();
            if (project.hasMappings()) {
                project.transition(ProjectState.MAPPING_MODIFIED, "Metadata refreshed after a structure change");
            }
            projects.save(project);
            audit.info("Metadata refreshed project={} changedTables={}", projectId, changed.size());
            return new RefreshResult(true, List.copyOf(changed));
        });
    }

    public record RefreshResult(boolean changed, List<String> changedTables) {
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, TableMetadata> readLive(MigrationProject project, ConnectionRole role) {
        List<TableRef> refs = project.getTablePairs().stream()
                .map(p -> role == ConnectionRole.SOURCE ? p.getSourceTable() : p.getTargetTable()).toList();
        return new LinkedHashMap<>(metadata.readTablesIfPresent(project.getId(), requireConnection(project, role), refs));
    }

    private static void compare(Map<String, TableMetadata> stored, Map<String, TableMetadata> live, String side,
                                Set<String> changed) {
        for (Map.Entry<String, TableMetadata> e : stored.entrySet()) {
            TableMetadata now = live.get(e.getKey());
            if (now == null) {
                changed.add(side + " table " + e.getKey() + " no longer exists");
            } else if (!now.fingerprint().equals(e.getValue().fingerprint())) {
                changed.add(side + " table " + e.getKey() + " was altered");
            }
        }
    }

    private static void reconcile(TablePairMapping pair, TableMetadata source, TableMetadata target) {
        Set<String> sourceNames = new HashSet<>();
        source.columns().forEach(c -> sourceNames.add(c.name()));
        Set<String> targetNames = new HashSet<>();
        target.columns().forEach(c -> targetNames.add(c.name()));

        List<ColumnMapping> reconciled = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (ColumnMapping m : pair.getColumns()) {
            if (!sourceNames.contains(m.getSourceColumn())) {
                continue; // the source column no longer exists
            }
            present.add(m.getSourceColumn());
            if (m.isMapped() && !targetNames.contains(m.getTargetColumn())) {
                String lost = m.getTargetColumn();
                m.setTargetColumn(null);
                m.setStatus(MappingStatus.UNMAPPED);
                m.setReasons(List.of("The target column " + lost + " no longer exists, so the mapping was cleared."));
                m.setWarnings(List.of());
                m.setCompatibility(null);
            }
            reconciled.add(m);
        }
        for (ColumnMetadata c : source.columns()) {
            if (!present.contains(c.name())) {
                ColumnMapping fresh = new ColumnMapping(c.name());
                fresh.setReasons(List.of("This column appeared after the metadata refresh. Choose a target for it, "
                        + "or use Reset to automatic suggestions."));
                reconciled.add(fresh);
            }
        }
        pair.setColumns(reconciled);
    }

    private static ConnectionProfile requireConnection(MigrationProject project, ConnectionRole role) {
        ConnectionProfile profile = project.connection(role);
        if (profile == null) {
            throw new ApplicationException(ErrorCode.PRJ_STATE_001, "Missing " + role + " connection",
                    List.of("Save the " + role.name().toLowerCase() + " connection first."));
        }
        return profile;
    }

    private static ApplicationException invalid(String message) {
        return new ApplicationException(ErrorCode.CFG_VAL_001, message, List.of(message));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
