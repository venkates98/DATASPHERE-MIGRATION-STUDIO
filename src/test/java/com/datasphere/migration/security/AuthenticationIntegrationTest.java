package com.datasphere.migration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datasphere.migration.dto.MenuItem;
import com.datasphere.migration.support.AbstractWebTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;

class AuthenticationIntegrationTest extends AbstractWebTest {

    @Test
    @DisplayName("login page is public and shows the branded sign-in form")
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("Connect. Map. Validate. Migrate.")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("type=\"password\"")));
    }

    @ParameterizedTest(name = "unauthenticated GET {0} redirects to login")
    @ValueSource(strings = {"/dashboard", "/connections", "/migration", "/mapping", "/relationships",
            "/schema-comparison", "/validation", "/jobs", "/logs", "/reports", "/settings", "/help", "/", "/api/anything"})
    void protectedPagesRequireLogin(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("valid credentials authenticate and land on the dashboard")
    void validLogin() throws Exception {
        mvc.perform(formLogin("/login").user(ADMIN_USER).password(ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(authenticated().withUsername(ADMIN_USER));
    }

    @Test
    @DisplayName("invalid password is rejected with a friendly, non-revealing message")
    void invalidLogin() throws Exception {
        mvc.perform(formLogin("/login").user(ADMIN_USER).password("wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("username or password is incorrect")))
                .andExpect(content().string(not(containsString("wrong-password"))));
    }

    @Test
    @DisplayName("unknown user gets the same response as a wrong password")
    void unknownUserLooksLikeWrongPassword() throws Exception {
        mvc.perform(formLogin("/login").user("no-such-user").password("whatever"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("login with a forged CSRF token is refused")
    void loginRejectsInvalidCsrfToken() throws Exception {
        mvc.perform(post("/login").with(csrf().useInvalidToken())
                        .param("username", ADMIN_USER).param("password", ADMIN_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("login without any CSRF token never authenticates (sent back to sign-in as an expired page)")
    void loginWithoutCsrfTokenDoesNotAuthenticate() throws Exception {
        mvc.perform(post("/login").param("username", ADMIN_USER).param("password", ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("session id changes on login (session fixation protection)")
    void sessionFixationProtection() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        MvcResult result = mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", ADMIN_USER).param("password", ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(before);
    }

    @Test
    @DisplayName("an authenticated user opening /login is sent to the dashboard")
    void loginRedirectsWhenAuthenticated() throws Exception {
        mvc.perform(get("/login").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("logout (POST + CSRF) ends the session and shows a confirmation")
    void logout() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", ADMIN_USER).param("password", ADMIN_PASSWORD));

        MvcResult result = mvc.perform(post("/logout").session(session).with(csrf())
                        .with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();

        mvc.perform(get("/login").param("logout", ""))
                .andExpect(content().string(containsString("signed out successfully")));
    }

    @Test
    @DisplayName("logout with a forged CSRF token is refused and GET /logout does not log out")
    void logoutRequiresValidCsrf() throws Exception {
        mvc.perform(post("/logout").with(csrf().useInvalidToken()).with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/logout").with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().is4xxClientError());
    }

    @ParameterizedTest(name = "authenticated user can open {0}")
    @EnumSource(MenuItem.class)
    void everyMenuPageOpens(MenuItem item) throws Exception {
        if (item == MenuItem.CONNECTIONS || item == MenuItem.MAPPING) {
            // These entries now lead into the migration project workspace.
            mvc.perform(get(item.getPath()).with(user(ADMIN_USER).roles("ADMIN")))
                    .andExpect(status().is3xxRedirection());
            return;
        }
        mvc.perform(get(item.getPath()).with(user(ADMIN_USER).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1 class=\"ds-page-title\">"
                        + HtmlUtils.htmlEscape(item.getTitle()) + "</h1>")))
                .andExpect(content().string(containsString("Logout")));
    }

    @ParameterizedTest(name = "static asset {0} is public")
    @ValueSource(strings = {"/css/app.css", "/css/tokens.css", "/css/login.css", "/js/app.js", "/js/login.js",
            "/img/favicon.svg", "/webjars/bootstrap/css/bootstrap.min.css",
            "/webjars/bootstrap/js/bootstrap.bundle.min.js",
            "/webjars/bootstrap-icons/font/bootstrap-icons.min.css"})
    void staticAssetsArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("security headers are present and the CSP forbids inline scripts")
    void securityHeaders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("pages do not contain inline scripts or style attributes (strict CSP compatible)")
    void noInlineScriptsOrStyles() throws Exception {
        for (String path : Stream.of("/dashboard", "/connections", "/settings", "/help").toList()) {
            String html = mvc.perform(get(path).with(user(ADMIN_USER).roles("ADMIN")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as(path).doesNotContainPattern("<script(?![^>]*\\bsrc=)[^>]*>");
            assertThat(html).as(path).doesNotContainPattern("\\sstyle=\"");
        }
        String login = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertThat(login).doesNotContainPattern("<script(?![^>]*\\bsrc=)[^>]*>");
        assertThat(login).doesNotContainPattern("\\sstyle=\"");
    }
}
