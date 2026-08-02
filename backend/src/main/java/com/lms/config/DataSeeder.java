package com.lms.config;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.tenant.Tenant;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the default demo tenant and demo users on startup.
 *
 * <p><strong>Ordering:</strong> {@code @Order(2)} runs after {@link FlywayMigrationRunner}
 * ({@code @Order(1)}). Both implement {@link InitializingBean}.
 *
 * <p><strong>Transaction safety:</strong> This class does NOT use a class-level
 * {@code @Transactional} because it switches {@code TenantContext} mid-execution.
 * Each database operation is wrapped in its own {@link TransactionTemplate} call
 * that opens a fresh connection AFTER setting the tenant schema, ensuring Hibernate
 * binds the correct schema for that session.
 *
 * <p>Idempotent — skips if data already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class DataSeeder implements InitializingBean {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final FlywayMigrationRunner flywayMigrationRunner;
    private final TransactionTemplate transactionTemplate;

    @Value("${lms.tenant.default-slug:demo}")
    private String defaultTenantSlug;

    @Override
    public void afterPropertiesSet() {
        log.info("[SEEDER] Starting idempotent seed...");
        Tenant tenant = seedDemoTenant();
        if (tenant != null) {
            seedDemoUsers(tenant);
        }
        log.info("[SEEDER] Seed complete.");
    }

    // ─── Tenant ───────────────────────────────────────────────────────────────

    private Tenant seedDemoTenant() {
        // Check + create in a single transaction (public schema — no tenant context needed)
        return transactionTemplate.execute(status -> {
            if (tenantRepository.existsBySubdomain(defaultTenantSlug)) {
                log.info("[SEEDER] Tenant '{}' already exists. Skipping.", defaultTenantSlug);
                return tenantRepository.findBySubdomain(defaultTenantSlug).orElse(null);
            }

            String schemaName = "tenant_" + defaultTenantSlug;
            Tenant tenant = Tenant.builder()
                    .name("Demo LMS")
                    .subdomain(defaultTenantSlug)
                    .realmName("lms-demo")
                    .schemaName(schemaName)
                    .active(true)
                    .build();

            tenantRepository.save(tenant);
            log.info("[SEEDER] Created tenant subdomain='{}' schema='{}'", defaultTenantSlug, schemaName);
            return tenant;
        });
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    /**
     * Seeds demo users in the tenant schema.
     * Each call sets TenantContext BEFORE opening a transaction so Hibernate
     * routes to the correct schema. Context is always cleared in finally.
     */
    private void seedDemoUsers(Tenant tenant) {
        String schemaName = tenant.getSchemaName();

        seedUser(schemaName, "student@demo.com",    "Demo",  "Student",    User.UserRole.STUDENT);
        seedUser(schemaName, "instructor@demo.com", "Demo",  "Instructor", User.UserRole.INSTRUCTOR);
        seedUser(schemaName, "admin@demo.com",      "LMS",   "Admin",      User.UserRole.ADMIN);
    }

    private void seedUser(String schemaName, String email,
                          String firstName, String lastName, User.UserRole role) {
        TenantContext.setCurrentTenant(schemaName);
        try {
            transactionTemplate.execute(status -> {
                if (userRepository.existsByEmail(email)) {
                    log.debug("[SEEDER] User '{}' already exists.", email);
                    return null;
                }

                // keycloakId is intentionally null — it is filled on first login
                // by CurrentUserService.resolveOrProvision().
                User user = User.builder()
                        .keycloakId(null)
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName)
                        .role(role)
                        .active(true)
                        .build();

                userRepository.save(user);
                log.info("[SEEDER] Created demo user email='{}' role={}", email, role);
                return null;
            });
        } catch (Exception e) {
            log.warn("[SEEDER] Could not seed user '{}': {}", email, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
