package com.datasphere.migration.project;

import com.datasphere.migration.db.ConnectionProfile;
import com.datasphere.migration.db.ConnectionRequest;
import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.ConnectionService;
import com.datasphere.migration.db.ConnectionTestResult;
import com.datasphere.migration.db.CredentialVault;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import com.datasphere.migration.repository.MetricsRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Project lifecycle and connection management. */
@Service
public class ProjectService {

    private static final Logger audit = LoggerFactory.getLogger("com.datasphere.migration.audit.mapping");

    private final ProjectRepository repository;
    private final ProjectLocks locks;
    private final ConnectionService connections;
    private final CredentialVault vault;
    private final MetricsRepository metrics;

    public ProjectService(ProjectRepository repository, ProjectLocks locks, ConnectionService connections,
                          CredentialVault vault, MetricsRepository metrics) {
        this.repository = repository;
        this.locks = locks;
        this.connections = connections;
        this.vault = vault;
        this.metrics = metrics;
        refreshProfileCount();
    }

    public MigrationProject create(String name) {
        MigrationProject project = new MigrationProject(UUID.randomUUID().toString(), name.trim());
        project.transition(ProjectState.CREATED, "Project created");
        repository.save(project);
        audit.info("Project created id={} name={}", project.getId(), project.getName());
        return project;
    }

    public List<MigrationProject> list() {
        return repository.findAll();
    }

    public MigrationProject require(String projectId) {
        return repository.findById(projectId).orElseThrow(() ->
                new ApplicationException(ErrorCode.PRJ_NOTFOUND_001, "Unknown project " + projectId));
    }

    public void save(MigrationProject project) {
        project.touch();
        repository.save(project);
    }

    /** Runs a read-modify-write on a project while holding that project's lock. */
    public <T> T modify(String projectId, Function<MigrationProject, T> work) {
        return locks.withLock(projectId, () -> work.apply(require(projectId)));
    }

    public void delete(String projectId) {
        locks.withLock(projectId, () -> {
            MigrationProject project = require(projectId);
            vault.removeProject(projectId);
            repository.deleteById(projectId);
            audit.info("Project deleted id={} name={}", projectId, project.getName());
            return null;
        });
        refreshProfileCount();
    }

    /** Tests a connection without saving anything. The password is used once and wiped. */
    public ConnectionTestResult test(ConnectionRole role, ConnectionRequest request) {
        try {
            return connections.test(request.toProfile(role), request.password());
        } finally {
            CredentialVault.wipe(request.password());
        }
    }

    /**
     * Tests and saves a connection. The profile (no password) is persisted; the password stays in memory.
     * Pointing a role at a different database discards the table selection and mappings built on the old one.
     */
    public MigrationProject saveConnection(String projectId, ConnectionRole role, ConnectionRequest request) {
        ConnectionProfile profile = request.toProfile(role);
        try {
            connections.test(profile, request.password());
            MigrationProject saved = modify(projectId, project -> {
                ConnectionProfile previous = project.connection(role);
                if (previous != null && !previous.identity().equals(profile.identity())
                        && (!project.getTablePairs().isEmpty() || project.getApproval() != null)) {
                    project.resetSelection();
                    project.bumpVersion();
                    project.transition(ProjectState.CREATED, role + " connection changed; selection cleared");
                }
                project.setConnection(role, profile);
                vault.put(projectId, role, request.password());
                save(project);
                audit.info("Connection saved project={} role={} target={}", projectId, role, profile.display());
                return project;
            });
            refreshProfileCount();
            return saved;
        } finally {
            CredentialVault.wipe(request.password());
        }
    }

    private void refreshProfileCount() {
        int count = 0;
        for (MigrationProject p : repository.findAll()) {
            count += (p.connection(ConnectionRole.SOURCE) != null ? 1 : 0)
                    + (p.connection(ConnectionRole.TARGET) != null ? 1 : 0);
        }
        metrics.setConnectionProfiles(count);
    }
}
