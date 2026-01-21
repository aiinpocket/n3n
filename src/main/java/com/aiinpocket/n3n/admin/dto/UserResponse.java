package com.aiinpocket.n3n.admin.dto;

import com.aiinpocket.n3n.auth.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String name;
    private String avatarUrl;
    private String status;
    private boolean emailVerified;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Set<String> roles;

    public static UserResponse from(User user, Set<String> roles) {
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .avatarUrl(user.getAvatarUrl())
            .status(user.getStatus())
            .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .roles(roles)
            .build();
    }
}
