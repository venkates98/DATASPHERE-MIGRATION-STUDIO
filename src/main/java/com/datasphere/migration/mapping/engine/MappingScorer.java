package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.config.MappingProperties;
import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.Transformation;
import com.datasphere.migration.mapping.type.TypeCompatibility;
import com.datasphere.migration.mapping.type.TypeCompatibilityChecker;
import com.datasphere.migration.metadata.ColumnMetadata;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the evidence for one (source column, target column) pair into a score out of 100:
 * name evidence (exact / synonym / similarity), data-type compatibility, length and precision fit, and
 * constraint agreement. Weights come from {@link MappingProperties}. No data values are ever consulted.
 */
public final class MappingScorer {

    private final MappingProperties properties;

    public MappingScorer(MappingProperties properties) {
        this.properties = properties;
    }

    public ScoreBreakdown score(ColumnMetadata source, ColumnMetadata target, NormalizedType sourceType,
                                NormalizedType targetType, NameEvidence names, Transformation transformation) {
        MappingProperties.Weights w = properties.weights();
        MappingProperties.Thresholds t = properties.thresholds();

        NameEvidence.Points name = names.points(w, t);
        TypeCompatibility compat = TypeCompatibilityChecker.check(sourceType, targetType, transformation);
        double typePoints = w.dataType() * compat.level().scoreFactor();
        double sizePoints = w.length() * compat.sizeFactor();

        List<String> reasons = new ArrayList<>(names.reasons());
        List<String> warnings = new ArrayList<>(compat.warnings());
        reasons.add(typeReason(compat, sourceType, targetType));
        reasons.addAll(compat.notes());

        double constraintPoints = w.constraint() * constraints(source, target, reasons, warnings);

        double penalty = compat.isIncompatible() ? t.incompatiblePenalty() : 0;
        double total = round(Math.max(0, Math.min(100,
                name.total() + typePoints + sizePoints + constraintPoints - penalty)));
        return new ScoreBreakdown(round(name.exact()), round(name.synonym()), round(name.similarity()),
                round(typePoints), round(sizePoints), round(constraintPoints), round(penalty), total,
                ConfidenceClass.of(total, t), names.tier(), compat, List.copyOf(reasons), List.copyOf(warnings));
    }

    private static String typeReason(TypeCompatibility compat, NormalizedType s, NormalizedType t) {
        String pair = s.display() + " \u2192 " + t.display();
        return switch (compat.level()) {
            case IDENTICAL -> "Both columns use the same data type (" + s.display() + ").";
            case COMPATIBLE -> "The data types are compatible (" + pair + ").";
            case CONDITIONAL -> "The data types are compatible with conditions (" + pair + ").";
            case INCOMPATIBLE -> "The data types are not compatible (" + pair + ").";
        };
    }

    /**
     * Share (0..1) of four agreement checks that hold: primary key, nullability, uniqueness and
     * foreign-key relationship. Disagreements are also reported as warnings.
     */
    private static double constraints(ColumnMetadata s, ColumnMetadata t, List<String> reasons, List<String> warnings) {
        int ok = 0;

        if (s.primaryKey() == t.primaryKey()) {
            ok++;
            if (s.primaryKey()) {
                reasons.add("Both columns are part of their table's primary key.");
            }
        } else if (s.primaryKey()) {
            warnings.add("The source column is a primary key column but the target column is not.");
        } else {
            warnings.add("The target column is a primary key column but the source column is not, so values may "
                    + "repeat or be empty.");
        }

        if (!(s.nullable() && !t.nullable() && !t.hasDefault())) {
            ok++;
        } else {
            warnings.add("The source column can be empty (NULL) but the target column does not allow empty values.");
        }

        boolean keyColumns = s.primaryKey() || t.primaryKey();
        if (keyColumns || s.unique() == t.unique() || !t.unique()) {
            ok++;
            if (!keyColumns && s.unique() && t.unique()) {
                reasons.add("Both columns have a unique constraint.");
            }
        } else {
            warnings.add("The target column requires unique values but the source column does not guarantee that.");
        }

        if (s.foreignKey() == t.foreignKey()) {
            ok++;
            if (s.foreignKey()) {
                reasons.add("Both columns reference another table (" + s.referencedTable() + " and "
                        + t.referencedTable() + ").");
            }
        } else if (t.foreignKey()) {
            warnings.add("The target column must match a row in " + t.referencedTable()
                    + "; make sure the source values exist there.");
        } else {
            ok++; // a source foreign key without a target counterpart loses no data
        }
        return ok / 4.0;
    }

    static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
