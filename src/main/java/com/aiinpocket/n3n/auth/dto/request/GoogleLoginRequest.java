package com.aiinpocket.n3n.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    /** Google Identity Services 回傳的 ID token（credential） */
    @NotBlank(message = "Credential is required")
    private String credential;
}
