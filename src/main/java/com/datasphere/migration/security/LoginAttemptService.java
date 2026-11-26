package com.datasphere.migration.security;

import com.datasphere.migration.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Throttles login attempts. Failures are tracked per client address and per submitted username, so both a
 * single client guessing many names and many clients guessing one account are slowed down.
 *
 * <p>State is in memory: it is reset on restart and is not shared between instances.
 * Trade-off: lock-out by username lets an attacker temporarily lock a known account;
 * the lock expires automatically and the audit log records it.
 */
@Service
public class LoginAttemptService {

    private static final Logger auditLog = LoggerFactory.getLogger("com.datasphere.migration.audit.login");
    private static final int PURGE_THRESHOLD = 5_000;
    private static final int MAX_KEY_LENGTH = 128;

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockout;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LoginAttemptService(AppProperties properties) {
        this(properties.security().login().maxAttempts(), properties.security().login().lockoutDuration(),
                Clock.systemUTC());
    }

    public LoginAttemptService(int maxAttempts, Duration lockout, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockout = lockout;
        this.clock = clock;
    }

    public static String ipKey(String address) {
        return "ip:" + limit(address);
    }

    public static String userKey(String username) {
        return "user:" + limit(username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    /** @return true if any key is currently locked. */
    public boolean isBlocked(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            Entry e = entries.get(key);
            if (e != null && e.lockedUntil != null && now.isBefore(e.lockedUntil)) {
                return true;
            }
        }
        return false;
    }

    /** Records a failure for each key and returns true if any key became (or already was) locked. */
    public boolean recordFailure(String... keys) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        boolean locked = false;
        for (String key : keys) {
            Entry updated = entries.compute(key, (k, existing) -> {
                Entry e = existing;
                if (e == null || e.isStale(now, lockout)) {
                    e = new Entry(now);
                }
                if (e.lockedUntil != null && now.isBefore(e.lockedUntil)) {
                    return e;
                }
                e.failures++;
                if (e.failures >= maxAttempts) {
                    e.lockedUntil = now.plus(lockout);
                    auditLog.warn("Login lock-out started key={} until={}", describe(k), e.lockedUntil);
                }
                return e;
            });
            if (updated.lockedUntil != null && now.isBefore(updated.lockedUntil)) {
                locked = true;
            }
        }
        return locked;
    }

    public void recordSuccess(String... keys) {
        for (String key : keys) {
            entries.remove(key);
        }
    }

    /** Forgets all throttling state (also what an application restart does). */
    public void clear() {
        entries.clear();
    }

    public int trackedKeys() {
        return entries.size();
    }

    private void purgeIfLarge(Instant now) {
        if (entries.size() > PURGE_THRESHOLD) {
            entries.entrySet().removeIf(e -> e.getValue().isStale(now, lockout));
        }
    }

    private static String limit(String value) {
        return value.length() > MAX_KEY_LENGTH ? value.substring(0, MAX_KEY_LENGTH) : value;
    }

    /** Avoid writing raw usernames from the key into logs verbatim. */
    private static String describe(String key) {
        return key.startsWith("ip:") ? key : "user:" + com.datasphere.migration.util.SensitiveDataMasker
                .sanitizeForLog(key.substring(5), 64);
    }

    private static final class Entry {
        private final Instant windowStart;
        private int failures;
        private Instant lockedUntil;

        private Entry(Instant windowStart) {
            this.windowStart = windowStart;
        }

        /** An entry is stale when its lock expired, or its failure window elapsed without a lock. */
        private boolean isStale(Instant now, Duration window) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return now.isAfter(windowStart.plus(window));
        }
    }
}
