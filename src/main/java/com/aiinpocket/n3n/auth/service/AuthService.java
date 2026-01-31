package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.auth.dto.request.LoginRequest;
import com.aiinpocket.n3n.auth.dto.request.RegisterRequest;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.entity.RefreshToken;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.exception.*;
import com.aiinpocket.n3n.auth.repository.RefreshTokenRepository;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.max-login-attempts}")
    private int maxLoginAttempts;

    @Value("${app.lock-duration-minutes}")
    private int lockDurationMinutes;

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        validateUserStatus(user);
        checkAccountLock(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset login attempts on successful login
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateAuthResponse(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Check if this is the first user (will become admin)
        boolean isFirstUser = userRepository.count() == 0;

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .name(request.getName())
            .status("active") // For simplicity, auto-activate. In production, use email verification.
            .emailVerified(true)
            .loginAttempts(0)
            .build();

        user = userRepository.save(user);

        // Assign roles - first user gets ADMIN + USER
        List<String> roles;
        if (isFirstUser) {
            UserRole adminRole = UserRole.builder()
                .userId(user.getId())
                .role("ADMIN")
                .build();
            userRoleRepository.save(adminRole);

            UserRole userRole = UserRole.builder()
                .userId(user.getId())
                .role("USER")
                .build();
            userRoleRepository.save(userRole);

            roles = List.of("ADMIN", "USER");
            log.info("First user registered as admin: {}", user.getEmail());
        } else {
            UserRole role = UserRole.builder()
                .userId(user.getId())
                .role("USER")
                .build();
            userRoleRepository.save(role);

            roles = List.of("USER");
            log.info("User registered: {}", user.getEmail());
        }

        return AuthResponse.builder()
            .message(isFirstUser ? "Admin account created successfully" : "Registration successful")
            .user(UserResponse.from(user, roles))
            .build();
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken, String ipAddress, String userAgent) {
        String tokenHash = jwtService.hashRefreshToken(refreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException("Refresh token expired");
        }

        User user = userRepository.findById(storedToken.getUserId())
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        validateUserStatus(user);

        // Revoke old token (Token Rotation)
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        return generateAuthResponse(user, ipAddress, userAgent);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null) {
            String tokenHash = jwtService.hashRefreshToken(refreshToken);
            refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
        }
    }

    public UserResponse getCurrentUser(String userId) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<String> roles = userRoleRepository.findByUserId(user.getId())
            .stream()
            .map(UserRole::getRole)
            .toList();

        return UserResponse.from(user, roles);
    }

    /**
     * Check if initial setup is needed (no users exist)
     */
    public boolean isSetupRequired() {
        return userRepository.count() == 0;
    }

    private AuthResponse generateAuthResponse(User user, String ipAddress, String userAgent) {
        List<String> roles = userRoleRepository.findByUserId(user.getId())
            .stream()
            .map(UserRole::getRole)
            .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getName(), roles);
        String refreshToken = jwtService.generateRefreshToken();

        saveRefreshToken(user, refreshToken, ipAddress, userAgent);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
            .user(UserResponse.from(user, roles))
            .build();
    }

    private void validateUserStatus(User user) {
        switch (user.getStatus()) {
            case "pending" -> throw new EmailNotVerifiedException("Please verify your email first");
            case "suspended" -> throw new AccountSuspendedException("Account has been suspended");
            case "deleted" -> throw new BadCredentialsException("Invalid email or password");
        }
    }

    private void checkAccountLock(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException("Account is locked. Try again later.");
        }
    }

    private void handleFailedLogin(User user) {
        user.setLoginAttempts(user.getLoginAttempts() + 1);

        if (user.getLoginAttempts() >= maxLoginAttempts) {
            user.setLockedUntil(Instant.now().plus(lockDurationMinutes, ChronoUnit.MINUTES));
            log.warn("Account locked due to too many failed attempts: {}", user.getEmail());
        }

        userRepository.save(user);
    }

    private void saveRefreshToken(User user, String refreshToken, String ipAddress, String userAgent) {
        RefreshToken token = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(jwtService.hashRefreshToken(refreshToken))
            .deviceInfo(userAgent)
            .ipAddress(ipAddress)
            .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .build();

        refreshTokenRepository.save(token);
    }
}
