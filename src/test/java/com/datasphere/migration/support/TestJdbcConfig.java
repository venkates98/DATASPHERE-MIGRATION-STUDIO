package com.datasphere.migration.support;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.JdbcConnectionFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real JDBC connection factory with in-memory H2 databases so that discovery, drift detection and
 * the whole workflow run against genuine {@code DatabaseMetaData}, without any external database server.
 * The H2 compatibility mode follows the product the profile claims to be (identifier quoting in particular).
 */
@TestConfiguration
public class TestJdbcConfig {

    public static final String USER = "sa";
    public static final String PASSWORD = "pw-test-only";

    public static String url(DatabaseType type, String database) {
        String mode = switch (type) {
            case SQLSERVER -> "MSSQLServer";
            case ORACLE -> "Oracle";
            case POSTGRESQL -> "PostgreSQL";
            case MYSQL -> "MySQL";
        };
        return "jdbc:h2:mem:" + database + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1";
    }

    /** Creates (or opens) an in-memory database and runs the given DDL statements. */
    public static void create(DatabaseType type, String database, String... statements) {
        try (Connection c = DriverManager.getConnection(url(type, database), USER, PASSWORD);
             Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prepare test database " + database, e);
        }
    }

    @Bean
    @Primary
    JdbcConnectionFactory h2ConnectionFactory() {
        return (profile, password) -> DriverManager.getConnection(url(profile.type(), profile.database()),
                profile.username(), new String(password));
    }
}
