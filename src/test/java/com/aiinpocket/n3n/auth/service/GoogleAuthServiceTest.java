package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.exception.BadCredentialsException;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import com.aiinpocket.n3n.auth.service.GoogleTokenVerifier.GoogleTokenInfo;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.base.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GoogleAuthServiceTest extends BaseServiceTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";
    private static final String CREDENTIAL = "google-id-token";
    private static final String IP = "127.0.0.1";
    private static final String USER_AGENT = "JUnit";

    @Mock
    private GoogleTokenVerifier tokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ActivityService activityService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(googleAuthService, "clientId", CLIENT_ID);
    }

    private GoogleTokenInfo validTokenInfo() {
        return new GoogleTokenInfo(
            CLIENT_ID, "google-user@example.com", true,
            "Google User", "https://example.com/avatar.png", "google-sub-123");
    }

    private AuthResponse authResponseFor(User user) {
        return AuthResponse.builder()
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .expiresIn(900L)
            .user(UserResponse.from(user, List.of("USER")))
            .build();
    }

    // ========== Successful login tests ==========

    @Test
    void login_validToken_existingUser_returnsAuthResponse() {
        // Given
        User user = TestDataFactory.createUser("google-user@example.com", "Google User");
        user.setId(UUID.randomUUID());

        when(tokenVerifier.verify(CREDENTIAL)).thenReturn(validTokenInfo());
        when(userRepository.findByEmail("google-user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authService.generateAuthResponse(any(User.class), anyString(), anyString()))
            .thenReturn(authResponseFor(user));

        // When
        AuthResponse response = googleAuthService.login(CREDENTIAL, IP, USER_AGENT);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(authService).validateUserStatus(user);
        verify(authService).generateAuthResponse(user, IP, USER_AGENT);
        verify(activityService).logLogin(user.getId(), user.getEmail());
        // No new user is created
        verify(userRoleRepository, never()).save(any(UserRole.class));
        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_validToken_newUser_autoCreatedWithUserRole() {
        // Given: no existing user, an admin already exists
        when(tokenVerifier.verify(CREDENTIAL)).thenReturn(validTokenInfo());
        when(userRepository.findByEmail("google-user@example.com")).thenReturn(Optional.empty());
        when(userRoleRepository.findByRole("ADMIN")).thenReturn(List.of(
            UserRole.builder().userId(UUID.randomUUID()).role("ADMIN").build()
        ));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authService.generateAuthResponse(any(User.class), anyString(), anyString()))
            .thenAnswer(inv -> authResponseFor(inv.getArgument(0)));

        // When
        AuthResponse response = googleAuthService.login(CREDENTIAL, IP, USER_AGENT);

        // Then
        assertThat(response).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getEmail()).isEqualTo("google-user@example.com");
        assertThat(created.getName()).isEqualTo("Google User");
        assertThat(created.getStatus()).isEqualTo("active");
        assertThat(created.getEmailVerified()).isTrue();
        assertThat(created.getPasswordHash()).isEqualTo("encoded-random-password");
        assertThat(created.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository, times(1)).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isEqualTo("USER");

        verify(activityService).logUserCreate(any(UUID.class), eq("google-user@example.com"), eq("USER"));
        verify(activityService).logLogin(any(UUID.class), eq("google-user@example.com"));
    }

    @Test
    void login_validToken_firstUser_autoCreatedAsAdmin() {
        // Given: no existing user and no admin yet
        when(tokenVerifier.verify(CREDENTIAL)).thenReturn(validTokenInfo());
        when(userRepository.findByEmail("google-user@example.com")).thenReturn(Optional.empty());
        when(userRoleRepository.findByRole("ADMIN")).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authService.generateAuthResponse(any(User.class), anyString(), anyString()))
            .thenAnswer(inv -> authResponseFor(inv.getArgument(0)));

        // When
        googleAuthService.login(CREDENTIAL, IP, USER_AGENT);

        // Then: first user gets ADMIN + USER
        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository, times(2)).save(roleCaptor.capture());
        assertThat(roleCaptor.getAllValues())
            .extracting(UserRole::getRole)
            .containsExactly("ADMIN", "USER");
    }

    // ========== Rejection tests ==========

    @Test
    void login_wrongAudience_rejected() {
        // Given
        GoogleTokenInfo wrongAud = new GoogleTokenInfo(
            "another-client-id.apps.googleusercontent.com",
            "google-user@example.com", true, "Google User", null, "google-sub-123");
        when(tokenVerifier.verify(CREDENTIAL)).thenReturn(wrongAud);

        // When / Then
        assertThatThrownBy(() -> googleAuthService.login(CREDENTIAL, IP, USER_AGENT))
            .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_unverifiedEmail_rejected() {
        // Given
        GoogleTokenInfo unverified = new GoogleTokenInfo(
            CLIENT_ID, "google-user@example.com", false, "Google User", null, "google-sub-123");
        when(tokenVerifier.verify(CREDENTIAL)).thenReturn(unverified);

        // When / Then
        assertThatThrownBy(() -> googleAuthService.login(CREDENTIAL, IP, USER_AGENT))
            .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_disabled_blankClientId_rejected() {
        // Given
        ReflectionTestUtils.setField(googleAuthService, "clientId", "");

        // When / Then
        assertThatThrownBy(() -> googleAuthService.login(CREDENTIAL, IP, USER_AGENT))
            .isInstanceOf(BadCredentialsException.class);
        verify(tokenVerifier, never()).verify(anyString());
    }

    @Test
    void login_invalidToken_verifierThrows_rejected() {
        // Given
        when(tokenVerifier.verify(CREDENTIAL))
            .thenThrow(new BadCredentialsException("Invalid Google credential"));

        // When / Then
        assertThatThrownBy(() -> googleAuthService.login(CREDENTIAL, IP, USER_AGENT))
            .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).findByEmail(anyString());
    }

    // ========== isEnabled tests ==========

    @Test
    void isEnabled_withClientId_returnsTrue() {
        assertThat(googleAuthService.isEnabled()).isTrue();
        assertThat(googleAuthService.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void isEnabled_blankClientId_returnsFalse() {
        ReflectionTestUtils.setField(googleAuthService, "clientId", "");
        assertThat(googleAuthService.isEnabled()).isFalse();
        assertThat(googleAuthService.getClientId()).isNull();
    }
}
