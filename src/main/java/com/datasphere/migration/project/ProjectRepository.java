package com.datasphere.migration.project;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for migration projects. Implementations return independent copies, so a caller can change
 * a project freely and only the explicit {@link #save} makes the change durable.
 */
public interface ProjectRepository {

    void save(MigrationProject project);

    Optional<MigrationProject> findById(String id);

    /** Most recently updated first. */
    List<MigrationProject> findAll();

    void deleteById(String id);
}
