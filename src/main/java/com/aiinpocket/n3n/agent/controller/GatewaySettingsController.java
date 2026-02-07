package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.GatewaySettings;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gateway settings REST Controller.
 * Admin access only.
 */
@RestController
@RequestMapping("/api/settings/gateway")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Gateway Settings", description = "Gateway configuration management")
public class GatewaySettingsController {

    private final GatewaySettingsService gatewaySettingsService;

    /**
     * Get gateway settings
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GatewaySettingsResponse> getSettings() {
        GatewaySettings settings = gatewaySettingsService.getSettings();
        return ResponseEntity.ok(toResponse(settings));
    }

    /**
     * Update gateway settings
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
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
