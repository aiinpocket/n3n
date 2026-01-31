package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.auth.dto.request.LoginRequest;
import com.aiinpocket.n3n.auth.dto.request.RegisterRequest;
import com.aiinpocket.n3n.auth.dto.response.AuthResponse;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.exception.*;
import com.aiinpocket.n3n.auth.repository.RefreshTokenRepository;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.base.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest extends BaseServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockDurationMinutes", 30);
    }

    // ========== Register Tests ==========

    @Test
    void register_firstUser_shouldBeAdmin() {
        // Given
        RegisterRequest request = TestDataFactory.createRegisterRequest();
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse response = authService.register(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUser().getRoles()).contains("ADMIN", "USER");
        assertThat(response.getMessage()).contains("Admin");
        verify(userRoleRepository, times(2)).save(any(UserRole.class));
    }

    @Test
    void register_subsequentUser_shouldBeRegularUser() {
        // Given
        RegisterRequest request = TestDataFactory.createRegisterRequest();
        when(userRepository.count()).thenReturn(5L);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse response = authService.register(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUser().getRoles()).containsExactly("USER");
        assertThat(response.getUser().getRoles()).doesNotContain("ADMIN");
        verify(userRoleRepository, times(1)).save(any(UserRole.class));
    }

    @Test
    void register_duplicateEmail_throwsException() {
        // Given
        RegisterRequest request = TestDataFactory.createRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("already registered");
    }

    // ========== Login Tests ==========

    @Test
    void login_validCredentials_returnsTokens() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        User user = TestDataFactory.createUser(request.getEmail(), "Test User");
        UserRole role = TestDataFactory.createUserRole(user.getId(), "USER");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(role));
        when(jwtService.generateAccessToken(any(), anyString(), anyString(), any())).thenReturn("accessToken");
        when(jwtService.generateRefreshToken()).thenReturn("refreshToken");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(jwtService.hashRefreshToken(anyString())).thenReturn("hashedToken");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthResponse response = authService.login(request, "127.0.0.1", "TestBrowser");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
        assertThat(response.getUser().getEmail()).isEqualTo(request.getEmail());
    }

    @Test
    void login_invalidPassword_throwsException() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        User user = TestDataFactory.createUser(request.getEmail(), "Test User");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestBrowser"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_nonExistentUser_throwsException() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestBrowser"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_pendingUser_throwsException() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        User user = TestDataFactory.createPendingUser(request.getEmail());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestBrowser"))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("verify your email");
    }

    @Test
    void login_lockedUser_throwsException() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        User user = TestDataFactory.createLockedUser(request.getEmail());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestBrowser"))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void login_suspendedUser_throwsException() {
        // Given
        LoginRequest request = TestDataFactory.createLoginRequest();
        User user = TestDataFactory.createUser(request.getEmail(), "Suspended User");
        user.setStatus("suspended");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestBrowser"))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessageContaining("suspended");
    }

    // ========== Setup Required Tests ==========

    @Test
    void isSetupRequired_noUsers_returnsTrue() {
        // Given
        when(userRepository.count()).thenReturn(0L);

        // When
        boolean result = authService.isSetupRequired();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isSetupRequired_usersExist_returnsFalse() {
        // Given
        when(userRepository.count()).thenReturn(1L);

        // When
        boolean result = authService.isSetupRequired();

        // Then
        assertThat(result).isFalse();
    }
}
