package com.datasphere.migration.support;

import com.datasphere.migration.repository.ActivityRepository;
import com.datasphere.migration.repository.MetricsRepository;
import com.datasphere.migration.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for full-context web tests. The test administrator is generated at run time with a throw-away
 * password, so no credential — real or development — is stored in test sources.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJdbcConfig.class)
public abstract class AbstractWebTest {

    protected static final String ADMIN_USER = "test-admin";
    protected static final String ADMIN_PASSWORD = "Unit-Test-Only-Pw-1!";

    /** One throw-away folder per test JVM for saved projects, so tests never touch a real ./data folder. */
    protected static final java.nio.file.Path DATA_DIR = createDataDir();

    private static java.nio.file.Path createDataDir() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("datasphere-test-data");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void adminProperties(DynamicPropertyRegistry registry) {
        registry.add("datasphere.storage.data-dir", () -> DATA_DIR.toString());
        String hash = new BCryptPasswordEncoder(4).encode(ADMIN_PASSWORD);
        registry.add("datasphere.security.admin.username", () -> ADMIN_USER);
        registry.add("datasphere.security.admin.display-name", () -> "Test Administrator");
        registry.add("datasphere.security.admin.password-hash", () -> hash);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected LoginAttemptService loginAttempts;
    @Autowired
    protected MetricsRepository metrics;
    @Autowired
    protected ActivityRepository activity;

    @BeforeEach
    void resetSharedState() {
        loginAttempts.clear();
        metrics.clear();
        activity.clear();
    }
}
