package com.lms.security;

import com.lms.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts Keycloak JWT claims into Spring Security authorities.
 * Extracts roles from realm_access.roles and resource_access.{client}.roles.
 * Also sets the MDC userId from the JWT subject.
 */
@Component
@Slf4j
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String TENANT_ID_CLAIM = "tenant_id";
    private static final String CLIENT_ID = "lms-backend";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        // Set tenant from JWT claim if available
        String tenantId = jwt.getClaimAsString(TENANT_ID_CLAIM);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setCurrentTenant(tenantId);
        }

        // Set MDC userId
        org.slf4j.MDC.put("userId", jwt.getSubject());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extract realm-level roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        List<String> realmRoles = realmAccess != null
                ? (List<String>) realmAccess.getOrDefault(ROLES_CLAIM, Collections.emptyList())
                : Collections.emptyList();

        // Extract client-level roles
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        List<String> clientRoles = Collections.emptyList();
        if (resourceAccess != null && resourceAccess.containsKey(CLIENT_ID)) {
            Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(CLIENT_ID);
            clientRoles = (List<String>) clientAccess.getOrDefault(ROLES_CLAIM, Collections.emptyList());
        }

        return java.util.stream.Stream.concat(realmRoles.stream(), clientRoles.stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
    }
}
