package com.lms.security;

import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    @Mock
    private Jwt jwt;

    private static final String KEYCLOAK_SUB = "kc-user-123-abc";

    @BeforeEach
    void setUp() {
        lenient().when(jwt.getSubject()).thenReturn(KEYCLOAK_SUB);
    }

    @Test
    @DisplayName("resolveOrProvision: returns existing user when found by Keycloak ID")
    void returnsExistingUserWhenFound() {
        UUID dbId = UUID.randomUUID();
        User existingUser = User.builder()
                .keycloakId(KEYCLOAK_SUB)
                .email("student@lms.com")
                .firstName("Test")
                .lastName("Student")
                .role(User.UserRole.STUDENT)
                .build();
        existingUser.setId(dbId);

        when(userRepository.findByKeycloakId(KEYCLOAK_SUB)).thenReturn(Optional.of(existingUser));

        User result = currentUserService.resolveOrProvision(jwt);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(dbId);
        assertThat(result.getEmail()).isEqualTo("student@lms.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolveOrProvision: provisions new user on first login with claims and role")
    void provisionsNewUserOnFirstLogin() {
        when(userRepository.findByKeycloakId(KEYCLOAK_SUB)).thenReturn(Optional.empty());
        lenient().when(jwt.getClaimAsString("email")).thenReturn("instructor@lms.com");
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn("Alice");
        lenient().when(jwt.getClaimAsString("preferred_username")).thenReturn("alice");
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn("Instructor");
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of("INSTRUCTOR")));

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(generatedId);
            return u;
        });

        User result = currentUserService.resolveOrProvision(jwt);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(generatedId);
        assertThat(result.getKeycloakId()).isEqualTo(KEYCLOAK_SUB);
        assertThat(result.getEmail()).isEqualTo("instructor@lms.com");
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Instructor");
        assertThat(result.getRole()).isEqualTo(User.UserRole.INSTRUCTOR);

        verify(userRepository, times(1)).save(any(User.class));
    }
}
