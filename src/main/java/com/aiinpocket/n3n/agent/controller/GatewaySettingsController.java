package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.GatewaySettings;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gateway 設定 REST Controller
 * 只有管理員可以存取
 */
@RestController
@RequestMapping("/api/settings/gateway")
@RequiredArgsConstructor
@Slf4j
public class GatewaySettingsController {

    private final GatewaySettingsService gatewaySettingsService;

    /**
     * 取得 Gateway 設定
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GatewaySettingsResponse> getSettings() {
        GatewaySettings settings = gatewaySettingsService.getSettings();
        return ResponseEntity.ok(toResponse(settings));
    }

    /**
     * 更新 Gateway 設定
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSettings(@RequestBody UpdateSettingsRequest request) {
        try {
            GatewaySettings settings = gatewaySettingsService.updateSettings(
                request.domain(),
                request.port(),
                request.enabled()
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "settings", toResponse(settings),
                "message", "Settings updated. Restart the server to apply changes."
            ));
        } catch (Exception e) {
            log.error("Failed to update gateway settings", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to update settings: " + e.getMessage()));
        }
    }

    private GatewaySettingsResponse toResponse(GatewaySettings settings) {
        return new GatewaySettingsResponse(
            settings.getGatewayDomain(),
            settings.getGatewayPort(),
            settings.getEnabled(),
            settings.getWebSocketUrl(),
            settings.getHttpUrl(),
            settings.getUpdatedAt().toEpochMilli()
        );
    }

    // Request/Response DTOs

    public record UpdateSettingsRequest(
        String domain,
        Integer port,
        Boolean enabled
    ) {}

    public record GatewaySettingsResponse(
        String domain,
        int port,
        boolean enabled,
        String webSocketUrl,
        String httpUrl,
        long updatedAt
    ) {}
}
