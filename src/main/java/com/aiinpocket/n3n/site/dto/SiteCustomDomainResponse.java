package com.aiinpocket.n3n.site.dto;

import lombok.Builder;

import java.util.List;

/**
 * 自訂網域狀態 + 使用者需建立的 DNS 記錄。
 * domain 為 null 表示尚未設定。
 */
@Builder
public record SiteCustomDomainResponse(
        String domain,
        boolean verified,
        List<SiteDnsRecord> records
) {
}
