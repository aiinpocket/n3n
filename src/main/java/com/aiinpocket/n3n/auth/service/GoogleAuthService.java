package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.exception.BadCredentialsException;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import com.aiinpocket.n3n.auth.service.GoogleTokenVerifier.GoogleTokenInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Google Sign-In：驗證 Google ID Token 後登入（帳號不存在時自動建立）。
 * 未設定 GOOGLE_OAUTH_CLIENT_ID 時此功能自動停用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final GoogleTokenVerifier tokenVerifier;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityService activityService;
    private final AuthService authService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${n3n.auth.google.client-id:}")
    private String clientId;

    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank();
    }

    public String getClientId() {
        return isEnabled() ? clientId : null;
    }

    /**
     * Sign in with a Google ID token (the "credential" from Google Identity Services).
     * Returns the same response shape as a password login.
     */
    @Transactional
    public AuthResponse login(String credential, String ipAddress, String userAgent) {
        if (!isEnabled()) {
            throw new BadCredentialsException("Google sign-in is not enabled");
        }

        GoogleTokenInfo tokenInfo = tokenVerifier.verify(credential);
        validateTokenInfo(tokenInfo);

        User user = userRepository.findByEmail(tokenInfo.email())
            .map(existing -> loginExistingUser(existing))
            .orElseGet(() -> createGoogleUser(tokenInfo));

        activityService.logLogin(user.getId(), user.getEmail());

        return authService.generateAuthResponse(user, ipAddress, userAgent);
    }

    private void validateTokenInfo(GoogleTokenInfo tokenInfo) {
        if (!clientId.equals(tokenInfo.aud())) {
            log.warn("Google token rejected: audience mismatch");
            throw new BadCredentialsException("Invalid Google credential");
        }
        if (!tokenInfo.emailVerified()) {
            log.warn("Google token rejected: email not verified");
            throw new BadCredentialsException("Google account email is not verified");
        }
        if (tokenInfo.email() == null || tokenInfo.email().isBlank()) {
            throw new BadCredentialsException("Invalid Google credential");
        }
    }

    private User loginExistingUser(User user) {
        authService.validateUserStatus(user);

        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    private User createGoogleUser(GoogleTokenInfo tokenInfo) {
        // 與 AuthService.register 相同：第一位使用者取得 ADMIN + USER
        boolean isFirstUser = userRoleRepository.findByRole("ADMIN").isEmpty();

        String displayName = tokenInfo.name() != null && !tokenInfo.name().isBlank()
            ? tokenInfo.name().replaceAll("<[^>]*>", "").trim()
            : tokenInfo.email();

        User user = User.builder()
            .email(tokenInfo.email())
            .passwordHash(passwordEncoder.encode(randomUnusablePassword()))
            .name(displayName)
            .avatarUrl(tokenInfo.picture())
            .status("active")
            .emailVerified(true)
            .loginAttempts(0)
            .lastLoginAt(Instant.now())
            .build();

        user = userRepository.save(user);

        List<String> roles = isFirstUser ? List.of("ADMIN", "USER") : List.of("USER");
        for (String role : roles) {
            userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .role(role)
                .build());
        }

        activityService.logUserCreate(user.getId(), user.getEmail(), String.join(",", roles));

        if (isFirstUser) {
            log.info("First user auto-created via Google Sign-In as admin: {}", user.getEmail());
        } else {
            log.info("User auto-created via Google Sign-In: {}", user.getEmail());
        }

        return user;
    }

    /**
     * Google 帳號使用者沒有本地密碼：產生隨機值後以 bcrypt 雜湊，
     * 使密碼登入永遠無法匹配（unusable password）。
     */
    private String randomUnusablePassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
