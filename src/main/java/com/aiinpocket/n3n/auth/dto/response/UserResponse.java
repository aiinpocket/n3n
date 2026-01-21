package com.aiinpocket.n3n.auth.dto.response;

import com.aiinpocket.n3n.auth.entity.User;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String name;
    private String avatarUrl;
    private List<String> roles;

    public static UserResponse from(User user, List<String> roles) {
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .avatarUrl(user.getAvatarUrl())
            .roles(roles)
            .build();
    }
}
