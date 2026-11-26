package com.datasphere.migration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private MutableClock clock;
    private LoginAttemptService service;
    private final String ip = LoginAttemptService.ipKey("10.0.0.1");
    private final String user = LoginAttemptService.userKey("Alice");

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new LoginAttemptService(3, Duration.ofMinutes(10), clock);
    }

    @Test
    void locksAfterMaxFailures() {
        assertThat(service.recordFailure(ip, user)).isFalse();
        assertThat(service.recordFailure(ip, user)).isFalse();
        assertThat(service.isBlocked(ip, user)).isFalse();
        assertThat(service.recordFailure(ip, user)).isTrue();
        assertThat(service.isBlocked(ip)).isTrue();
        assertThat(service.isBlocked(user)).isTrue();
    }

    @Test
    void lockExpiresAfterDuration() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(user);
        }
        assertThat(service.isBlocked(user)).isTrue();
        clock.advance(Duration.ofMinutes(9));
        assertThat(service.isBlocked(user)).isTrue();
        clock.advance(Duration.ofMinutes(2));
        assertThat(service.isBlocked(user)).isFalse();
        // A fresh window starts after expiry.
        assertThat(service.recordFailure(user)).isFalse();
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        service.recordFailure(user);
        service.recordFailure(user);
        clock.advance(Duration.ofMinutes(11));
        assertThat(service.recordFailure(user)).isFalse();
        assertThat(service.isBlocked(user)).isFalse();
    }

    @Test
    void successClearsCounters() {
        service.recordFailure(user);
        service.recordFailure(user);
        service.recordSuccess(user);
        assertThat(service.recordFailure(user)).isFalse();
        assertThat(service.recordFailure(user)).isFalse();
    }

    @Test
    void usernameKeyIsCaseAndWhitespaceInsensitive() {
        assertThat(LoginAttemptService.userKey("  ALICE ")).isEqualTo(LoginAttemptService.userKey("alice"));
    }

    @Test
    void keysAreIndependent() {
        String other = LoginAttemptService.userKey("bob");
        for (int i = 0; i < 3; i++) {
            service.recordFailure(user);
        }
        assertThat(service.isBlocked(other)).isFalse();
    }

    @Test
    void overlongKeysAreBounded() {
        String key = LoginAttemptService.userKey("a".repeat(10_000));
        assertThat(key.length()).isLessThanOrEqualTo(5 + 128);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
