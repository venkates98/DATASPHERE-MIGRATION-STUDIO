package com.datasphere.migration.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.datasphere.migration.support.AbstractWebTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginThrottlingIntegrationTest extends AbstractWebTest {

    @Test
    @DisplayName("after the maximum failed attempts even the correct password is refused until the lock-out ends")
    void lockoutBlocksCorrectPassword() throws Exception {
        for (int i = 1; i <= 4; i++) {
            mvc.perform(formLogin("/login").user(ADMIN_USER).password("bad-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        // The fifth failure triggers the lock-out.
        mvc.perform(formLogin("/login").user(ADMIN_USER).password("bad-5"))
                .andExpect(redirectedUrl("/login?locked"));

        mvc.perform(formLogin("/login").user(ADMIN_USER).password(ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/login?locked"))
                .andExpect(unauthenticated());

        mvc.perform(get("/login").param("locked", ""))
                .andExpect(content().string(containsString("Too many attempts")));
    }

    @Test
    @DisplayName("a successful login clears earlier failures")
    void successResetsCounter() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mvc.perform(formLogin("/login").user(ADMIN_USER).password("bad-" + i));
        }
        mvc.perform(formLogin("/login").user(ADMIN_USER).password(ADMIN_PASSWORD))
                .andExpect(authenticated());
        for (int i = 1; i <= 3; i++) {
            mvc.perform(formLogin("/login").user(ADMIN_USER).password("bad-again-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    @Test
    @DisplayName("lock-out is enforced for unknown usernames too (no account enumeration)")
    void lockoutAppliesToUnknownUsers() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mvc.perform(formLogin("/login").user("ghost").password("x" + i));
        }
        mvc.perform(formLogin("/login").user("ghost").password("x6"))
                .andExpect(redirectedUrl("/login?locked"));
    }
}
