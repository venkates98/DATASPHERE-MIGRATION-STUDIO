package com.datasphere.migration.db.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.TypeCategory;
import com.datasphere.migration.mapping.type.TypeConversionService;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DatabaseAdapterTest {

    @Test
    void everyEnumValueHasAnAdapterAndDriverClass() {
        assertThat(DatabaseAdapters.all()).hasSize(DatabaseType.values().length);
        for (DatabaseType type : DatabaseType.values()) {
            DatabaseAdapter adapter = DatabaseAdapters.require(type);
            assertThat(adapter.driverClassName()).isNotBlank();
            assertThat(adapter.defaultPort()).isBetween(1, 65535);
            assertThat(adapter.jdbcUrl("db.example.internal", adapter.defaultPort(), "app", false, 10))
                    .startsWith("jdbc:").doesNotContain("password");
            assertThat(adapter.quote("x]y")).contains("x");
        }
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void quotingDoesNotConcatenateUserTextIntoKeywords(DatabaseType type) {
        String quoted = type.quote("users; drop table t");
        assertThat(quoted).contains("users; drop table t");
        assertThat(quoted.length()).isGreaterThan("users; drop table t".length());
        assertThat(type.qualified("hr", "emp")).contains(type.quote("hr"));
    }

    @Test
    void conversionCoversEveryCategoryOnEveryTarget() {
        TypeConversionService svc = new TypeConversionService();
        NormalizedType integer = new NormalizedType(TypeCategory.INTEGER, "int", "INT", false, null, 10, 0, null);
        for (DatabaseType source : DatabaseType.values()) {
            for (DatabaseType target : DatabaseType.values()) {
                var result = svc.propose(integer, source, target);
                assertThat(result.supported()).as(source + "->" + target).isTrue();
                assertThat(result.targetType().ddl()).isNotBlank();
            }
        }
        var other = svc.propose(NormalizedType.of(TypeCategory.OTHER, "XMLTYPE"), DatabaseType.ORACLE,
                DatabaseType.SQLSERVER);
        assertThat(other.supported()).isFalse();
        assertThat(other.unsupportedReason()).isNotBlank();
    }

    @Test
    void matrixMarksEveryPairPartialBecauseLiveServersWereNotTested() {
        CompatibilityMatrixService matrix = new CompatibilityMatrixService();
        assertThat(matrix.matrix()).hasSize(16);
        assertThat(matrix.matrix()).allMatch(c -> "PARTIAL SUPPORT".equals(c.overall()) || "UNSUPPORTED".equals(c.overall()));
        assertThat(matrix.catalog()).extracting(CompatibilityMatrixService.DatabaseCatalogEntry::id)
                .containsExactlyInAnyOrderElementsOf(
                        EnumSet.allOf(DatabaseType.class).stream().map(Enum::name).toList());
    }
}
