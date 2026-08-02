package com.lms.security;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Loads users for the Spring Authorization Server login form.
 *
 * <p>Searches across ALL active tenants for a user with the given email address.
 * This is only called during the authorization code flow login (interactive browser login),
 * never during API request authentication.
 *
 * <p>Demo users are seeded with a default password by {@link com.lms.config.DataSeeder}.
 * Since we store users without passwords (Keycloak historically handled auth),
 * we use a convention: the password for demo users is {@code Demo1234!}.
 * Production deployments should seed proper BCrypt hashes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoUserDetailsService implements UserDetailsService {

    private static final String DEMO_DEFAULT_PASSWORD = "Demo1234!";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("[AUTH] loadUserByUsername: {}", username);

        // Search across all active tenants for this email
        for (var tenant : tenantRepository.findAll()) {
            if (!tenant.isActive()) continue;
            TenantContext.setCurrentTenant(tenant.getSchemaName());
            try {
                var optUser = userRepository.findByEmail(username);
                if (optUser.isPresent()) {
                    User user = optUser.get();
                    log.debug("[AUTH] Found user {} in tenant {}", username, tenant.getSchemaName());
                    // Demo users: no stored password — use Demo1234! as the seeded credential
                    String encoded = passwordEncoder.encode(DEMO_DEFAULT_PASSWORD);
                    return new DemoUserPrincipal(
                            user.getEmail(),
                            encoded,
                            user.getFirstName(),
                            user.getLastName(),
                            user.getRole(),
                            user.isActive()
                    );
                }
            } finally {
                TenantContext.clear();
            }
        }

        throw new UsernameNotFoundException("User not found: " + username);
    }
}
