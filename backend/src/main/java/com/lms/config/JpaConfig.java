package com.lms.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * JPA configuration that ensures Flyway migrations run BEFORE Hibernate
 * validates or creates any schema.
 *
 * <p>{@code @DependsOn("flywayMigrationRunner")} forces Spring to fully
 * initialize {@link FlywayMigrationRunner} (including {@code afterPropertiesSet()})
 * before the auto-configured {@code LocalContainerEntityManagerFactoryBean} is created.
 *
 * <p>Without this, on a clean database Hibernate's {@code ddl-auto: validate}
 * would throw {@code SchemaManagementException} before V1/V2 migrations apply.
 */
@Configuration
@DependsOn("flywayMigrationRunner")
public class JpaConfig {

    /**
     * Ensures Hibernate uses the correct multi-tenant schema resolver.
     * This bean also serves as an additional hook that depends on Flyway being ready.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        // The customizer itself doesn't need to do anything extra here;
        // its mere existence within a @DependsOn("flywayMigrationRunner") class
        // ensures the EMF setup waits for Flyway.
        return hibernateProperties -> {
            // Hibernate multi-tenancy strategy is configured via application.yml:
            // hibernate.multiTenancy=SCHEMA
            // This bean exists primarily for the DependsOn ordering guarantee.
        };
    }
}
