package com.datasphere.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DataSphere Migration Studio — Connect. Map. Validate. Migrate.
 */
// There is deliberately no application database: connection profiles and job history live in memory, and
// JdbcTemplate instances are created per user-defined connection. Spring's single-DataSource auto-configuration
// therefore has nothing to configure and is excluded.
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class})
@ConfigurationPropertiesScan
public class DataSphereMigrationStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataSphereMigrationStudioApplication.class, args);
    }
}
