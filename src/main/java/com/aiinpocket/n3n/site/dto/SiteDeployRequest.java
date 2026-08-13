package com.aiinpocket.n3n.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 對外部平台部署請求（v1 僅支援 vercel，實驗性功能）。
 * token 由加密憑證庫解密取得（credentialId 必須屬於呼叫者本人）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteDeployRequest {

    @NotBlank
    private String provider;

    @NotNull
    private UUID credentialId;
}
