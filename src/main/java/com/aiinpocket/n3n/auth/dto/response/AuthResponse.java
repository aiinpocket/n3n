package com.aiinpocket.n3n.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserResponse user;
    private String message;
    private Boolean isFirstUser;
    private Boolean needsRecoveryKeyBackup;
    private List<String> recoveryKey;
}
