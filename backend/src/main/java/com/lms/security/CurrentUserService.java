package com.lms.security;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Resolves the currently authenticated user from a Keycloak JWT.
 *
 * <p>On every first login, provisions a User row keyed by {@code keycloak_id = jwt.sub}.
 * Subsequent calls look up the existing row. The app-level UUID ({@code user.id}) is
 * used everywhere as the internal primary key — NOT the Keycloak subject directly.
 *
 * <p>Use this in controllers instead of {@code UUID.fromString(jwt.getSubject())}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Resolves the app {@link User} for the given JWT, creating the row on first login.
     *
     * @param jwt the validated JWT from Spring Security context
     * @return the persisted {@link User} entity with app-level UUID primary key
     */
    @Transactional
    public User resolveOrProvision(Jwt jwt) {
        String subjectId = jwt.getSubject();

        return userRepository.findByKeycloakId(subjectId)
                .orElseGet(() -> {
                    // Extract claims
                    String email = jwt.getClaimAsString("email");
                    if (email == null) {
                        email = subjectId + "@unknown.invalid";
                    }

                    // Fallback to lookup by email (for seeded users or migrated users)
                    var existingUser = userRepository.findByEmail(email);
                    if (existingUser.isPresent()) {
                        User user = existingUser.get();
                        log.info("[AUTH] Linking existing user email={} to subjectId={}", email, subjectId);
                        user.setKeycloakId(subjectId);
                        return userRepository.save(user);
                    }

                    log.info("[AUTH] First login for subjectId={}. Provisioning app user.", subjectId);
                    String firstName = firstOrFallback(jwt.getClaimAsString("given_name"),
                            jwt.getClaimAsString("preferred_username"), "User");
                    String lastName = firstOrFallback(jwt.getClaimAsString("family_name"), "");

                    // Determine role from realm roles claim
                    User.UserRole role = resolveRole(jwt);

                    User user = User.builder()
                            .keycloakId(subjectId)
                            .email(email)
                            .firstName(firstName)
                            .lastName(lastName)
                            .role(role)
                            .active(true)
                            .build();

                    User saved = userRepository.save(user);
                    log.info("[AUTH] Provisioned user id={} role={} email={}", saved.getId(), role, email);
                    return saved;
                });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private User.UserRole resolveRole(Jwt jwt) {
        // Keycloak puts realm roles in realm_access.roles claim
        try {
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                @SuppressWarnings("unchecked")
                var roles = (java.util.List<String>) realmAccess.get("roles");
                if (roles != null) {
                    if (roles.contains("ADMIN"))        return User.UserRole.ADMIN;
                    if (roles.contains("INSTRUCTOR"))   return User.UserRole.INSTRUCTOR;
                    if (roles.contains("SUPER_ADMIN"))  return User.UserRole.SUPER_ADMIN;
                }
            }
        } catch (Exception e) {
            log.debug("[AUTH] Could not read realm roles from JWT: {}", e.getMessage());
        }
        return User.UserRole.STUDENT; // safe default
    }

    private String firstOrFallback(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return "";
    }
}
