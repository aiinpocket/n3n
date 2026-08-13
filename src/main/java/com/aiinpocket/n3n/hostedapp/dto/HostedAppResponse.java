package com.aiinpocket.n3n.hostedapp.dto;

import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hosted App API 回應（zip 內容與加密參數值不外流；params 僅回傳已填參數的
 * 名稱清單，值以遮罩處理由 service 層完成）。
 */
@Builder
public record HostedAppResponse(
        UUID id,
        String name,
        String slug,
        String appType,
        String status,
        Map<String, Object> manifest,
        List<String> filledParams,
        Integer hostPort,
        Integer internalPort,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {

    public static HostedAppResponse from(HostedApp app) {
        return HostedAppResponse.builder()
                .id(app.getId())
                .name(app.getName())
                .slug(app.getSlug())
                .appType(app.getAppType())
                .status(app.getStatus())
                .manifest(app.getManifest())
                .filledParams(app.getParams() == null
                        ? List.of()
                        : app.getParams().keySet().stream().sorted().toList())
                .hostPort(app.getHostPort())
                .internalPort(app.getInternalPort())
                .errorMessage(app.getErrorMessage())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
