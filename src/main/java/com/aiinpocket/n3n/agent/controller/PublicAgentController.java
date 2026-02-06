package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.service.AgentRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公開的 Agent 端點（無需認證）
 * Agent 使用一次性 Token 進行註冊
 */
@RestController
@RequestMapping("/api/public/agents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public Agent", description = "Public agent registration endpoints")
public class PublicAgentController {

    private final AgentRegistrationService registrationService;

    /**
     * Agent 使用 Token 註冊
     * 這個端點不需要認證，Token 本身就是認證
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerAgent(@RequestBody RegisterRequest request) {
        try {
            AgentRegistrationService.RegistrationRequest regRequest =
                new AgentRegistrationService.RegistrationRequest(
                    request.token(),
                    request.deviceId(),
                    request.deviceName(),
                    request.platform(),
                    request.devicePublicKey(),
                    request.deviceFingerprint()
                );

            AgentRegistrationService.RegistrationResult result =
                registrationService.registerAgent(regRequest);

            return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "platformPublicKey", result.platformPublicKey(),
                "platformFingerprint", result.platformFingerprint(),
                "deviceToken", result.deviceToken()
            ));

        } catch (AgentRegistrationService.RegistrationException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to register agent: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    // Request DTO

    public record RegisterRequest(
        String token,
        String deviceId,
        String deviceName,
        String platform,
        String devicePublicKey,
        String deviceFingerprint
    ) {}
}
