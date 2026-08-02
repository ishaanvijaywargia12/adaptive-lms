package com.lms.tenant;

import com.lms.common.exception.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the current tenant from the incoming HTTP request.
 *
 * <p>Resolution order (first match wins):
 * <ol>
 *   <li>{@code X-Tenant-ID} header — slug value (e.g. "demo")</li>
 *   <li>Host subdomain — e.g. {@code demo.lms.com} → slug "demo"</li>
 *   <li>Environment variable {@code LMS_DEFAULT_TENANT} — for single-tenant demo deployments</li>
 *   <li>Falls back to "public" schema (super-admin / unauthenticated)</li>
 * </ol>
 *
 * <p>Tenant slug is looked up in {@code public.tenants.subdomain} to get the schema name.
 * An inactive tenant resolves to a 403 error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    /**
     * Default tenant slug for single-tenant deployments.
     * Set via {@code LMS_DEFAULT_TENANT} env var (or {@code lms.tenant.default-slug} property).
     */
    @Value("${lms.tenant.default-slug:}")
    private String defaultTenantSlug;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String slug = resolveSlug(request);

            if (slug == null || slug.isBlank()) {
                // No tenant context — use public schema (super-admin / Actuator / Swagger)
                TenantContext.setCurrentTenant("public");
            } else {
                Tenant tenant = tenantRepository.findBySubdomain(slug)
                        .orElseThrow(() -> new TenantNotFoundException("Tenant not found for slug: " + slug));

                if (!tenant.isActive()) {
                    log.warn("[TENANT] Request for inactive tenant slug='{}'", slug);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is inactive");
                    return;
                }

                TenantContext.setCurrentTenant(tenant.getSchemaName());
                log.debug("[TENANT] Resolved slug='{}' → schema='{}'", slug, tenant.getSchemaName());
            }

            MDC.put("tenantId", TenantContext.getCurrentTenant());
            filterChain.doFilter(request, response);

        } catch (TenantNotFoundException e) {
            log.warn("[TENANT] {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    /**
     * Extracts the tenant slug from the request.
     * Slug is the short identifier (e.g. "demo"), NOT the full subdomain.
     */
    private String resolveSlug(HttpServletRequest request) {
        // 1. Explicit header (set by frontend, Nginx, or API gateway)
        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) {
            return header.trim().toLowerCase();
        }

        // 2. Subdomain from Host header
        String host = request.getServerName();
        if (host != null) {
            if (host.endsWith(".onrender.com")) {
                // Ignore the onrender.com base domain so it falls through to the default tenant
                log.trace("Ignoring onrender.com host for tenant resolution: {}", host);
            } else {
                String[] parts = host.split("\\.");
                if (parts.length >= 3) {
                    // host = "demo.lms.com" → slug = "demo"
                    return parts[0].toLowerCase();
                }
            }
        }

        // 3. Default tenant for single-tenant/demo deployments
        if (defaultTenantSlug != null && !defaultTenantSlug.isBlank()) {
            return defaultTenantSlug.toLowerCase();
        }

        return null;
    }
}
