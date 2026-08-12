package com.aiinpocket.n3n.flow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateShareLinkRequest {

    /**
     * 連結授予的權限：view 或 edit（連結不開放 admin）
     */
    @NotBlank(message = "Permission is required")
    @Pattern(regexp = "^(view|edit)$", message = "Permission must be view or edit")
    private String permission = "view";

    /**
     * 有效天數（null 表示永久有效）
     */
    @Min(value = 1, message = "expiresInDays must be at least 1")
    @Max(value = 365, message = "expiresInDays must not exceed 365")
    private Integer expiresInDays;
}
