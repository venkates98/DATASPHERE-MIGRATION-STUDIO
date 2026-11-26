package com.datasphere.migration.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datasphere.migration.dto.ActivityEvent;
import com.datasphere.migration.support.AbstractWebTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DashboardPageTest extends AbstractWebTest {

    @Test
    @DisplayName("empty session shows zero values, an empty state and the in-memory notice — no fake history")
    void emptyState() throws Exception {
        mvc.perform(get("/dashboard").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No activity yet")))
                .andExpect(content().string(containsString("Session data only")))
                .andExpect(content().string(containsString("Connection profiles")))
                .andExpect(content().string(containsString("Idle")))
                .andExpect(content().string(containsString("Test Administrator")));
    }

    @Test
    @DisplayName("unavailable quick actions are disabled and show their reason")
    void quickActionsDisabledWithReason() throws Exception {
        String html = mvc.perform(get("/dashboard").with(user(ADMIN_USER).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("Test a connection", "Compare schemas", "Create a mapping", "Start a migration")
                .contains("Not available yet. Delivered in development phase 6.");
    }

    @Test
    @DisplayName("recent activity is rendered and HTML-escaped")
    void activityIsEscaped() throws Exception {
        activity.add(new ActivityEvent(Instant.now(), "TEST", "<script>alert(1)</script>", "OK", "JOB-1"));
        mvc.perform(get("/dashboard").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(content().string(not(containsString("No activity yet"))))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("JOB-1")));
    }

    @Test
    @DisplayName("counters reflect the in-memory metrics")
    void countersReflectMetrics() throws Exception {
        metrics.addRecordCounts(1500, 1400, 20, 70, 10);
        metrics.recordConnectionTest(true);
        metrics.recordConnectionTest(false);
        mvc.perform(get("/dashboard").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(content().string(containsString("1,500")))
                .andExpect(content().string(containsString("1,400")));
    }

    @Test
    @DisplayName("planned pages state they are not available and disable their operations")
    void plannedPageIsHonest() throws Exception {
        mvc.perform(get("/relationships").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not available in this build")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString("Potential risks")))
                .andExpect(content().string(containsString("Required inputs")));
    }

    @Test
    @DisplayName("settings shows effective limits but never the password hash")
    void settingsPage() throws Exception {
        mvc.perform(get("/settings").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Maximum batch size")))
                .andExpect(content().string(containsString("Login throttling")))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    @DisplayName("help lists every error code")
    void helpPage() throws Exception {
        mvc.perform(get("/help").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DB-CONN-001")))
                .andExpect(content().string(containsString("MIG-TXN-001")))
                .andExpect(content().string(containsString("does not claim 100% accuracy")))
                .andExpect(content().string(containsString("Sign in and open a project")));
    }
}
