package com.datasphere.migration.project;

import com.datasphere.migration.db.ConnectionProfile;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.engine.MigrationRun;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.metadata.TableMetadata;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The aggregate that is persisted: connection references (never passwords), the selected table pairs, the
 * metadata snapshot the mapping was built on, the column mappings, the latest validation and the approval.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class MigrationProject {

    private static final int HISTORY_LIMIT = 100;

    private String id;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;
    private ProjectState state = ProjectState.CREATED;
    private int mappingVersion;
    private ConnectionProfile sourceConnection;
    private ConnectionProfile targetConnection;
    private List<TablePairMapping> tablePairs = new ArrayList<>();
    private Map<String, TableMetadata> sourceMetadata = new LinkedHashMap<>();
    private Map<String, TableMetadata> targetMetadata = new LinkedHashMap<>();
    private ApprovalRecord approval;
    private ValidationReport lastValidation;
    private Instant previewVerifiedAt;
    private MigrationRun lastRun;
    private List<StateTransition> history = new ArrayList<>();

    public MigrationProject() {
        // for Jackson
    }

    public MigrationProject(String id, String name) {
        this.id = id;
        this.name = name;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public ProjectState getState() {
        return state;
    }

    public int getMappingVersion() {
        return mappingVersion;
    }

    public ConnectionProfile connection(ConnectionRole role) {
        return role == ConnectionRole.SOURCE ? sourceConnection : targetConnection;
    }

    public void setConnection(ConnectionRole role, ConnectionProfile profile) {
        if (role == ConnectionRole.SOURCE) {
            sourceConnection = profile;
        } else {
            targetConnection = profile;
        }
    }

    public List<TablePairMapping> getTablePairs() {
        return tablePairs;
    }

    public Map<String, TableMetadata> metadata(ConnectionRole role) {
        return role == ConnectionRole.SOURCE ? sourceMetadata : targetMetadata;
    }

    public ApprovalRecord getApproval() {
        return approval;
    }

    public void setApproval(ApprovalRecord approval) {
        this.approval = approval;
    }

    public ValidationReport getLastValidation() {
        return lastValidation;
    }

    public void setLastValidation(ValidationReport lastValidation) {
        this.lastValidation = lastValidation;
    }

    public Instant getPreviewVerifiedAt() {
        return previewVerifiedAt;
    }

    public void setPreviewVerifiedAt(Instant previewVerifiedAt) {
        this.previewVerifiedAt = previewVerifiedAt;
    }

    public MigrationRun getLastRun() {
        return lastRun;
    }

    public void setLastRun(MigrationRun lastRun) {
        this.lastRun = lastRun;
    }

    public List<StateTransition> getHistory() {
        return history;
    }

    public Optional<TablePairMapping> pair(String pairId) {
        return tablePairs.stream().filter(p -> p.getId().equals(pairId)).findFirst();
    }

    public boolean hasMappings() {
        return tablePairs.stream().anyMatch(p -> !p.getColumns().isEmpty());
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    /** Records a state change (and the reason) in the history. */
    public void transition(ProjectState to, String reason) {
        if (to != state) {
            history.add(new StateTransition(Instant.now(), state, to, reason));
            if (history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
            state = to;
        }
        touch();
    }

    /** Every content change increments the version so an earlier approval can never apply to it. */
    public void bumpVersion() {
        mappingVersion++;
        touch();
    }

    /** Removes the approval and the evidence derived from it. */
    public void invalidateApproval() {
        approval = null;
        previewVerifiedAt = null;
    }

    /** Replaces tables, snapshots and mappings (used when the table selection changes). */
    public void resetSelection() {
        tablePairs = new ArrayList<>();
        sourceMetadata = new LinkedHashMap<>();
        targetMetadata = new LinkedHashMap<>();
        lastValidation = null;
        invalidateApproval();
    }

    /** Fingerprint of the structure of every selected table on both sides. */
    public String metadataFingerprint() {
        return fingerprintOf(sourceMetadata, targetMetadata);
    }

    public static String fingerprintOf(Map<String, TableMetadata> source, Map<String, TableMetadata> target) {
        Map<String, String> parts = new TreeMap<>();
        source.forEach((k, v) -> parts.put("S:" + k, v.fingerprint()));
        target.forEach((k, v) -> parts.put("T:" + k, v.fingerprint()));
        StringBuilder sb = new StringBuilder();
        parts.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return TableMetadata.sha256(sb.toString());
    }

    /** Hash of everything that decides what a migration would do: table pairs, column pairs and conversions. */
    public String mappingHash() {
        StringBuilder sb = new StringBuilder();
        for (TablePairMapping pair : tablePairs) {
            sb.append(pair.getSourceTable().key()).append("->").append(pair.getTargetTable().key()).append('\n');
            for (ColumnMapping c : pair.getColumns()) {
                if (c.isMapped()) {
                    sb.append(c.getSourceColumn()).append('>').append(c.getTargetColumn()).append('|')
                            .append(c.getTransformation().name()).append('|')
                            .append(c.getTransformationParameter() == null ? "" : c.getTransformationParameter())
                            .append('\n');
                }
            }
        }
        return TableMetadata.sha256(sb.toString());
    }
}
