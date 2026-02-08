package com.aiinpocket.n3n.admin.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.admin.dto.CreateUserRequest;
import com.aiinpocket.n3n.admin.dto.UserResponse;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Set.of;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private static final Set<String> VALID_STATUSES = Set.of("active", "suspended", "deleted");
    private static final Set<String> VALID_ROLES = Set.of("USER", "ADMIN");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityService activityService;
    private final EmailService emailService;

    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(user -> {
                Set<String> roles = userRoleRepository.findByUserId(user.getId())
                    .stream()
                    .map(UserRole::getRole)
                    .collect(Collectors.toSet());
                return UserResponse.from(user, roles);
            });
    }

    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        Set<String> roles = userRoleRepository.findByUserId(user.getId())
            .stream()
            .map(UserRole::getRole)
            .collect(Collectors.toSet());

        return UserResponse.from(user, roles);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, UUID adminId) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Generate password if not provided
        String password = request.getPassword();
        if (password == null || password.isEmpty()) {
            password = generateRandomPassword();
        }

        // Create user
        User user = User.builder()
            .email(request.getEmail())
            .name(request.getName())
            .passwordHash(passwordEncoder.encode(password))
            .status("active")
            .emailVerified(true) // Admin-created users are pre-verified
            .build();

        user = userRepository.save(user);

        // Validate and assign roles
        Set<String> roles = request.getRoles();
        if (roles == null || roles.isEmpty()) {
            roles = Set.of("USER");
        }
        validateRoles(roles);
        for (String role : roles) {
            UserRole userRole = UserRole.builder()
                .userId(user.getId())
                .role(role.toUpperCase())
                .build();
            userRoleRepository.save(userRole);
        }

        log.info("Admin {} created user: {}", adminId, user.getEmail());
        activityService.logActivity(adminId, ActivityService.USER_CREATE, "user", user.getId(), user.getEmail(), null);

        // Send invite email with temporary password
        emailService.sendUserInvitation(user.getEmail(), user.getName(), password);

        return UserResponse.from(user, request.getRoles());
    }

    @Transactional
    public UserResponse updateUserStatus(UUID id, String status, UUID adminId) {
        if (id.equals(adminId)) {
            throw new IllegalArgumentException("Cannot change your own account status");
        }
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Must be one of: " + VALID_STATUSES);
        }

        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Prevent suspending/blocking the last active admin
        if (!"active".equals(status)) {
            boolean targetIsAdmin = userRoleRepository.findByUserId(id).stream()
                .anyMatch(r -> "ADMIN".equals(r.getRole()));
            if (targetIsAdmin) {
                long activeAdminCount = countActiveAdmins();
                if (activeAdminCount <= 1) {
                    throw new IllegalArgumentException("Cannot deactivate the last admin user");
                }
            }
        }

        // Clear login attempts when reactivating
        if ("active".equals(status)) {
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
        }

        user.setStatus(status);
        user = userRepository.save(user);

        Set<String> roles = userRoleRepository.findByUserId(user.getId())
            .stream()
            .map(UserRole::getRole)
            .collect(Collectors.toSet());

        log.info("Admin {} updated user {} status to: {}", adminId, id, status);
        activityService.logActivity(adminId, ActivityService.USER_UPDATE, "user", id, user.getEmail(),
            java.util.Map.of("status", status));

        return UserResponse.from(user, roles);
    }

    @Transactional
    public UserResponse updateUserRoles(UUID id, Set<String> newRoles, UUID adminId) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new IllegalArgumentException("Roles cannot be empty");
        }
        validateRoles(newRoles);

        if (id.equals(adminId) && !newRoles.stream().map(String::toUpperCase).collect(Collectors.toSet()).contains("ADMIN")) {
            throw new IllegalArgumentException("Cannot remove ADMIN role from your own account");
        }

        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Remove existing roles
        userRoleRepository.deleteByUserId(id);

        // Add new roles
        for (String role : newRoles) {
            UserRole userRole = UserRole.builder()
                .userId(id)
                .role(role.toUpperCase())
                .build();
            userRoleRepository.save(userRole);
        }

        log.info("Admin {} updated user {} roles to: {}", adminId, id, newRoles);
        activityService.logActivity(adminId, ActivityService.USER_UPDATE, "user", id, user.getEmail(),
            java.util.Map.of("roles", newRoles));

        return UserResponse.from(user, newRoles);
    }

    @Transactional
    public void resetUserPassword(UUID id, UUID adminId) {
        if (id.equals(adminId)) {
            throw new IllegalArgumentException("Cannot reset your own password via admin endpoint. Use the change password feature instead.");
        }

        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        String newPassword = generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Admin {} reset password for user: {}", adminId, id);

        // Send password reset email
        emailService.sendPasswordReset(user.getEmail(), newPassword);
    }

    private void validateRoles(Set<String> roles) {
        for (String role : roles) {
            if (!VALID_ROLES.contains(role.toUpperCase())) {
                throw new IllegalArgumentException("Invalid role: " + role + ". Must be one of: " + VALID_ROLES);
            }
        }
    }

    private long countActiveAdmins() {
        return userRoleRepository.findByRole("ADMIN").stream()
            .map(UserRole::getUserId)
            .distinct()
            .filter(userId -> userRepository.findById(userId)
                .map(u -> "active".equals(u.getStatus()))
                .orElse(false))
            .count();
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
