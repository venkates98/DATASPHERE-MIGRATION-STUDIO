package com.datasphere.migration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalModelAttributesTest {

    @Test
    void initialsUseLettersAndDigitsOnly() {
        assertThat(GlobalModelAttributes.initials("Venkat (dev)")).isEqualTo("VD");
        assertThat(GlobalModelAttributes.initials("venkat11484")).isEqualTo("V");
        assertThat(GlobalModelAttributes.initials("ada.lovelace")).isEqualTo("AL");
        assertThat(GlobalModelAttributes.initials("  ")).isEqualTo("?");
        assertThat(GlobalModelAttributes.initials(null)).isEqualTo("?");
        assertThat(GlobalModelAttributes.initials("(((")).isEqualTo("?");
    }
}
