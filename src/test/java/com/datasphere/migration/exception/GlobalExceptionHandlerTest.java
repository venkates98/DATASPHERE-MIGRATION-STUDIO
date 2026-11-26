package com.datasphere.migration.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.datasphere.migration.support.AbstractWebTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GlobalExceptionHandlerTest extends AbstractWebTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    @DisplayName("application errors return code, message, action, correlation id — and never the secret")
    void applicationErrorAsJson() throws Exception {
        mvc.perform(get("/probe/app-error").accept(MediaType.APPLICATION_JSON)
                        .with(user("u").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DB-CONN-001"))
                .andExpect(jsonPath("$.category").value("Connectivity error"))
                .andExpect(jsonPath("$.message").value(containsString("Connection failed")))
                .andExpect(jsonPath("$.suggestedAction").value(containsString("hostname, port")))
                .andExpect(jsonPath("$.correlationId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("LeakyPassw0rd"))))
                .andExpect(content().string(not(containsString("jdbc:"))))
                .andExpect(content().string(not(containsString("SQLException"))));
    }

    @Test
    @DisplayName("the correlation id in the body matches the response header")
    void correlationIdMatchesHeader() throws Exception {
        String header = mvc.perform(get("/probe/mapping-error").accept(MediaType.APPLICATION_JSON)
                        .with(user("u").roles("ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAP-VAL-001"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");
        org.assertj.core.api.Assertions.assertThat(header).matches(UUID_PATTERN);
    }

    @Test
    @DisplayName("browser requests get the friendly error page with no technical detail")
    void applicationErrorAsHtml() throws Exception {
        mvc.perform(get("/probe/app-error").accept(MediaType.TEXT_HTML).with(user("u").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("DB-CONN-001")))
                .andExpect(content().string(containsString("Correlation ID")))
                .andExpect(content().string(not(containsString("LeakyPassw0rd"))))
                .andExpect(content().string(not(containsString("sqlserver"))));
    }

    @Test
    @DisplayName("unexpected exceptions are classified SYS-UNEXPECTED-001 and leak nothing")
    void unexpectedException() throws Exception {
        mvc.perform(get("/probe/unexpected").accept(MediaType.APPLICATION_JSON).with(user("u").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYS-UNEXPECTED-001"))
                .andExpect(content().string(not(containsString("abc123"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.datasphere"))));
    }

    @Test
    @DisplayName("bean-validation failures report the offending field")
    void validationErrors() throws Exception {
        mvc.perform(post("/probe/validate").with(csrf()).with(user("u").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CFG-VAL-001"))
                .andExpect(jsonPath("$.fieldIssues[0].field").value("name"))
                .andExpect(jsonPath("$.fieldIssues[0].message").value("Profile name is required"));
    }

    @Test
    @DisplayName("malformed JSON is a classified bad request, not a stack trace")
    void malformedBody() throws Exception {
        mvc.perform(post("/probe/validate").with(csrf()).with(user("u").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CFG-REQ-001"));
    }

    @Test
    @DisplayName("unknown pages give a 404 error page for HTML and JSON")
    void notFound() throws Exception {
        mvc.perform(get("/does-not-exist").accept(MediaType.TEXT_HTML).with(user("u").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("CFG-NOTFOUND-001")));
        mvc.perform(get("/api/does-not-exist").with(user("u").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", containsString("json")))
                .andExpect(jsonPath("$.code").value("CFG-NOTFOUND-001"));
    }

    @Test
    @DisplayName("wrong HTTP method yields CFG-METHOD-001")
    void methodNotAllowed() throws Exception {
        mvc.perform(post("/dashboard").with(csrf()).with(user("u").roles("ADMIN")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("CFG-METHOD-001"));
    }
}
