package com.aiinpocket.n3n.flow.dto;

import com.aiinpocket.n3n.flow.entity.FlowShareLink;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ShareLinkResponse {

    private UUID id;
    private String token;
    private String permission;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * 前端分享頁相對路徑，例如 /share/{token}
     */
    private String url;

    public static ShareLinkResponse from(FlowShareLink link) {
        return ShareLinkResponse.builder()
            .id(link.getId())
            .token(link.getToken())
            .permission(link.getPermission())
            .createdAt(link.getCreatedAt())
            .expiresAt(link.getExpiresAt())
            .url("/share/" + link.getToken())
            .build();
    }
}
