package com.aiinpocket.n3n.site.dto;

import lombok.Builder;

/**
 * 外部部署結果。experimental 恆為 true（v1）。
 */
@Builder
public record SiteDeployResponse(
        String provider,
        String deploymentId,
        String url,
        String status,
        boolean experimental
) {
}
