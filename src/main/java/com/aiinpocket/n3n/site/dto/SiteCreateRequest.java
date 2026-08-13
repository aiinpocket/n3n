package com.aiinpocket.n3n.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 建立網站請求。slug 由後端自動產生（name → kebab-case + 隨機尾碼）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;
}
