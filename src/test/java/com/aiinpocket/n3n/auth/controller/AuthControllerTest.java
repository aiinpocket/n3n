package com.aiinpocket.n3n.auth.controller;

import com.aiinpocket.n3n.auth.dto.request.LoginRequest;
import com.aiinpocket.n3n.auth.dto.request.RegisterRequest;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.exception.BadCredentialsException;
import com.aiinpocket.n3n.auth.exception.EmailAlreadyExistsException;
import com.aiinpocket.n3n.auth.security.LoginRateLimiter;
import com.aiinpocket.n3n.auth.service.AuthService;
import com.aiinpocket.n3n.base.TestDataFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController using Mockito.
 * Tests controller logic without full Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthController authController;

    // ========== Login Tests ==========

    @Test
    void login_validCredentials_returnsOk() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        AuthResponse response = createAuthResponse();
        HttpServletRequest httpRequest = createMockHttpRequest();

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenReturn(response);

        // When
        ResponseEntity<AuthResponse> result = authController.login(request, httpRequest);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getAccessToken()).isEqualTo("test-access-token");
        assertThat(result.getBody().getRefreshToken()).isEqualTo("test-refresh-token");

        verify(loginRateLimiter).checkLoginAllowed(anyString(), eq(request.getEmail()));
        verify(loginRateLimiter).recordLoginSuccess(request.getEmail());
    }

    @Test
    void login_invalidCredentials_recordsFailure() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        HttpServletRequest httpRequest = createMockHttpRequest();

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        // When/Then
        assertThatThrownBy(() -> authController.login(request, httpRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginRateLimiter).recordLoginFailure(anyString(), eq(request.getEmail()));
    }

    @Test
    void login_withXForwardedFor_usesFirstIp() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        AuthResponse response = createAuthResponse();
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestBrowser");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenReturn(response);

        // When
        authController.login(request, httpRequest);

        // Then
        verify(loginRateLimiter).checkLoginAllowed(eq("10.0.0.1"), anyString());
    }

    // ========== Register Tests ==========

    @Test
    void register_validRequest_returnsOk() {
        // Given
        RegisterRequest request = TestDataFactory.createRegisterRequest();
        AuthResponse response = createAuthResponse();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // When
        ResponseEntity<AuthResponse> result = authController.register(request);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void register_duplicateEmail_throwsException() {
        // Given
        RegisterRequest request = TestDataFactory.createRegisterRequest();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email already registered"));

        // When/Then
        assertThatThrownBy(() -> authController.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    // ========== Setup Status Tests ==========

    @Test
    void getSetupStatus_noUsers_returnsSetupRequired() {
        // Given
        when(authService.isSetupRequired()).thenReturn(true);

        // When
        ResponseEntity<AuthController.SetupStatusResponse> result = authController.getSetupStatus();

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().setupRequired()).isTrue();
    }

    @Test
    void getSetupStatus_usersExist_returnsSetupNotRequired() {
        // Given
        when(authService.isSetupRequired()).thenReturn(false);

        // When
        ResponseEntity<AuthController.SetupStatusResponse> result = authController.getSetupStatus();

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().setupRequired()).isFalse();
    }

    // ========== Logout Tests ==========

    @Test
    void logout_withRefreshToken_revokesToken() {
        // Given
        var request = new com.aiinpocket.n3n.auth.dto.request.RefreshTokenRequest();
        request.setRefreshToken("test-refresh-token");

        // When
        ResponseEntity<Void> result = authController.logout(request);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).logout("test-refresh-token");
    }

    @Test
    void logout_withoutRefreshToken_completesSuccessfully() {
        // When
        ResponseEntity<Void> result = authController.logout(null);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).logout(null);
    }

    // ========== Helper Methods ==========

    private HttpServletRequest createMockHttpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("TestBrowser");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        return request;
    }

    private AuthResponse createAuthResponse() {
        return AuthResponse.builder()
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .expiresIn(3600L)
                .user(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .name("Test User")
                        .roles(List.of("USER"))
                        .build())
                .build();
    }
}
