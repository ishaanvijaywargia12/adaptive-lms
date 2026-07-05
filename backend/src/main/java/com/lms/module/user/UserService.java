package com.lms.module.user;

import com.lms.common.exception.ResourceNotFoundException;
import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Page<User> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public User getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + keycloakId));
    }

    @Transactional
    public User updateRole(UUID id, String roleName) {
        User user = getUserById(id);
        user.setRole(User.UserRole.valueOf(roleName.toUpperCase()));
        return userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = getUserById(id);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public User ensureUserExists(String keycloakId, String email, String firstName, String lastName) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = User.builder()
                    .keycloakId(keycloakId)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(User.UserRole.STUDENT)
                    .active(true)
                    .build();
            return userRepository.save(user);
        });
    }
}
