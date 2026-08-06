package com.lms.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@Testcontainers
public class MigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("lms_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Redis so we don't need a container for it just for DB migration test
        registry.add("spring.data.redis.repositories.enabled", () -> false);
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FlywayMigrationRunner flywayMigrationRunner;

    @Test
    void testCleanDatabaseMigration() {
        assertDoesNotThrow(() -> {
            flywayMigrationRunner.afterPropertiesSet();
            // In a clean test database, the public.tenants table is created,
            // but no tenants are active initially unless seeded by DataSeeder.
            // Let's manually run the tenant migration to test V4
            flywayMigrationRunner.migrateTenantSchema("tenant_demo");
        });

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Assert password_hash exists in tenant_demo.users
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = 'tenant_demo' AND table_name = 'users' AND column_name = 'password_hash'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
}
