package com.aiinpocket.n3n.flow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class FlowShareRequest {

    /**
     * 被分享的用戶 ID（已註冊用戶）
     */
    private UUID userId;

    /**
     * 邀請的 Email（尚未註冊的用戶）
     */
    @Email(message = "Invalid email format")
    private String email;

    /**
     * 權限等級：view, edit, admin
     */
    @NotBlank(message = "Permission is required")
    @Pattern(regexp = "^(view|edit|admin)$", message = "Permission must be view, edit, or admin")
    private String permission = "view";
}
