package com.aiinpocket.n3n.auth.controller;

import com.aiinpocket.n3n.auth.dto.request.LoginRequest;
import com.aiinpocket.n3n.auth.dto.request.RefreshTokenRequest;
import com.aiinpocket.n3n.auth.dto.request.RegisterRequest;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.exception.BadCredentialsException;
import com.aiinpocket.n3n.auth.security.LoginRateLimiter;
import com.aiinpocket.n3n.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

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
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            // 登入失敗，記錄失敗嘗試
            loginRateLimiter.recordLoginFailure(ipAddress, request.getEmail());
            throw e;
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), ipAddress, userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
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

    public record SetupStatusResponse(boolean setupRequired) {}

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
