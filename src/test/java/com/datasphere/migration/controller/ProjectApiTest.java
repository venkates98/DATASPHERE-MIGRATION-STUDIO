package com.datasphere.migration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datasphere.migration.support.AbstractWebTest;
import com.datasphere.migration.support.TestJdbcConfig;
import com.datasphere.migration.db.DatabaseType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Security and behaviour of the REST API as seen by the browser. */
class ProjectApiTest extends AbstractWebTest {

    @Autowired ObjectMapper json;

    private RequestPostProcessor admin() {
        return user(ADMIN_USER).roles("ADMIN");
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String connectionBody(String database) {
        return "{\"type\":\"ORACLE\",\"host\":\"localhost\",\"port\":1521,\"database\":\"" + database
                + "\",\"username\":\"" + TestJdbcConfig.USER + "\",\"password\":\"" + TestJdbcConfig.PASSWORD
                + "\",\"trustServerCertificate\":false}";
    }

    @Test
    void anonymousUsersCannotUseTheApi() throws Exception {
        MvcResult result = mvc.perform(get("/api/projects")).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(302, 401, 403);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Test migration");
    }

    @Test
    void stateChangingCallsNeedACsrfToken() throws Exception {
        // Without a token the request is refused (403, or a redirect to the login page when the session is
        // missing/expired); either way nothing is executed.
        String name = "csrf-" + UUID.randomUUID();
        MvcResult create = mvc.perform(post("/api/projects").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andReturn();
        assertThat(create.getResponse().getStatus()).isIn(302, 403);
        MvcResult start = mvc.perform(post("/api/migration/" + UUID.randomUUID() + "/start").with(admin()))
                .andReturn();
        assertThat(start.getResponse().getStatus()).isIn(302, 403);
        assertThat(mvc.perform(get("/api/projects").with(admin())).andReturn().getResponse().getContentAsString())
                .doesNotContain(name);
    }

    @Test
    void databaseCatalogAndCompatibilityMatrixAreGeneratedFromAdapters() throws Exception {
        mvc.perform(get("/api/databases").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].driverClass").exists())
                .andExpect(jsonPath("$[0].defaultPort").exists());
        mvc.perform(get("/api/compatibility").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].overall").exists())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIAL SUPPORT")));
        mvc.perform(get("/schema-comparison").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("registered database adapters")));
    }

    @Test
    void projectsCanBeCreatedListedFetchedAndDeleted() throws Exception {
        String id = createProject("API project");

        mvc.perform(get("/api/projects/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API project"))
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.capabilities.canApprove").value(false))
                .andExpect(jsonPath("$.capabilities.canStart").value(false));
        mvc.perform(get("/api/projects").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(id)));

        mvc.perform(delete("/api/projects/" + id).with(admin()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/" + id).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void invalidAndUnknownInputIsRejectedWithSafeMessages() throws Exception {
        mvc.perform(post("/api/projects").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldIssues").isNotEmpty());
        mvc.perform(get("/api/projects/not-a-valid-id").with(admin())).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/../../etc/passwd").with(admin())).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/projects/" + UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRJ-NOTFOUND-001"));
    }

    @Test
    void connectionTestRejectsDriverPropertyInjection() throws Exception {
        String body = connectionBody("db;socketFactory=evil");
        mvc.perform(post("/api/connections/target/test").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/connections/sideways/test").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(connectionBody("db"))).andExpect(status().is4xxClientError());
    }

    @Test
    void connectionIsTestedBeforeSavedAndThePasswordNeverComesBack() throws Exception {
        String db = "api" + UUID.randomUUID().toString().replace("-", "");
        TestJdbcConfig.create(DatabaseType.ORACLE, db, "CREATE SCHEMA APP");
        String id = createProject("Connection project");

        MvcResult tested = mvc.perform(post("/api/connections/target/test").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(connectionBody(db)))
                .andExpect(status().isOk()).andReturn();
        assertThat(tested.getResponse().getContentAsString()).doesNotContain(TestJdbcConfig.PASSWORD);

        MvcResult saved = mvc.perform(put("/api/projects/" + id + "/connections/target").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(connectionBody(db)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.credentialsAvailable").value(true)).andReturn();
        assertThat(saved.getResponse().getContentAsString()).doesNotContain(TestJdbcConfig.PASSWORD);
    }

    @Test
    void wrongCredentialsAreReportedWithoutLeakingTheSecret() throws Exception {
        String db = "api" + UUID.randomUUID().toString().replace("-", "");
        TestJdbcConfig.create(DatabaseType.ORACLE, db, "CREATE SCHEMA APP");
        String id = createProject("Bad credentials");
        String wrong = connectionBody(db).replace(TestJdbcConfig.PASSWORD, "definitely-wrong-pw");

        MvcResult result = mvc.perform(put("/api/projects/" + id + "/connections/target").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(wrong)).andReturn();

        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("definitely-wrong-pw");
        JsonNode project = json.readTree(mvc.perform(get("/api/projects/" + id).with(admin()))
                .andReturn().getResponse().getContentAsString());
        assertThat(project.get("target").isNull()).isTrue();
    }

    @Test
    void migrationEndpointsRefuseAnUnapprovedProject() throws Exception {
        String id = createProject("Unapproved");

        mvc.perform(post("/api/migration/" + id + "/preview").with(admin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAP-APPR-001"));
        mvc.perform(post("/api/migration/" + id + "/start").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmWrite\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAP-APPR-001"));
    }

    @Test
    void approvalWithoutAnyMappingIsRefused() throws Exception {
        String id = createProject("Nothing to approve");

        mvc.perform(post("/api/mapping/" + id + "/approve").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"confirmReviewed\":true}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/projects/" + id).with(admin())).andExpect(jsonPath("$.approval").doesNotExist());
    }

    @Test
    void transformationsOfferedMatchTheColumnTypes() throws Exception {
        MvcResult result = mvc.perform(get("/api/mapping/transformations?source=STRING&target=INTEGER")
                .with(admin())).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("PARSE_NUMBER");
        MvcResult none = mvc.perform(get("/api/mapping/transformations?source=DATE&target=INTEGER")
                .with(admin())).andExpect(status().isOk()).andReturn();
        assertThat(none.getResponse().getContentAsString()).doesNotContain("PARSE_NUMBER");
    }

    @Test
    void pagesRenderAndLegacyEntriesRedirect() throws Exception {
        String id = createProject("Page project");

        mvc.perform(get("/projects").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New project")));
        mvc.perform(get("/projects/" + id).with(admin())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Approve Mapping &amp; Continue")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-project-id=\"" + id + "\"")));
        mvc.perform(get("/projects/" + UUID.randomUUID()).with(admin())).andExpect(status().isNotFound());
        MvcResult redirect = mvc.perform(get("/connections").with(admin())).andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(redirect.getResponse().getRedirectedUrl()).startsWith("/projects");
        mvc.perform(get("/mapping").with(admin())).andExpect(status().is3xxRedirection());
    }

    @Test
    void projectPagesDoNotUseInlineScripts() throws Exception {
        String id = createProject("CSP project");
        String html = mvc.perform(get("/projects/" + id).with(admin())).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
        assertThat(html).doesNotContain("onclick=").doesNotContain(" style=\"");
    }
}
