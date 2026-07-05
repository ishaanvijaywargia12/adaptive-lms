package com.lms.module.auth;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.admin.client-id}")
    private String adminClientId;

    @Value("${keycloak.admin.realm}")
    private String adminRealm;

    public Map<String, Object> getCurrentUserProfile(Jwt jwt) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", jwt.getSubject());
        profile.put("email", jwt.getClaim("email"));
        profile.put("firstName", jwt.getClaim("given_name"));
        profile.put("lastName", jwt.getClaim("family_name"));
        profile.put("username", jwt.getClaim("preferred_username"));

        // Extract roles from realm_access
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            profile.put("roles", realmAccess.get("roles"));
        } else {
            profile.put("roles", List.of());
        }
        return profile;
    }

    public Map<String, Object> updateProfile(Jwt jwt, Map<String, String> updates) {
        // In a production system, this would call Keycloak Admin API to update the user
        // For now, return the updated profile merged with JWT claims
        Map<String, Object> profile = getCurrentUserProfile(jwt);
        if (updates.containsKey("firstName")) profile.put("firstName", updates.get("firstName"));
        if (updates.containsKey("lastName")) profile.put("lastName", updates.get("lastName"));
        log.info("Profile update requested for user {}", jwt.getSubject());
        return profile;
    }

    public void changePassword(String userId, String newPassword) {
        try {
            Keycloak keycloak = buildAdminKeycloak();
            // Get tenant realm from thread context - default to 'lms-demo' for now
            String realm = "lms-demo";
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);
            keycloak.realm(realm).users().get(userId).resetPassword(credential);
            log.info("Password changed for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to change password for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Password change failed: " + e.getMessage());
        }
    }

    public void logout(String userId) {
        try {
            Keycloak keycloak = buildAdminKeycloak();
            String realm = "lms-demo";
            keycloak.realm(realm).users().get(userId).logout();
            log.info("User {} logged out", userId);
        } catch (Exception e) {
            log.warn("Failed to invalidate Keycloak session for user {}: {}", userId, e.getMessage());
            // Best-effort logout — don't fail the request
        }
    }

    private Keycloak buildAdminKeycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakUrl)
                .realm(adminRealm)
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }
}
