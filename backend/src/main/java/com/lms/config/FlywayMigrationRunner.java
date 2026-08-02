package com.lms.config;

import com.lms.tenant.Tenant;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * Deterministic programmatic Flyway migration runner.
 *
 * <p>Execution order (before DataSeeder):
 * <ol>
 *   <li>Apply V1 migration against the {@code public} schema (tenants registry, global_configs)</li>
 *   <li>Read all active tenants from {@code public.tenants}</li>
 *   <li>For each tenant, create schema if absent, then apply V2 migrations</li>
 * </ol>
 *
 * <p>This is idempotent — Flyway tracks applied versions and only runs new migrations.
 * Using {@code ApplicationReadyEvent} with {@code @Order(1)} ensures it runs
 * before {@code DataSeeder} ({@code @Order(2)}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlywayMigrationRunner {

    private final DataSource dataSource;
    private final TenantRepository tenantRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void runMigrations() {
        log.info("=== Flyway Migration Runner starting ===");

        // Step 1: Shared public schema (V1)
        migratePublicSchema();

        // Step 2: Per-tenant schemas (V2)
        migrateAllTenantSchemas();

        log.info("=== Flyway Migration Runner complete ===");
    }

    /**
     * Runs V1__create_shared_schema.sql against the {@code public} schema.
     */
    private void migratePublicSchema() {
        log.info("[FLYWAY] Migrating public schema...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration")
                .table("flyway_schema_history")
                // Only apply V1 here — V2 is per-tenant only
                .sqlMigrationPrefix("V")
                .target("1")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(false)
                .load();
        try {
            flyway.migrate();
            log.info("[FLYWAY] Public schema migration complete.");
        } catch (Exception e) {
            log.error("[FLYWAY] Public schema migration failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * For each active tenant: ensures the schema exists, then runs V2 migrations.
     */
    private void migrateAllTenantSchemas() {
        List<Tenant> tenants;
        try {
            tenants = tenantRepository.findAll();
        } catch (Exception e) {
            log.warn("[FLYWAY] Cannot read tenants (public schema may be empty on first boot): {}", e.getMessage());
            return;
        }

        if (tenants.isEmpty()) {
            log.info("[FLYWAY] No tenants found yet — tenant schemas will be created on first tenant registration.");
            return;
        }

        for (Tenant tenant : tenants) {
            if (!tenant.isActive()) {
                log.debug("[FLYWAY] Skipping inactive tenant: {}", tenant.getSchemaName());
                continue;
            }
            migrateTenantSchema(tenant.getSchemaName());
        }
    }

    /**
     * Creates (if absent) and migrates a single tenant schema.
     *
     * @param schemaName e.g. "tenant_demo"
     */
    public void migrateTenantSchema(String schemaName) {
        log.info("[FLYWAY] Migrating tenant schema: {}", schemaName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration")
                .table("flyway_schema_history")
                // Only V2 and above — tenant schemas don't get V1 (public schema tables)
                .sqlMigrationPrefix("V")
                .target("2")
                .baselineOnMigrate(true)
                .baselineVersion("1")  // Pretend V1 already ran (it's for public only)
                .outOfOrder(false)
                .createSchemas(true)
                .load();
        try {
            flyway.migrate();
            log.info("[FLYWAY] Tenant schema '{}' migration complete.", schemaName);
        } catch (Exception e) {
            log.error("[FLYWAY] Tenant schema '{}' migration failed: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to migrate tenant schema: " + schemaName, e);
        }
    }
}
