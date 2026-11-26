package com.datasphere.migration.mapping.validation;

import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.model.MappingStatus;
import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.Transformation;
import com.datasphere.migration.mapping.type.TypeCompatibility;
import com.datasphere.migration.mapping.type.TypeCompatibilityChecker;
import com.datasphere.migration.mapping.type.TypeNormalizer;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.project.MigrationProject;
import com.datasphere.migration.project.TablePairMapping;
import com.datasphere.migration.project.ValidationIssue;
import com.datasphere.migration.project.ValidationIssue.Severity;
import com.datasphere.migration.project.ValidationReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Checks a mapping against the metadata snapshot stored with the project. It is a pure function of the
 * project: no database access, so it gives the same answer online or offline. Messages are plain language.
 */
@Component
public class MappingValidator {

    private static final int LIST_LIMIT = 8;

    public ValidationReport validate(MigrationProject project) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (project.connection(ConnectionRole.SOURCE) == null || project.connection(ConnectionRole.TARGET) == null) {
            issues.add(error("CONNECTION_MISSING", null, null, null,
                    "Both the source and the target connection must be saved first."));
            return ValidationReport.of(issues, project.getMappingVersion());
        }
        if (project.getTablePairs().isEmpty()) {
            issues.add(error("NO_TABLES", null, null, null, "No tables are selected for migration."));
            return ValidationReport.of(issues, project.getMappingVersion());
        }
        DatabaseType sourceDb = project.connection(ConnectionRole.SOURCE).type();
        DatabaseType targetDb = project.connection(ConnectionRole.TARGET).type();

        checkRepeatedTables(project, issues);
        for (TablePairMapping pair : project.getTablePairs()) {
            TableMetadata source = project.metadata(ConnectionRole.SOURCE).get(pair.getSourceTable().key());
            TableMetadata target = project.metadata(ConnectionRole.TARGET).get(pair.getTargetTable().key());
            String label = pair.getSourceTable().key() + " \u2192 " + pair.getTargetTable().key();
            if (source == null) {
                issues.add(error("SOURCE_TABLE_MISSING", pair.getId(), null, null,
                        "The source table " + pair.getSourceTable().key() + " is not in the discovered metadata."));
            }
            if (target == null) {
                issues.add(error("TARGET_TABLE_MISSING", pair.getId(), null, null,
                        "The target table " + pair.getTargetTable().key() + " is not in the discovered metadata."));
            }
            if (source == null || target == null) {
                continue;
            }
            if (target.isView()) {
                issues.add(error("TARGET_NOT_A_TABLE", pair.getId(), null, null,
                        pair.getTargetTable().key() + " is a view; data can only be loaded into a table."));
            }
            validatePair(pair, label, source, target, sourceDb, targetDb, issues);
        }
        return ValidationReport.of(issues, project.getMappingVersion());
    }

    private void checkRepeatedTables(MigrationProject project, List<ValidationIssue> issues) {
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (TablePairMapping pair : project.getTablePairs()) {
            if (!sources.add(pair.getSourceTable().key())) {
                issues.add(error("DUPLICATE_SOURCE_TABLE", pair.getId(), null, null,
                        "The source table " + pair.getSourceTable().key() + " is used in more than one table pair."));
            }
            if (!targets.add(pair.getTargetTable().key())) {
                issues.add(error("DUPLICATE_TARGET_TABLE", pair.getId(), null, null,
                        "The target table " + pair.getTargetTable().key() + " is used in more than one table pair."));
            }
        }
    }

    private void validatePair(TablePairMapping pair, String label, TableMetadata source, TableMetadata target,
                              DatabaseType sourceDb, DatabaseType targetDb, List<ValidationIssue> issues) {
        String id = pair.getId();
        Map<String, List<String>> sourcesByTarget = new LinkedHashMap<>();
        Set<String> seenSources = new HashSet<>();
        List<String> unmappedSources = new ArrayList<>();
        int mapped = 0;

        for (ColumnMapping m : pair.getColumns()) {
            String src = m.getSourceColumn();
            if (!seenSources.add(src)) {
                issues.add(error("DUPLICATE_SOURCE", id, src, null,
                        "The source column " + src + " appears more than once in " + label + "."));
            }
            Optional<ColumnMetadata> sourceColumn = source.column(src);
            if (m.isMapped() && sourceColumn.isEmpty()) {
                issues.add(error("SOURCE_COLUMN_MISSING", id, src, m.getTargetColumn(),
                        "The source column " + src + " does not exist in " + pair.getSourceTable().key() + "."));
                continue;
            }
            if (m.getStatus().awaitsReview()) {
                issues.add(error("REVIEW_REQUIRED", id, src, m.getTargetColumn(), reviewMessage(m)));
            }
            if (!m.isMapped()) {
                unmappedSources.add(src);
                continue;
            }
            mapped++;
            Optional<ColumnMetadata> targetColumn = target.column(m.getTargetColumn());
            if (targetColumn.isEmpty()) {
                issues.add(error("TARGET_COLUMN_MISSING", id, src, m.getTargetColumn(),
                        "The target column " + m.getTargetColumn() + " does not exist in "
                                + pair.getTargetTable().key() + "."));
                continue;
            }
            sourcesByTarget.computeIfAbsent(m.getTargetColumn(), k -> new ArrayList<>()).add(src);
            validateColumnPair(id, m, sourceColumn.get(), targetColumn.get(), sourceDb, targetDb, issues);
        }

        sourcesByTarget.forEach((targetName, sources) -> {
            if (sources.size() > 1) {
                issues.add(error("DUPLICATE_TARGET", id, null, targetName, "More than one source column ("
                        + String.join(", ", sources) + ") is mapped to the target column " + targetName
                        + ". Each target column can receive only one source column."));
            }
        });

        List<String> unmappedOptionalTargets = new ArrayList<>();
        for (ColumnMetadata t : target.columns()) {
            if (sourcesByTarget.containsKey(t.name())) {
                continue;
            }
            if (t.required()) {
                issues.add(error("REQUIRED_TARGET_UNMAPPED", id, null, t.name(), "The target column " + t.name()
                        + " requires a value (not null, no default) but no source column is mapped to it."));
            } else if (t.primaryKey() && (t.autoIncrement() || t.generated())) {
                issues.add(info("PRIMARY_KEY_GENERATED", id, null, t.name(), "The target primary key " + t.name()
                        + " is generated by the database; source key values will not be copied into it."));
            } else {
                unmappedOptionalTargets.add(t.name());
            }
        }

        if (mapped == 0) {
            issues.add(error("NO_COLUMNS_MAPPED", id, null, null, "No columns are mapped in " + label + "."));
        }
        if (!unmappedSources.isEmpty()) {
            issues.add(warning("UNMAPPED_SOURCE", id, null, null, unmappedSources.size() + " source column(s) of "
                    + label + " are not mapped and their data will not be migrated: " + list(unmappedSources) + "."));
        }
        if (!unmappedOptionalTargets.isEmpty()) {
            issues.add(info("UNMAPPED_TARGET", id, null, null, unmappedOptionalTargets.size()
                    + " optional target column(s) of " + label + " receive no data and will keep their default or "
                    + "empty value: " + list(unmappedOptionalTargets) + "."));
        }
    }

    private void validateColumnPair(String pairId, ColumnMapping m, ColumnMetadata s, ColumnMetadata t,
                                    DatabaseType sourceDb, DatabaseType targetDb, List<ValidationIssue> issues) {
        String src = s.name();
        String tgt = t.name();
        String arrow = src + " \u2192 " + tgt;
        Transformation transformation = m.getTransformation();

        if (!transformation.isApplicable(TypeNormalizer.normalize(s, sourceDb).category(),
                TypeNormalizer.normalize(t, targetDb).category())) {
            issues.add(error("TRANSFORMATION_INVALID", pairId, src, tgt, "The conversion '" + transformation.label()
                    + "' cannot be used for " + arrow + "."));
            return;
        }
        Optional<String> parameterError = transformation.validateParameter(m.getTransformationParameter(),
                TypeNormalizer.normalize(t, targetDb).category());
        if (parameterError.isPresent()) {
            issues.add(error("TRANSFORMATION_INVALID", pairId, src, tgt, arrow + ": " + parameterError.get()));
            return;
        }

        NormalizedType sourceType = TypeNormalizer.normalize(s, sourceDb);
        NormalizedType targetType = TypeNormalizer.normalize(t, targetDb);
        TypeCompatibility compat = TypeCompatibilityChecker.check(sourceType, targetType, transformation);
        switch (compat.level()) {
            case INCOMPATIBLE -> issues.add(error("TYPE_INCOMPATIBLE", pairId, src, tgt, arrow + ": "
                    + String.join(" ", compat.warnings())
                    + " Choose a supported conversion or clear this mapping."));
            case CONDITIONAL -> compat.warnings().forEach(w ->
                    issues.add(warning("TYPE_CONDITIONAL", pairId, src, tgt, arrow + ": " + w)));
            default -> {
                // identical or compatible: nothing to report
            }
        }

        if (s.nullable() && !t.nullable() && !t.hasDefault() && transformation != Transformation.DEFAULT_IF_NULL) {
            issues.add(warning("NULL_RISK", pairId, src, tgt, arrow + ": the source can be empty (NULL) but the target "
                    + "does not allow empty values. Use 'Default when empty' or make sure the data has no gaps."));
        }
        if (s.primaryKey() && !t.primaryKey()) {
            issues.add(warning("PRIMARY_KEY_NOT_PRESERVED", pairId, src, tgt, arrow + ": the source is a primary key "
                    + "column but the target is not, so uniqueness is not guaranteed in the target."));
        } else if (!s.primaryKey() && t.primaryKey()) {
            issues.add(warning("PRIMARY_KEY_UNIQUENESS_RISK", pairId, src, tgt, arrow + ": the target is a primary "
                    + "key column but the source is not; duplicate or empty values would be rejected."));
        }
    }

    private static String reviewMessage(ColumnMapping m) {
        if (m.getStatus() == MappingStatus.AMBIGUOUS) {
            return "The source column " + m.getSourceColumn() + " has several possible targets. Choose one, or "
                    + "clear it to leave it unmapped.";
        }
        return "The mapping " + m.getSourceColumn() + " \u2192 " + m.getTargetColumn()
                + " is still waiting for your review. Accept, change or reject it.";
    }

    private static String list(List<String> names) {
        if (names.size() <= LIST_LIMIT) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, LIST_LIMIT)) + " and " + (names.size() - LIST_LIMIT) + " more";
    }

    private static ValidationIssue error(String code, String pair, String source, String target, String message) {
        return new ValidationIssue(Severity.ERROR, code, pair, source, target, message);
    }

    private static ValidationIssue warning(String code, String pair, String source, String target, String message) {
        return new ValidationIssue(Severity.WARNING, code, pair, source, target, message);
    }

    private static ValidationIssue info(String code, String pair, String source, String target, String message) {
        return new ValidationIssue(Severity.INFO, code, pair, source, target, message);
    }
}
