package com.datasphere.migration;

import org.springframework.boot.SpringApplication;

/**
 * One-click local launcher for IDEs (Eclipse: Run As > Java Application).
 *
 * <p>It starts the real application with the development-only {@code dev} profile, so no run-configuration
 * arguments are needed. It lives in {@code src/test/java} on purpose: it is never packaged into the
 * production jar, so the development account cannot be enabled by accident in a deployment.
 */
public final class DevApplication {

    private DevApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DataSphereMigrationStudioApplication.class);
        application.setAdditionalProfiles("dev");
        application.run(args);
    }
}
