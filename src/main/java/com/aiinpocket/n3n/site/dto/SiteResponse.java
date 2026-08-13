package com.aiinpocket.n3n.site.dto;

import com.aiinpocket.n3n.site.entity.Site;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * 網站摘要（列表用）。url 由呼叫端提供：base-domain 設定時為
 * https://{slug}.{base-domain}/，否則為相對路徑 /sites/{slug}/。
 */
@Builder
public record SiteResponse(
        UUID id,
        String slug,
        String name,
        String description,
        boolean isPublished,
        String url,
        String customDomain,
        boolean customDomainVerified,
        long fileCount,
        long totalSizeBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public static SiteResponse from(Site site, long fileCount, long totalSizeBytes, String url) {
        return SiteResponse.builder()
                .id(site.getId())
                .slug(site.getSlug())
                .name(site.getName())
                .description(site.getDescription())
                .isPublished(site.isPublished())
                .url(url)
                .customDomain(site.getCustomDomain())
                .customDomainVerified(site.isCustomDomainVerified())
                .fileCount(fileCount)
                .totalSizeBytes(totalSizeBytes)
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .build();
    }
}
