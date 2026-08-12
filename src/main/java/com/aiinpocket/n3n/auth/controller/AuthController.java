package com.aiinpocket.n3n.auth.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.auth.dto.request.*;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.exception.BadCredentialsException;
import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.auth.security.LoginRateLimiter;
import com.aiinpocket.n3n.auth.service.AuthService;
import com.aiinpocket.n3n.auth.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication and registration")
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final LoginRateLimiter loginRateLimiter;
    private final IpRateLimiter ipRateLimiter;
    private final ActivityService activityService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 檢查頻率限制
        loginRateLimiter.checkLoginAllowed(ipAddress, request.getEmail());

        try {
            AuthResponse response = authService.login(request, ipAddress, userAgent);
            // 登入成功，清除失敗計數
            loginRateLimiter.recordLoginSuccess(request.getEmail());

            // 記錄登入成功審計日誌
            UUID userId = response.getUser() != null ? response.getUser().getId() : null;
            activityService.logLogin(userId, request.getEmail());

            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            // 登入失敗，記錄失敗嘗試
            loginRateLimiter.recordLoginFailure(ipAddress, request.getEmail());

            // 記錄登入失敗審計日誌
            activityService.logLoginFailed(request.getEmail(), "Invalid credentials");

            throw e;
        }
    }

    /**
     * Google Sign-In 前端設定（未設定 client-id 時 enabled=false）
     */
    @GetMapping("/google/config")
    public ResponseEntity<GoogleConfigResponse> getGoogleConfig() {
        return ResponseEntity.ok(new GoogleConfigResponse(
            googleAuthService.isEnabled(),
            googleAuthService.getClientId()
        ));
    }

    /**
     * 以 Google ID Token 登入，回應格式與 /login 相同
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 與登入端點相同的 IP 頻率限制
        ipRateLimiter.checkAllowed("google-login", ipAddress, 10, 60);

        try {
            return ResponseEntity.ok(googleAuthService.login(request.getCredential(), ipAddress, userAgent));
        } catch (BadCredentialsException e) {
            activityService.logLoginFailed("google-sign-in", "Invalid Google credential");
            throw e;
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed("register", getClientIp(httpRequest), 5, 300);
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        ipRateLimiter.checkAllowed("token-refresh", ipAddress, 30, 60);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), ipAddress, userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String refreshToken = request != null ? request.getRefreshToken() : null;

        // 記錄登出審計日誌
        if (userDetails != null) {
            UserResponse user = authService.getCurrentUser(userDetails.getUsername());
            if (user != null) {
                activityService.logLogout(user.getId(), user.getEmail());
            }
        }

        authService.logout(refreshToken);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }

    /**
     * Check if initial setup is required (no users exist)
     */
    @GetMapping("/setup-status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        boolean setupRequired = authService.isSetupRequired();
        return ResponseEntity.ok(new SetupStatusResponse(setupRequired));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody com.aiinpocket.n3n.auth.dto.request.UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        UUID userId = UUID.fromString(userDetails.getUsername());
        UserResponse updated = authService.updateProfile(userId, request.name());
        activityService.logProfileUpdate(userId, updated.getEmail());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        authService.changePassword(UUID.fromString(userDetails.getUsername()), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed("forgot-password", getClientIp(httpRequest), 3, 300);
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If this email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed("reset-password", getClientIp(httpRequest), 5, 300);
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
    }

    public record SetupStatusResponse(boolean setupRequired) {}

    public record GoogleConfigResponse(boolean enabled, String clientId) {}

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String ip = xForwardedFor.split(",")[0].trim();
            // Validate IP with InetAddress to reject spoofed values
            try {
                java.net.InetAddress.getByName(ip);
                return ip;
            } catch (java.net.UnknownHostException e) {
                // Invalid IP format, fall through to remoteAddr
            }
        }
        return request.getRemoteAddr();
    }
}
