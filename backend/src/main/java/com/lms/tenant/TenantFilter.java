package com.lms.tenant;

import com.lms.common.exception.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the current tenant from the request's Host header subdomain.
 * e.g. demo.lms.com → TenantContext is set to "tenant_demo"
 *
 * Public endpoints (like /public/verify) use "public" schema.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String subdomain = extractSubdomain(request);

            if (subdomain == null || subdomain.isBlank()) {
                // Shared / super-admin context
                TenantContext.setCurrentTenant("public");
            } else {
                String schemaName = tenantRepository.findSchemaBySubdomain(subdomain)
                        .orElseThrow(() -> new TenantNotFoundException(subdomain));
                TenantContext.setCurrentTenant(schemaName);
            }

            // Set MDC for structured logging
            org.slf4j.MDC.put("tenantId", TenantContext.getCurrentTenant());

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
            org.slf4j.MDC.remove("tenantId");
            org.slf4j.MDC.remove("userId");
        }
    }

    private String extractSubdomain(HttpServletRequest request) {
        // Check explicit header first (set by Nginx for subdomain routing)
        String tenantHeader = request.getHeader("X-Tenant-ID");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return tenantHeader;
        }

        // Fall back to Host header parsing
        String host = request.getServerName(); // e.g. demo.lms.com
        if (host == null) return null;

        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            return parts[0]; // subdomain
        }
        return null;
    }
}
