package com.datasphere.migration.project;

import com.datasphere.migration.db.ConnectionProfile;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.CredentialVault;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.mapping.type.TypeNormalizer;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.project.ProjectView.Capabilities;
import com.datasphere.migration.project.ProjectView.ColumnInfo;
import com.datasphere.migration.project.ProjectView.ConnectionView;
import com.datasphere.migration.project.ProjectView.TablePairView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the browser-facing {@link ProjectView} from the persisted aggregate. */
@Component
public class ProjectViewFactory {

    private final CredentialVault vault;

    public ProjectViewFactory(CredentialVault vault) {
        this.vault = vault;
    }

    public ProjectView view(MigrationProject p) {
        ConnectionProfile sourceProfile = p.connection(ConnectionRole.SOURCE);
        ConnectionProfile targetProfile = p.connection(ConnectionRole.TARGET);
        List<TablePairView> pairs = new ArrayList<>();
        for (TablePairMapping pair : p.getTablePairs()) {
            TableMetadata source = p.metadata(ConnectionRole.SOURCE).get(pair.getSourceTable().key());
            TableMetadata target = p.metadata(ConnectionRole.TARGET).get(pair.getTargetTable().key());
            pairs.add(new TablePairView(pair.getId(), pair.getSourceTable().schema(), pair.getSourceTable().name(),
                    pair.getTargetTable().schema(), pair.getTargetTable().name(),
                    columns(source, sourceProfile == null ? null : sourceProfile.type()),
                    columns(target, targetProfile == null ? null : targetProfile.type()),
                    pair.getColumns()));
        }
        boolean validationCurrent = p.getLastValidation() != null
                && p.getLastValidation().mappingVersion() == p.getMappingVersion();
        return new ProjectView(p.getId(), p.getName(), p.getState().name(), p.getState().label(),
                p.getState().description(), p.getMappingVersion(), p.getCreatedAt(), p.getUpdatedAt(),
                connection(p, ConnectionRole.SOURCE), connection(p, ConnectionRole.TARGET), pairs, p.getApproval(),
                p.getLastValidation(), validationCurrent, p.getPreviewVerifiedAt(), p.getLastRun(),
                capabilities(p, validationCurrent), List.copyOf(p.getHistory()));
    }

    public ProjectView.Summary summary(MigrationProject p) {
        return new ProjectView.Summary(p.getId(), p.getName(), p.getState().name(), p.getState().label(),
                p.getMappingVersion(), p.getCreatedAt(), p.getUpdatedAt(), describe(p.connection(ConnectionRole.SOURCE)),
                describe(p.connection(ConnectionRole.TARGET)), p.getTablePairs().size(), p.getState().isApproved());
    }

    private static String describe(ConnectionProfile profile) {
        return profile == null ? null : profile.type().label() + " \u00b7 " + profile.database();
    }

    private ConnectionView connection(MigrationProject p, ConnectionRole role) {
        ConnectionProfile c = p.connection(role);
        if (c == null) {
            return null;
        }
        return new ConnectionView(role.name(), c.type().name(), c.type().label(), c.host(), c.port(), c.database(),
                c.schema(), c.username(), c.trustServerCertificate(), vault.has(p.getId(), role));
    }

    private static List<ColumnInfo> columns(TableMetadata table, DatabaseType db) {
        if (table == null || db == null) {
            return List.of();
        }
        List<ColumnInfo> list = new ArrayList<>();
        for (ColumnMetadata c : table.columns()) {
            list.add(new ColumnInfo(c.name(), c.displayType(), TypeNormalizer.normalize(c, db).category().name(),
                    c.ordinal(), c.nullable(), c.primaryKey(), c.unique(), c.foreignKey(), c.required(),
                    c.autoIncrement()));
        }
        return list;
    }

    private Capabilities capabilities(MigrationProject p, boolean validationCurrent) {
        boolean connected = p.connection(ConnectionRole.SOURCE) != null && p.connection(ConnectionRole.TARGET) != null;
        boolean hasPairs = !p.getTablePairs().isEmpty();
        boolean hasMappings = p.hasMappings();
        boolean approved = p.getState().isApproved() && p.getApproval() != null;
        String blocked = null;
        if (p.getState() != ProjectState.READY_FOR_MIGRATION) {
            blocked = approved ? "Run the migration preview first; it re-checks the approval against the databases."
                    : "The mapping must be validated and approved first.";
        }
        boolean canStart = (p.getState() == ProjectState.READY_FOR_MIGRATION
                || p.getState() == ProjectState.MIGRATION_FAILED) && p.getApproval() != null;
        boolean canCancel = p.getState() == ProjectState.MIGRATING;
        boolean canReconcile = p.getState() == ProjectState.MIGRATED || p.getState() == ProjectState.VALIDATED
                || p.getState() == ProjectState.MIGRATION_FAILED;
        if (p.getState() == ProjectState.MIGRATING) {
            blocked = "A migration is running.";
        }
        return new Capabilities(connected, connected && hasPairs,
                hasMappings && p.getState() != ProjectState.MIGRATING, hasMappings, hasMappings
                && !approved && validationCurrent && p.getLastValidation().valid(), approved, canStart, canCancel,
                canReconcile, canStart ? null : blocked);
    }
}
