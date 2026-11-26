package com.datasphere.migration.project;

import com.datasphere.migration.config.StorageProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Stores each project as one JSON file ({@code <data-dir>/projects/<id>.json}) so mappings and approvals survive
 * a restart without needing an application database. Files are written atomically (temp file + move).
 *
 * <p>The files contain connection references (host, database, user name) and table structure, but never a
 * password. Protect the data directory with normal file permissions and backups.
 */
@Repository
public class FileProjectRepository implements ProjectRepository {

    private static final Logger log = LoggerFactory.getLogger(FileProjectRepository.class);
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final JsonMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
    private final Path directory;

    public FileProjectRepository(StorageProperties storage) {
        this.directory = Path.of(storage.dataDir()).toAbsolutePath().normalize().resolve("projects");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the project data directory " + directory, e);
        }
        load();
        log.info("Project store: {} ({} project(s) loaded)", directory, cache.size());
    }

    private void load() {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String name = p.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    mapper.readValue(json, MigrationProject.class); // fail fast on corrupt files
                    if (ID.matcher(id).matches()) {
                        cache.put(id, json);
                    }
                } catch (IOException | RuntimeException e) {
                    log.error("Skipping unreadable project file {}: {}", name, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the project data directory " + directory, e);
        }
    }

    @Override
    public synchronized void save(MigrationProject project) {
        requireValidId(project.getId());
        try {
            String json = mapper.writeValueAsString(project);
            Path target = directory.resolve(project.getId() + ".json");
            Path temp = directory.resolve(project.getId() + ".json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            restrict(temp);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            cache.put(project.getId(), json);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save project " + project.getId(), e);
        }
    }

    @Override
    public Optional<MigrationProject> findById(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return Optional.empty();
        }
        String json = cache.get(id);
        return json == null ? Optional.empty() : Optional.of(read(json));
    }

    @Override
    public List<MigrationProject> findAll() {
        List<MigrationProject> all = new ArrayList<>();
        cache.values().forEach(json -> all.add(read(json)));
        all.sort(Comparator.comparing(MigrationProject::getUpdatedAt).reversed());
        return all;
    }

    @Override
    public synchronized void deleteById(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return;
        }
        cache.remove(id);
        try {
            Files.deleteIfExists(directory.resolve(id + ".json"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete project " + id, e);
        }
    }

    private MigrationProject read(String json) {
        try {
            return mapper.readValue(json, MigrationProject.class);
        } catch (IOException e) {
            throw new IllegalStateException("Stored project is unreadable", e);
        }
    }

    private static void requireValidId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid project id");
        }
    }

    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // not a POSIX file system (Windows): rely on the directory's ACLs
        }
    }
}
