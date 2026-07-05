package com.lms.config;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.tenant.Tenant;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting database seeding process...");
        seedTenants();
        log.info("Database seeding completed.");
    }

    private void seedTenants() {
        if (tenantRepository.count() > 0) {
            log.info("Tenants already exist. Skipping tenant seeding.");
            return;
        }

        Tenant techCorp = Tenant.builder()
                .id(UUID.randomUUID())
                .name("TechCorp")
                .subdomain("techcorp.lms.local")
                .realmName("lms-demo")
                .schemaName("tenant_techcorp")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        Tenant medSchool = Tenant.builder()
                .id(UUID.randomUUID())
                .name("MedSchool")
                .subdomain("medschool.lms.local")
                .realmName("lms-demo")
                .schemaName("tenant_medschool")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        tenantRepository.save(techCorp);
        tenantRepository.save(medSchool);

        log.info("Seeded tenants: TechCorp and MedSchool.");

        // We would also ideally trigger Flyway/Hibernate schema generation for these schemas here
        // and seed dummy users/instructors using TenantContext in a real setting.
        try {
            TenantContext.setCurrentTenant(techCorp.getSchemaName());
            User student1 = User.builder()
                    .keycloakId(UUID.randomUUID().toString())
                    .email("student1@techcorp.com")
                    .firstName("Tech")
                    .lastName("Student")
                    .role(User.UserRole.STUDENT)
                    .build();
            userRepository.save(student1);
            log.info("Seeded dummy student for TechCorp.");
        } catch (Exception e) {
            log.warn("Could not seed users inside tenant schema (schema might not be initialized yet): {}", e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
