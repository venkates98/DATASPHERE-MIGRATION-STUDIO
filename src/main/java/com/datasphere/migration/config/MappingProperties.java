package com.datasphere.migration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable parameters of the deterministic mapping engine ({@code datasphere.mapping.*}).
 * The scoring weights must add up to 100 so a score reads as "points out of 100".
 */
@Validated
@ConfigurationProperties(prefix = "datasphere.mapping")
public record MappingProperties(
        @Valid @NotNull @DefaultValue Weights weights,
        @Valid @NotNull @DefaultValue Thresholds thresholds,
        /** Optional properties file whose entries extend/override the built-in abbreviation dictionary. */
        @DefaultValue("") String abbreviationFile) {

    public MappingProperties {
        double sum = weights.total();
        if (Math.abs(sum - 100.0) > 0.001) {
            throw new IllegalArgumentException(
                    "datasphere.mapping.weights must add up to 100 but add up to " + sum);
        }
    }

    /** Points awarded per kind of evidence. */
    public record Weights(
            @DecimalMin("0") @DefaultValue("40") double exact,
            @DecimalMin("0") @DefaultValue("25") double synonym,
            @DecimalMin("0") @DefaultValue("15") double similarity,
            @DecimalMin("0") @DefaultValue("10") double dataType,
            @DecimalMin("0") @DefaultValue("5") double length,
            @DecimalMin("0") @DefaultValue("5") double constraint) {

        public double total() {
            return exact + synonym + similarity + dataType + length + constraint;
        }
    }

    /** Classification bands and selection rules. */
    public record Thresholds(
            @DecimalMin("0") @DecimalMax("100") @DefaultValue("90") double high,
            @DecimalMin("0") @DecimalMax("100") @DefaultValue("75") double good,
            @DecimalMin("0") @DecimalMax("100") @DefaultValue("50") double review,
            /** Candidates scoring below this are never proposed automatically. */
            @DecimalMin("0") @DecimalMax("100") @DefaultValue("40") double minimumSuggestion,
            /** If the best and second-best candidates are closer than this, the mapping is ambiguous. */
            @DecimalMin("0") @DecimalMax("100") @DefaultValue("8") double ambiguityGap,
            /** Points removed when the data types are incompatible. */
            @DecimalMin("0") @DecimalMax("100") @DefaultValue("30") double incompatiblePenalty,
            /** Share of the exact-match points given when the same words appear in a different order. */
            @DecimalMin("0") @DecimalMax("1") @DefaultValue("0.5") double reorderedWordsFactor) {
    }

    /** Defaults used by unit tests and by code that has no Spring context. */
    public static MappingProperties defaults() {
        return new MappingProperties(
                new Weights(40, 25, 15, 10, 5, 5),
                new Thresholds(90, 75, 50, 40, 8, 30, 0.5),
                "");
    }
}
