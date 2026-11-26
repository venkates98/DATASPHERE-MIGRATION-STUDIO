package com.datasphere.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasphere.migration.dto.FeatureGuide;
import com.datasphere.migration.dto.MenuItem;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FeatureGuideCatalogTest {

    private final FeatureGuideCatalog catalog = new FeatureGuideCatalog();

    @ParameterizedTest
    @EnumSource(MenuItem.class)
    void everyMenuItemExplainsItself(MenuItem item) {
        FeatureGuide guide = catalog.guideFor(item);
        assertThat(guide).as(item.name()).isNotNull();
        assertThat(guide.whatItDoes()).isNotBlank();
        assertThat(guide.whenToUse()).isNotBlank();
        assertThat(guide.requiredInputs()).isNotEmpty();
        assertThat(guide.risks()).isNotEmpty();
        assertThat(guide.expectedResults()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(MenuItem.class)
    void operationsOfUnimplementedFeaturesAreDisabledWithAReason(MenuItem item) {
        FeatureGuide guide = catalog.guideFor(item);
        guide.operations().forEach(op -> {
            if (!item.isImplemented()) {
                assertThat(op.enabled()).as(op.label()).isFalse();
            }
            if (!op.enabled()) {
                assertThat(op.disabledReason()).as(op.label()).isNotBlank();
            }
        });
    }

    @Test
    void menuPathsAreUniqueAndResolvable() {
        assertThat(new HashSet<>(Arrays.stream(MenuItem.values()).map(MenuItem::getPath).toList()))
                .hasSize(MenuItem.values().length);
        for (MenuItem item : MenuItem.values()) {
            assertThat(MenuItem.forPath(item.getPath())).contains(item);
        }
        assertThat(MenuItem.forPath("/unknown")).isEmpty();
        assertThat(MenuItem.forPath(null)).isEmpty();
    }

    @Test
    void allRequiredMenusArePresentPlusMigrationProjects() {
        // The 12 original page menus plus Migration Projects (the workspace for connections and mapping).
        // Logout is rendered separately as a POST form and is the 13th of the original required menus.
        assertThat(MenuItem.values()).hasSize(13);
        assertThat(MenuItem.PROJECTS.isImplemented()).isTrue();
    }
}
