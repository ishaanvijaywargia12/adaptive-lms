package com.lms.config;

import com.lms.security.DemoUserDetailsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Spring Authorization Server configuration.
 *
 * <p>This provides a fully self-hosted OAuth2/OIDC identity provider running
 * inside the same Spring Boot process. No external Keycloak or Auth0 required.
 *
 * <p>Issues JWTs at:
 * <ul>
 *   <li>{@code /oauth2/authorize} — PKCE authorization endpoint</li>
 *   <li>{@code /oauth2/token} — token endpoint</li>
 *   <li>{@code /oauth2/jwks} — JWK Set (used by resource server to verify tokens)</li>
 *   <li>{@code /userinfo} — OIDC UserInfo endpoint</li>
 *   <li>{@code /login} — form-based login page</li>
 * </ul>
 *
 * <p>Supported scopes: {@code openid profile email roles}
 * <p>Client: {@code lms-frontend} with PKCE, no client secret.
 */
@Configuration
@Slf4j
public class AuthServerConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String corsAllowedOrigins;

    // ─── Auth Server Security Filter Chain (highest priority) ─────────────────

    @Bean
    @Order(1)
    public SecurityFilterChain authServerFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults()); // Enable OIDC UserInfo endpoint

        http
                // Redirect to login when unauthenticated at /oauth2/authorize
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));

        return http.build();
    }

    // ─── Registered Client: lms-frontend (PKCE, public, no secret) ────────────

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        // Build redirect URIs: GitHub Pages + localhost dev
        List<String> origins = List.of(corsAllowedOrigins.split(","));

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("lms-frontend")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public client (PKCE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("roles")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)                // Enforce PKCE
                        .requireAuthorizationConsent(false)   // No consent screen
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build());

        // Explicit redirect URIs
        builder.redirectUri("https://ishaanvijaywargia12.github.io/adaptive-lms/callback");
        builder.redirectUri("https://ishaanvijaywargia12.github.io/adaptive-lms/silent-check-sso.html");
        
        // Dev fallbacks
        builder.redirectUri("http://localhost:5173/callback");
        builder.redirectUri("http://localhost:5173/silent-check-sso.html");
        builder.redirectUri("http://localhost:3000/callback");

        RegisteredClient client = builder.build();
        
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);
        if (repository.findByClientId(client.getClientId()) == null) {
            log.info("[AUTH] Registered OAuth2 client '{}' with {} redirect URIs into DB",
                    client.getClientId(), client.getRedirectUris().size());
            repository.save(client);
        } else {
            log.info("[AUTH] OAuth2 client '{}' already exists in DB", client.getClientId());
        }
        return repository;
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    // ─── JWT Token Customizer — inject email, name, roles into token ──────────

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(
            DemoUserDetailsService userDetailsService) {
        return context -> {
            String tokenType = context.getTokenType().getValue();
            if (tokenType.equals("access_token") || tokenType.equals("id_token")) {
                Authentication principal = context.getPrincipal();
                String username = principal.getName();

                // Load user details to embed claims
                try {
                    var userDetails = (com.lms.security.DemoUserPrincipal)
                            userDetailsService.loadUserByUsername(username);

                    context.getClaims()
                            .claim("email", userDetails.getEmail())
                            .claim("given_name", userDetails.getFirstName())
                            .claim("family_name", userDetails.getLastName())
                            .claim("roles", List.of(userDetails.getRole().name()))
                            // realm_access.roles for backward compatibility with CurrentUserService
                            .claim("realm_access", java.util.Map.of(
                                    "roles", List.of(userDetails.getRole().name())
                            ));
                } catch (Exception e) {
                    log.debug("[AUTH] Could not enrich JWT claims for {}: {}", username, e.getMessage());
                }
            }
        };
    }

    // ─── RSA Key for JWT Signing ───────────────────────────────────────────────
    // In production: provide APP_JWT_PRIVATE_KEY and APP_JWT_PUBLIC_KEY as PEM strings.
    // In local dev: keys are generated ephemerally (tokens invalidate on restart).

    @Value("${app.jwt.private-key:}")
    private String privateKeyPem;

    @Value("${app.jwt.public-key:}")
    private String publicKeyPem;

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = loadOrGenerateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder authServerJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(appBaseUrl)
                .build();
    }

    // ─── Password Encoder (BCrypt) ────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ─── RSA Key loading helpers ──────────────────────────────────────────────

    private RSAKey loadOrGenerateRsa() {
        if (privateKeyPem != null && !privateKeyPem.isBlank()
                && publicKeyPem != null && !publicKeyPem.isBlank()) {
            try {
                RSAPrivateKey privateKey = loadPrivateKey(privateKeyPem);
                RSAPublicKey publicKey = loadPublicKey(publicKeyPem);
                log.info("[AUTH] Loaded persistent RSA signing key from environment variables.");
                return new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID("lms-persistent-key")
                        .build();
            } catch (Exception e) {
                log.error("[AUTH] Failed to load RSA key from env vars — falling back to ephemeral key: {}", e.getMessage());
            }
        }
        log.warn("[AUTH] APP_JWT_PRIVATE_KEY not set — generating ephemeral RSA key. Tokens will be invalidated on restart.");
        return generateEphemeralRsa();
    }

    private static RSAPrivateKey loadPrivateKey(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = java.util.Base64.getDecoder().decode(cleaned);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(decoded));
    }

    private static RSAPublicKey loadPublicKey(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = java.util.Base64.getDecoder().decode(cleaned);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(new java.security.spec.X509EncodedKeySpec(decoded));
    }

    private static RSAKey generateEphemeralRsa() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ephemeral RSA key for JWT signing", e);
        }
    }
}
