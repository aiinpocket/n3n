package com.aiinpocket.n3n.admin.controller;

import com.aiinpocket.n3n.admin.dto.CreateUserRequest;
import com.aiinpocket.n3n.admin.dto.UserResponse;
import com.aiinpocket.n3n.admin.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin user management")
public class AdminController {

    private final AdminUserService adminUserService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listUsers(pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(adminUserService.createUser(request, adminId));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(adminUserService.updateUserStatus(id, status, adminId));
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserResponse> updateUserRoles(
            @PathVariable UUID id,
            @RequestBody Set<String> roles,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(adminUserService.updateUserRoles(id, roles, adminId));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Void> resetUserPassword(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = UUID.fromString(userDetails.getUsername());
        adminUserService.resetUserPassword(id, adminId);
        return ResponseEntity.ok().build();
    }
}
