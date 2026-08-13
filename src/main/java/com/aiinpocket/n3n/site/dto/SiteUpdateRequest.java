package com.aiinpocket.n3n.site.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新網站：改名、改描述、上/下架。所有欄位皆為可選（null = 不變更）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteUpdateRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    private Boolean isPublished;
}
