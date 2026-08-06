package com.lms.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic programmatic Flyway migration runner.
 *
 * <p><strong>Startup ordering (critical):</strong>
 * <ol>
 *   <li>This bean implements {@link InitializingBean} — Spring calls {@code afterPropertiesSet()}
 *       during the bean initialization phase, BEFORE {@code EntityManagerFactory} is created.</li>
 *   <li>{@link JpaConfig} declares {@code @DependsOn("flywayMigrationRunner")} on the JPA
 *       entity manager factory, guaranteeing migrations complete before Hibernate validates.</li>
 *   <li>Step 1: Apply V1 migration to the {@code public} schema (tenants table).</li>
 *   <li>Step 2: Read all active tenants using plain JDBC (no JPA, no circular dependency).</li>
 *   <li>Step 3: Apply V2 migration to each tenant schema.</li>
 * </ol>
 *
 * <p>This is idempotent — Flyway tracks applied versions per schema.
 */
@Component("flywayMigrationRunner")
@Slf4j
public class FlywayMigrationRunner implements InitializingBean {

    private final DataSource dataSource;

    @Value("${lms.tenant.default-slug:demo}")
    private String defaultTenantSlug;

    public FlywayMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Called by Spring during bean initialization — before EntityManagerFactory is created.
     * This guarantees the schema exists before Hibernate validation runs.
     */
    @Override
    public void afterPropertiesSet() {
        log.info("=== FlywayMigrationRunner starting (InitializingBean phase) ===");
        try {
            // Step 1: Public schema (tenants registry, global_configs)
            migratePublicSchema();

            // Step 2: All existing tenant schemas
            List<String> tenantSchemas = loadTenantSchemasFromJdbc();
            for (String schema : tenantSchemas) {
                migrateTenantSchema(schema);
            }

            log.info("=== FlywayMigrationRunner complete — {} tenant schemas migrated ===",
                    tenantSchemas.size());
        } catch (Exception e) {
            log.error("=== FlywayMigrationRunner FAILED: {} ===", e.getMessage(), e);
            throw new RuntimeException("Schema migration failed — cannot start application", e);
        }
    }

    // ─── Public Schema ────────────────────────────────────────────────────────

    private void migratePublicSchema() {
        log.info("[FLYWAY] Migrating public schema (V1)...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration/public")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(false)
                .load();
        int applied = flyway.migrate().migrationsExecuted;
        log.info("[FLYWAY] Public schema: {} migration(s) applied.", applied);
    }

    // ─── Tenant Schemas ───────────────────────────────────────────────────────

    /**
     * Reads active tenant schema names directly from PostgreSQL using plain JDBC.
     * Does NOT use JPA/Hibernate (which hasn't initialized yet at this point).
     */
    private List<String> loadTenantSchemasFromJdbc() {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT schema_name FROM public.tenants WHERE is_active = true";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
            log.info("[FLYWAY] Found {} active tenant schema(s) via JDBC.", schemas.size());
        } catch (Exception e) {
            // On a brand-new database the public.tenants table itself may not exist yet
            // (V1 hasn't run) — that's fine; we just return empty and continue.
            log.warn("[FLYWAY] Could not read tenant list (expected on first boot): {}", e.getMessage());
        }
        return schemas;
    }

    /**
     * Creates (if absent) and migrates a single tenant schema.
     * Safe to call multiple times — Flyway is idempotent.
     *
     * @param schemaName e.g. "tenant_demo"
     */
    public void migrateTenantSchema(String schemaName) {
        log.info("[FLYWAY] Migrating tenant schema: {}", schemaName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration/tenant")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .outOfOrder(false)
                .createSchemas(true)
                .load();
        try {
            int applied = flyway.migrate().migrationsExecuted;
            log.info("[FLYWAY] Tenant schema '{}': {} migration(s) applied.", schemaName, applied);
        } catch (Exception e) {
            log.error("[FLYWAY] Tenant schema '{}' migration failed: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to migrate tenant schema: " + schemaName, e);
        }
    }
}
