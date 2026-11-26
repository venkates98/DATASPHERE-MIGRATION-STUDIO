package com.datasphere.migration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datasphere.migration.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserConfigTest {

    private final AdminUserConfig config = new AdminUserConfig();
    private final PasswordEncoder encoder = config.passwordEncoder();

    private static AppProperties props(String username, String hash) {
        return new AppProperties("1.0",
                new AppProperties.Security(new AppProperties.Admin(username, "Admin", hash),
                        new AppProperties.Login(5, java.time.Duration.ofMinutes(15))),
                new AppProperties.Limits(50, 10000, 1000, 1000, 2));
    }

    private static String hash(String password) {
        return new BCryptPasswordEncoder(4).encode(password);
    }

    @Test
    void startsWithValidBcryptHash() {
        String h = hash("a-test-password");
        UserDetails user = config.userDetailsService(props("admin", h), new MockEnvironment()).loadUserByUsername("admin");
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(encoder.matches("a-test-password", user.getPassword())).isTrue();
        assertThat(encoder.matches("other", user.getPassword())).isFalse();
    }

    @Test
    void acceptsBcryptPrefix() {
        String h = "{bcrypt}" + hash("pw");
        assertThat(config.userDetailsService(props("admin", h), new MockEnvironment())).isNotNull();
    }

    @Test
    void refusesToStartWithoutAdministrator() {
        assertThatThrownBy(() -> config.userDetailsService(props("", ""), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No administrator configured");
    }

    @Test
    void refusesPlainTextPasswordAndDoesNotEchoIt() {
        assertThatThrownBy(() -> config.userDetailsService(props("admin", "plain-text-secret"), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BCrypt")
                .hasMessageNotContaining("plain-text-secret");
    }

    @Test
    void refusesWeakDelegatingFormats() {
        assertThatThrownBy(() -> config.userDetailsService(props("admin", "{noop}secret"), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devProfileCannotBeCombinedWithProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "prod");
        assertThatThrownBy(() -> config.userDetailsService(props("admin", hash("pw")), env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be combined");
    }

    @Test
    void passwordEncoderDoesNotSupportNoop() {
        assertThatThrownBy(() -> encoder.matches("secret", "{noop}secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminToStringNeverShowsHash() {
        String h = hash("pw");
        assertThat(props("admin", h).security().admin().toString()).doesNotContain(h).contains("****");
    }
}
