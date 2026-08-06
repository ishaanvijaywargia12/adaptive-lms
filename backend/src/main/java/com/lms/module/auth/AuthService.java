package com.lms.module.auth;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.security.CurrentUserService;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public Map<String, Object> getCurrentUserProfile(Jwt jwt) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", jwt.getSubject());
        profile.put("email", jwt.getClaim("email"));
        profile.put("firstName", jwt.getClaim("given_name"));
        profile.put("lastName", jwt.getClaim("family_name"));
        profile.put("username", jwt.getClaim("email"));

        // Extract roles from realm_access (as set by jwtCustomizer in AuthServerConfig)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            profile.put("roles", realmAccess.get("roles"));
        } else {
            profile.put("roles", List.of());
        }
        return profile;
    }

    public Map<String, Object> updateProfile(Jwt jwt, Map<String, String> updates) {
        Map<String, Object> profile = getCurrentUserProfile(jwt);
        if (updates.containsKey("firstName")) profile.put("firstName", updates.get("firstName"));
        if (updates.containsKey("lastName")) profile.put("lastName", updates.get("lastName"));
        log.info("Profile update requested for user {}", jwt.getSubject());
        return profile;
    }

    /**
     * Changes the user's password by updating the BCrypt hash in the DB.
     * The Spring Authorization Server reads passwords from DemoUserDetailsService,
     * which loads passwordHash from this table.
     */
    public void changePassword(String userEmail, String newPassword) {
        // Find user across all active tenants
        for (var tenant : tenantRepository.findAll()) {
            if (!tenant.isActive()) continue;
            TenantContext.setCurrentTenant(tenant.getSchemaName());
            try {
                var optUser = userRepository.findByEmail(userEmail);
                if (optUser.isPresent()) {
                    transactionTemplate.execute(status -> {
                        User user = optUser.get();
                        user.setPasswordHash(passwordEncoder.encode(newPassword));
                        userRepository.save(user);
                        return null;
                    });
                    log.info("[AUTH] Password changed for user {}", userEmail);
                    return;
                }
            } finally {
                TenantContext.clear();
            }
        }
        throw new RuntimeException("User not found: " + userEmail);
    }

    /**
     * Logout is stateless — the client discards the JWT.
     * For true revocation a token denylist (Redis) would be needed.
     */
    public void logout(String userId) {
        log.info("[AUTH] Logout recorded for subject {}", userId);
        // Stateless — JWT expiry is the revocation mechanism.
        // Extend this with Redis denylist if needed.
    }
}
