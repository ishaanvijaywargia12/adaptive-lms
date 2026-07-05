package com.lms.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySubdomain(String subdomain);

    Optional<Tenant> findBySchemaName(String schemaName);

    @Query("SELECT t.schemaName FROM Tenant t WHERE t.subdomain = :subdomain AND t.active = true")
    Optional<String> findSchemaBySubdomain(String subdomain);

    boolean existsBySubdomain(String subdomain);
}
