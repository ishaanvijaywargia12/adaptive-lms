package com.lms.config;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.tenant.Tenant;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds default tenant and admin user for demo/dev environments.
 *
 * <p>Runs at startup, AFTER {@link FlywayMigrationRunner} (Order=2 vs Order=1).
 * Idempotent — skips if tenant/user already exist.
 *
 * <p>In production, tenants are created through the admin API, not seeded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final FlywayMigrationRunner flywayMigrationRunner;

    @Value("${lms.tenant.default-slug:demo}")
    private String defaultTenantSlug;

    @Value("${lms.demo.admin-email:admin@lms.demo}")
    private String adminEmail;

    @Value("${lms.demo.admin-keycloak-id:demo-admin-id}")
    private String adminKeycloakId;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void seed() {
        log.info("[SEEDER] Starting idempotent seed...");
        Tenant tenant = seedDemoTenant();
        if (tenant != null) {
            seedDemoAdmin(tenant);
        }
        log.info("[SEEDER] Seed complete.");
    }

    private Tenant seedDemoTenant() {
        if (tenantRepository.existsBySubdomain(defaultTenantSlug)) {
            log.info("[SEEDER] Tenant '{}' already exists. Skipping.", defaultTenantSlug);
            return tenantRepository.findBySubdomain(defaultTenantSlug).orElse(null);
        }

        String schemaName = "tenant_" + defaultTenantSlug;

        Tenant tenant = Tenant.builder()
                .name("Demo LMS")
                .subdomain(defaultTenantSlug)          // slug only — e.g. "demo", not "demo.lms.local"
                .realmName("lms-demo")
                .schemaName(schemaName)
                .active(true)
                .build();

        tenantRepository.save(tenant);
        log.info("[SEEDER] Created tenant subdomain='{}' schema='{}'", defaultTenantSlug, schemaName);

        // Run V2 migrations for this new tenant schema
        try {
            flywayMigrationRunner.migrateTenantSchema(schemaName);
        } catch (Exception e) {
            log.error("[SEEDER] Failed to migrate tenant schema '{}': {}", schemaName, e.getMessage());
        }

        return tenant;
    }

    private void seedDemoAdmin(Tenant tenant) {
        TenantContext.setCurrentTenant(tenant.getSchemaName());
        try {
            if (userRepository.existsByEmail(adminEmail)) {
                log.info("[SEEDER] Admin user '{}' already exists. Skipping.", adminEmail);
                return;
            }

            User admin = User.builder()
                    .keycloakId(adminKeycloakId)
                    .email(adminEmail)
                    .firstName("LMS")
                    .lastName("Admin")
                    .role(User.UserRole.ADMIN)
                    .active(true)
                    .build();

            userRepository.save(admin);
            log.info("[SEEDER] Created demo admin user email='{}'", adminEmail);
        } catch (Exception e) {
            log.warn("[SEEDER] Could not seed admin user: {}", e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
