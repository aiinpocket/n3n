package com.aiinpocket.n3n.admin.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.admin.dto.CreateUserRequest;
import com.aiinpocket.n3n.admin.dto.UserResponse;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.common.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest extends BaseServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ActivityService activityService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AdminUserService adminUserService;

    private UUID adminId;
    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testUser = User.builder()
            .id(userId)
            .email("test@example.com")
            .name("Test User")
            .passwordHash("encoded-password")
            .status("active")
            .emailVerified(true)
            .build();
    }

    @Nested
    @DisplayName("List Users")
    class ListUsers {

        @Test
        void listUsers_returnsUsersWithRoles() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(testUser));
            UserRole role = UserRole.builder().userId(userId).role("USER").build();

            when(userRepository.findAll(pageable)).thenReturn(page);
            when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(role));

            Page<UserResponse> result = adminUserService.listUsers(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("test@example.com");
            assertThat(result.getContent().get(0).getRoles()).contains("USER");
        }

        @Test
        void listUsers_emptyResult() {
            Pageable pageable = PageRequest.of(0, 10);
            when(userRepository.findAll(pageable)).thenReturn(Page.empty());

            Page<UserResponse> result = adminUserService.listUsers(pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get User")
    class GetUser {

        @Test
        void getUser_existingId_returnsUserWithRoles() {
            UserRole role = UserRole.builder().userId(userId).role("ADMIN").build();
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(role));

            UserResponse result = adminUserService.getUser(userId);

            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getRoles()).contains("ADMIN");
        }

        @Test
        void getUser_nonExistingId_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(userRepository.findById(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.getUser(nonExisting))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create User")
    class CreateUser {

        @Test
        void createUser_withPassword_createsSuccessfully() {
            CreateUserRequest request = new CreateUserRequest();
            request.setEmail("new@example.com");
            request.setName("New User");
            request.setPassword("securePassword123");
            request.setRoles(Set.of("USER"));

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("securePassword123")).thenReturn("encoded-pass");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            UserResponse result = adminUserService.createUser(request, adminId);

            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getName()).isEqualTo("New User");
            verify(passwordEncoder).encode("securePassword123");
            verify(userRoleRepository).save(argThat(r -> "USER".equals(r.getRole())));
            verify(emailService).sendUserInvitation(eq("new@example.com"), eq("New User"), eq("securePassword123"));
            verify(activityService).logActivity(eq(adminId), eq(ActivityService.USER_CREATE), any(), any(), any(), any());
        }

        @Test
        void createUser_withoutPassword_generatesRandomPassword() {
            CreateUserRequest request = new CreateUserRequest();
            request.setEmail("new@example.com");
            request.setName("New User");
            request.setRoles(Set.of("USER"));

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            adminUserService.createUser(request, adminId);

            verify(passwordEncoder).encode(argThat(pass -> pass != null && !pass.isEmpty()));
            verify(emailService).sendUserInvitation(eq("new@example.com"), eq("New User"), argThat(p -> p != null && !p.isEmpty()));
        }

        @Test
        void createUser_duplicateEmail_throwsException() {
            CreateUserRequest request = new CreateUserRequest();
            request.setEmail("existing@example.com");
            request.setName("Existing");
            request.setRoles(Set.of("USER"));

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> adminUserService.createUser(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        }

        @Test
        void createUser_multipleRoles_assignsAll() {
            CreateUserRequest request = new CreateUserRequest();
            request.setEmail("new@example.com");
            request.setName("Multi Role User");
            request.setPassword("pass1234");
            request.setRoles(Set.of("USER", "ADMIN"));

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("enc");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            adminUserService.createUser(request, adminId);

            verify(userRoleRepository, times(2)).save(any(UserRole.class));
        }
    }

    @Nested
    @DisplayName("Update User Status")
    class UpdateUserStatus {

        @Test
        void updateUserStatus_validId_updatesStatus() {
            UserRole role = UserRole.builder().userId(userId).role("USER").build();
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(role));

            UserResponse result = adminUserService.updateUserStatus(userId, "suspended", adminId);

            verify(userRepository).save(argThat(u -> "suspended".equals(u.getStatus())));
            verify(activityService).logActivity(eq(adminId), eq(ActivityService.USER_UPDATE), any(), any(), any(), any());
        }

        @Test
        void updateUserStatus_nonExisting_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(userRepository.findById(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.updateUserStatus(nonExisting, "active", adminId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update User Roles")
    class UpdateUserRoles {

        @Test
        void updateUserRoles_replaceRoles() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            UserResponse result = adminUserService.updateUserRoles(userId, Set.of("ADMIN", "USER"), adminId);

            verify(userRoleRepository).deleteByUserId(userId);
            verify(userRoleRepository, times(2)).save(any(UserRole.class));
            verify(activityService).logActivity(eq(adminId), eq(ActivityService.USER_UPDATE), any(), any(), any(), any());
        }

        @Test
        void updateUserRoles_uppercasesRoles() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            adminUserService.updateUserRoles(userId, Set.of("admin"), adminId);

            verify(userRoleRepository).save(argThat(r -> "ADMIN".equals(r.getRole())));
        }
    }

    @Nested
    @DisplayName("Reset Password")
    class ResetPassword {

        @Test
        void resetUserPassword_resetsAndUnlocks() {
            testUser.setLoginAttempts(5);
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");

            adminUserService.resetUserPassword(userId, adminId);

            verify(userRepository).save(argThat(u ->
                u.getLoginAttempts() == 0 && u.getLockedUntil() == null
            ));
            verify(emailService).sendPasswordReset(eq("test@example.com"), anyString());
        }

        @Test
        void resetUserPassword_nonExisting_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(userRepository.findById(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.resetUserPassword(nonExisting, adminId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
