package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.AgentRegistration;
import com.aiinpocket.n3n.agent.service.AgentRegistrationService;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 註冊管理 REST Controller
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentRegistrationController {

    private final AgentRegistrationService registrationService;
    private final ObjectMapper objectMapper;

    /**
     * 產生新的 Agent 註冊 Token
     * 回傳 JSON Config 檔案
     */
    @PostMapping("/tokens")
    public ResponseEntity<?> generateToken(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentRegistrationService.TokenGenerationResult result =
                registrationService.generateToken(userId);

            // Return as downloadable JSON file
            String configJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(result.config());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", "n3n-agent-config.json");

            return ResponseEntity.ok()
                .headers(headers)
                .body(configJson.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate token"));
        }
    }

    /**
     * 產生 Token 並取得 JSON 回應（不下載）
     */
    @PostMapping("/tokens/json")
    public ResponseEntity<?> generateTokenJson(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentRegistrationService.TokenGenerationResult result =
                registrationService.generateToken(userId);

            return ResponseEntity.ok(Map.of(
                "registrationId", result.registrationId(),
                "agentId", result.agentId(),
                "config", result.config()
            ));

        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate token"));
        }
    }

    /**
     * 列出所有 Agent 註冊
     */
    @GetMapping("/registrations")
    public ResponseEntity<?> listRegistrations(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            List<AgentRegistration> registrations = registrationService.getRegistrations(userId);

            List<RegistrationInfo> infos = registrations.stream()
                .map(this::toInfo)
                .toList();

            return ResponseEntity.ok(Map.of("registrations", infos));

        } catch (Exception e) {
            log.error("Failed to list registrations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list registrations"));
        }
    }

    /**
     * 封鎖 Agent
     */
    @PutMapping("/{id}/block")
    public ResponseEntity<?> blockAgent(
            @PathVariable UUID id,
            @RequestBody(required = false) BlockRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            String reason = request != null ? request.reason() : "Blocked by user";
            registrationService.blockAgent(userId, id, reason);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to block agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to block agent"));
        }
    }

    /**
     * 解除封鎖 Agent
     */
    @PutMapping("/{id}/unblock")
    public ResponseEntity<?> unblockAgent(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            registrationService.unblockAgent(userId, id);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to unblock agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to unblock agent"));
        }
    }

    /**
     * 刪除 Agent 註冊
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRegistration(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            registrationService.deleteRegistration(userId, id);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete registration"));
        }
    }

    private RegistrationInfo toInfo(AgentRegistration reg) {
        return new RegistrationInfo(
            reg.getId(),
            reg.getDeviceId(),
            reg.getDeviceName(),
            reg.getPlatform(),
            reg.getStatus().name(),
            reg.getCreatedAt().toEpochMilli(),
            reg.getRegisteredAt() != null ? reg.getRegisteredAt().toEpochMilli() : null,
            reg.getBlockedAt() != null ? reg.getBlockedAt().toEpochMilli() : null,
            reg.getBlockedReason(),
            reg.getLastSeenAt() != null ? reg.getLastSeenAt().toEpochMilli() : null
        );
    }

    // Request/Response DTOs

    public record BlockRequest(String reason) {}

    public record RegistrationInfo(
        UUID id,
        String deviceId,
        String deviceName,
        String platform,
        String status,
        long createdAt,
        Long registeredAt,
        Long blockedAt,
        String blockedReason,
        Long lastSeenAt
    ) {}
}
