package com.aiinpocket.n3n.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 設定自訂網域的請求（純 hostname，無 scheme/path/port）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteCustomDomainRequest {

    @NotBlank
    @Size(max = 255)
    private String domain;
}
