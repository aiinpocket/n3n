package com.aiinpocket.n3n.admin.controller;

import com.aiinpocket.n3n.admin.dto.CreateUserRequest;
import com.aiinpocket.n3n.admin.dto.UpdateUserRolesRequest;
import com.aiinpocket.n3n.admin.dto.UserResponse;
import com.aiinpocket.n3n.admin.service.AdminUserService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminController adminController;

    private UserDetails adminUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("admin")
                .authorities("ROLE_ADMIN")
                .build();
    }

    private UserDetails adminUserWithId(UUID id) {
        return User.withUsername(id.toString())
                .password("admin")
                .authorities("ROLE_ADMIN")
                .build();
    }

    private UserResponse sampleUserResponse() {
        return UserResponse.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("Test User")
                .status("active")
                .emailVerified(true)
                .roles(Set.of("USER"))
                .createdAt(Instant.now())
                .build();
    }

    private UserResponse sampleUserResponse(UUID id, String email, String name, String status, Set<String> roles) {
        return UserResponse.builder()
                .id(id)
                .email(email)
                .name(name)
                .status(status)
                .emailVerified(true)
                .roles(roles)
                .createdAt(Instant.now())
                .build();
    }

    // ===== listUsers (GET /api/admin/users) =====

    @Test
    void listUsers_noSearch_returnsPage() {
        var pageable = PageRequest.of(0, 20);
        var userResponse = sampleUserResponse();
        Page<UserResponse> page = new PageImpl<>(List.of(userResponse), pageable, 1);
        when(adminUserService.listUsers(any(Pageable.class), eq(null))).thenReturn(page);

        var result = adminController.listUsers(pageable, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getEmail()).isEqualTo("user@example.com");
        verify(adminUserService).listUsers(pageable, null);
    }

    @Test
    void listUsers_withSearch_passesSearchToService() {
        var pageable = PageRequest.of(0, 20);
        var userResponse = sampleUserResponse();
        Page<UserResponse> page = new PageImpl<>(List.of(userResponse), pageable, 1);
        when(adminUserService.listUsers(any(Pageable.class), eq("test"))).thenReturn(page);

        var result = adminController.listUsers(pageable, "test");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(adminUserService).listUsers(pageable, "test");
    }

    @Test
    void listUsers_emptyPage_returnsOk() {
        var pageable = PageRequest.of(0, 20);
        when(adminUserService.listUsers(any(Pageable.class), eq(null))).thenReturn(Page.empty(pageable));

        var result = adminController.listUsers(pageable, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
        assertThat(result.getBody().getContent()).isEmpty();
    }

    @Test
    void listUsers_multipleUsers_returnsAll() {
        var pageable = PageRequest.of(0, 20);
        var user1 = sampleUserResponse(UUID.randomUUID(), "user1@example.com", "User One", "active", Set.of("USER"));
        var user2 = sampleUserResponse(UUID.randomUUID(), "user2@example.com", "User Two", "active", Set.of("ADMIN", "USER"));
        var user3 = sampleUserResponse(UUID.randomUUID(), "user3@example.com", "User Three", "suspended", Set.of("USER"));
        Page<UserResponse> page = new PageImpl<>(List.of(user1, user2, user3), pageable, 3);
        when(adminUserService.listUsers(any(Pageable.class), eq(null))).thenReturn(page);

        var result = adminController.listUsers(pageable, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent())
                .extracting(UserResponse::getEmail)
                .containsExactly("user1@example.com", "user2@example.com", "user3@example.com");
    }

    @Test
    void listUsers_withPagination_respectsPageParameters() {
        var pageable = PageRequest.of(2, 10);
        Page<UserResponse> page = new PageImpl<>(List.of(sampleUserResponse()), pageable, 21);
        when(adminUserService.listUsers(eq(pageable), eq(null))).thenReturn(page);

        var result = adminController.listUsers(pageable, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(21);
        assertThat(result.getBody().getNumber()).isEqualTo(2);
        assertThat(result.getBody().getSize()).isEqualTo(10);
        verify(adminUserService).listUsers(eq(pageable), eq(null));
    }

    // ===== getUser (GET /api/admin/users/{id}) =====

    @Test
    void getUser_found_returnsOk() {
        var userId = UUID.randomUUID();
        var userResponse = sampleUserResponse(userId, "found@example.com", "Found User", "active", Set.of("USER"));
        when(adminUserService.getUser(userId)).thenReturn(userResponse);

        var result = adminController.getUser(userId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(userId);
        assertThat(result.getBody().getEmail()).isEqualTo("found@example.com");
        assertThat(result.getBody().getName()).isEqualTo("Found User");
        assertThat(result.getBody().getStatus()).isEqualTo("active");
        assertThat(result.getBody().getRoles()).containsExactly("USER");
        verify(adminUserService).getUser(userId);
    }

    @Test
    void getUser_notFound_throwsException() {
        var userId = UUID.randomUUID();
        when(adminUserService.getUser(userId))
                .thenThrow(new ResourceNotFoundException("User not found: " + userId));

        assertThatThrownBy(() -> adminController.getUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
        verify(adminUserService).getUser(userId);
    }

    @Test
    void getUser_adminUser_returnsWithAdminRole() {
        var userId = UUID.randomUUID();
        var userResponse = sampleUserResponse(userId, "admin@example.com", "Admin User", "active", Set.of("ADMIN", "USER"));
        when(adminUserService.getUser(userId)).thenReturn(userResponse);

        var result = adminController.getUser(userId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    // ===== createUser (POST /api/admin/users) =====

    @Test
    void createUser_success_returnsCreated() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var request = new CreateUserRequest();
        request.setEmail("new@example.com");
        request.setName("New User");
        request.setPassword("securePassword1!");
        request.setRoles(Set.of("USER"));

        var createdUser = sampleUserResponse(UUID.randomUUID(), "new@example.com", "New User", "active", Set.of("USER"));
        when(adminUserService.createUser(any(CreateUserRequest.class), eq(adminId))).thenReturn(createdUser);

        var result = adminController.createUser(request, admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getEmail()).isEqualTo("new@example.com");
        assertThat(result.getBody().getName()).isEqualTo("New User");
        assertThat(result.getBody().getStatus()).isEqualTo("active");
        verify(adminUserService).createUser(eq(request), eq(adminId));
    }

    @Test
    void createUser_duplicateEmail_throwsException() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var request = new CreateUserRequest();
        request.setEmail("existing@example.com");
        request.setName("Duplicate User");

        when(adminUserService.createUser(any(CreateUserRequest.class), eq(adminId)))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        assertThatThrownBy(() -> adminController.createUser(request, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void createUser_withAdminRole_returnsCreatedWithAdminRole() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var request = new CreateUserRequest();
        request.setEmail("newadmin@example.com");
        request.setName("New Admin");
        request.setRoles(Set.of("ADMIN", "USER"));

        var createdUser = sampleUserResponse(UUID.randomUUID(), "newadmin@example.com", "New Admin", "active", Set.of("ADMIN", "USER"));
        when(adminUserService.createUser(any(CreateUserRequest.class), eq(adminId))).thenReturn(createdUser);

        var result = adminController.createUser(request, admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    void createUser_extractsAdminIdFromUserDetails() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);
        var request = new CreateUserRequest();
        request.setEmail("extract@example.com");
        request.setName("Extract Test");

        var createdUser = sampleUserResponse();
        when(adminUserService.createUser(any(CreateUserRequest.class), eq(adminId))).thenReturn(createdUser);

        adminController.createUser(request, admin);

        verify(adminUserService).createUser(any(CreateUserRequest.class), eq(adminId));
    }

    @Test
    void createUser_withoutPassword_delegatesToService() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var request = new CreateUserRequest();
        request.setEmail("nopassword@example.com");
        request.setName("No Password User");
        // password is null, service generates one

        var createdUser = sampleUserResponse(UUID.randomUUID(), "nopassword@example.com", "No Password User", "active", Set.of("USER"));
        when(adminUserService.createUser(any(CreateUserRequest.class), eq(adminId))).thenReturn(createdUser);

        var result = adminController.createUser(request, admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getEmail()).isEqualTo("nopassword@example.com");
        verify(adminUserService).createUser(eq(request), eq(adminId));
    }

    // ===== updateUserStatus (PATCH /api/admin/users/{id}/status) =====

    @Test
    void updateUserStatus_toSuspended_returnsOk() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var updatedUser = sampleUserResponse(userId, "user@example.com", "Suspended User", "suspended", Set.of("USER"));
        when(adminUserService.updateUserStatus(eq(userId), eq("suspended"), eq(adminId))).thenReturn(updatedUser);

        var result = adminController.updateUserStatus(userId, "suspended", admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("suspended");
        verify(adminUserService).updateUserStatus(userId, "suspended", adminId);
    }

    @Test
    void updateUserStatus_toActive_returnsOk() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var updatedUser = sampleUserResponse(userId, "user@example.com", "Active User", "active", Set.of("USER"));
        when(adminUserService.updateUserStatus(eq(userId), eq("active"), eq(adminId))).thenReturn(updatedUser);

        var result = adminController.updateUserStatus(userId, "active", admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getStatus()).isEqualTo("active");
    }

    @Test
    void updateUserStatus_toDeleted_returnsOk() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var updatedUser = sampleUserResponse(userId, "user@example.com", "Deleted User", "deleted", Set.of("USER"));
        when(adminUserService.updateUserStatus(eq(userId), eq("deleted"), eq(adminId))).thenReturn(updatedUser);

        var result = adminController.updateUserStatus(userId, "deleted", admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getStatus()).isEqualTo("deleted");
    }

    @Test
    void updateUserStatus_selfStatusChange_throwsException() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);

        when(adminUserService.updateUserStatus(eq(adminId), eq("suspended"), eq(adminId)))
                .thenThrow(new IllegalArgumentException("Cannot change your own account status"));

        assertThatThrownBy(() -> adminController.updateUserStatus(adminId, "suspended", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own account status");
    }

    @Test
    void updateUserStatus_userNotFound_throwsException() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();

        when(adminUserService.updateUserStatus(eq(userId), eq("suspended"), eq(adminId)))
                .thenThrow(new ResourceNotFoundException("User not found: " + userId));

        assertThatThrownBy(() -> adminController.updateUserStatus(userId, "suspended", admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateUserStatus_lastAdmin_throwsException() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();

        when(adminUserService.updateUserStatus(eq(userId), eq("suspended"), eq(adminId)))
                .thenThrow(new IllegalArgumentException("Cannot deactivate the last admin user"));

        assertThatThrownBy(() -> adminController.updateUserStatus(userId, "suspended", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deactivate the last admin user");
    }

    @Test
    void updateUserStatus_extractsAdminIdFromUserDetails() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);
        var userId = UUID.randomUUID();
        var updatedUser = sampleUserResponse(userId, "user@example.com", "User", "active", Set.of("USER"));
        when(adminUserService.updateUserStatus(eq(userId), eq("active"), eq(adminId))).thenReturn(updatedUser);

        adminController.updateUserStatus(userId, "active", admin);

        verify(adminUserService).updateUserStatus(eq(userId), eq("active"), eq(adminId));
    }

    // ===== updateUserRoles (PUT /api/admin/users/{id}/roles) =====

    @Test
    void updateUserRoles_success_returnsOk() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var request = new UpdateUserRolesRequest(Set.of("USER", "ADMIN"));
        var updatedUser = sampleUserResponse(userId, "user@example.com", "Updated Roles User", "active", Set.of("USER", "ADMIN"));
        when(adminUserService.updateUserRoles(eq(userId), eq(Set.of("USER", "ADMIN")), eq(adminId))).thenReturn(updatedUser);

        var result = adminController.updateUserRoles(userId, request, admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
        verify(adminUserService).updateUserRoles(userId, Set.of("USER", "ADMIN"), adminId);
    }

    @Test
    void updateUserRoles_setToUserOnly_returnsOk() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var request = new UpdateUserRolesRequest(Set.of("USER"));
        var updatedUser = sampleUserResponse(userId, "user@example.com", "Demoted User", "active", Set.of("USER"));
        when(adminUserService.updateUserRoles(eq(userId), eq(Set.of("USER")), eq(adminId))).thenReturn(updatedUser);

        var result = adminController.updateUserRoles(userId, request, admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getRoles()).containsExactly("USER");
    }

    @Test
    void updateUserRoles_userNotFound_throwsException() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var request = new UpdateUserRolesRequest(Set.of("USER"));

        when(adminUserService.updateUserRoles(eq(userId), eq(Set.of("USER")), eq(adminId)))
                .thenThrow(new ResourceNotFoundException("User not found: " + userId));

        assertThatThrownBy(() -> adminController.updateUserRoles(userId, request, admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateUserRoles_removeSelfAdminRole_throwsException() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);
        var request = new UpdateUserRolesRequest(Set.of("USER"));

        when(adminUserService.updateUserRoles(eq(adminId), eq(Set.of("USER")), eq(adminId)))
                .thenThrow(new IllegalArgumentException("Cannot remove ADMIN role from your own account"));

        assertThatThrownBy(() -> adminController.updateUserRoles(adminId, request, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot remove ADMIN role from your own account");
    }

    @Test
    void updateUserRoles_invalidRole_throwsException() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var request = new UpdateUserRolesRequest(Set.of("SUPERADMIN"));

        when(adminUserService.updateUserRoles(eq(userId), eq(Set.of("SUPERADMIN")), eq(adminId)))
                .thenThrow(new IllegalArgumentException("Invalid role: SUPERADMIN. Must be one of: [USER, ADMIN]"));

        assertThatThrownBy(() -> adminController.updateUserRoles(userId, request, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    void updateUserRoles_extractsAdminIdFromUserDetails() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);
        var userId = UUID.randomUUID();
        var request = new UpdateUserRolesRequest(Set.of("USER"));
        var updatedUser = sampleUserResponse(userId, "user@example.com", "User", "active", Set.of("USER"));
        when(adminUserService.updateUserRoles(eq(userId), eq(Set.of("USER")), eq(adminId))).thenReturn(updatedUser);

        adminController.updateUserRoles(userId, request, admin);

        verify(adminUserService).updateUserRoles(eq(userId), eq(Set.of("USER")), eq(adminId));
    }

    // ===== resetUserPassword (POST /api/admin/users/{id}/reset-password) =====

    @Test
    void resetUserPassword_success_returnsOk() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        doNothing().when(adminUserService).resetUserPassword(eq(userId), eq(adminId));

        var result = adminController.resetUserPassword(userId, admin);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNull();
        verify(adminUserService).resetUserPassword(userId, adminId);
    }

    @Test
    void resetUserPassword_userNotFound_throwsException() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();

        doThrow(new ResourceNotFoundException("User not found: " + userId))
                .when(adminUserService).resetUserPassword(eq(userId), eq(adminId));

        assertThatThrownBy(() -> adminController.resetUserPassword(userId, admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void resetUserPassword_selfReset_throwsException() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);

        doThrow(new IllegalArgumentException("Cannot reset your own password via admin endpoint. Use the change password feature instead."))
                .when(adminUserService).resetUserPassword(eq(adminId), eq(adminId));

        assertThatThrownBy(() -> adminController.resetUserPassword(adminId, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot reset your own password via admin endpoint");
    }

    @Test
    void resetUserPassword_extractsAdminIdFromUserDetails() {
        var adminId = UUID.randomUUID();
        var admin = adminUserWithId(adminId);
        var userId = UUID.randomUUID();
        doNothing().when(adminUserService).resetUserPassword(eq(userId), eq(adminId));

        adminController.resetUserPassword(userId, admin);

        verify(adminUserService).resetUserPassword(eq(userId), eq(adminId));
    }

    // ===== Cross-cutting concerns =====

    @Test
    void allEndpoints_returnResponseEntity() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var userId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);

        // Setup mocks for all endpoints
        when(adminUserService.listUsers(any(Pageable.class), eq(null))).thenReturn(Page.empty(pageable));
        when(adminUserService.getUser(userId)).thenReturn(sampleUserResponse());
        doNothing().when(adminUserService).resetUserPassword(eq(userId), eq(adminId));

        // Verify each endpoint returns ResponseEntity
        var listResult = adminController.listUsers(pageable, null);
        assertThat(listResult).isNotNull();
        assertThat(listResult.getStatusCode()).isEqualTo(HttpStatus.OK);

        var getResult = adminController.getUser(userId);
        assertThat(getResult).isNotNull();
        assertThat(getResult.getStatusCode()).isEqualTo(HttpStatus.OK);

        var resetResult = adminController.resetUserPassword(userId, admin);
        assertThat(resetResult).isNotNull();
        assertThat(resetResult.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listUsers_serviceThrowsRuntimeException_propagates() {
        var pageable = PageRequest.of(0, 20);
        when(adminUserService.listUsers(any(Pageable.class), eq(null)))
                .thenThrow(new RuntimeException("Database connection failed"));

        assertThatThrownBy(() -> adminController.listUsers(pageable, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");
    }

    @Test
    void createUser_serviceThrowsRuntimeException_propagates() {
        var admin = adminUser();
        var adminId = UUID.fromString(admin.getUsername());
        var request = new CreateUserRequest();
        request.setEmail("error@example.com");
        request.setName("Error User");

        when(adminUserService.createUser(any(CreateUserRequest.class), eq(adminId)))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> adminController.createUser(request, admin))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected error");
    }
}
