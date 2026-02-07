package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.dto.request.CreateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.request.UpdateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.response.*;
import com.aiinpocket.n3n.ai.service.AiProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AI Provider 管理 API
 */
@RestController
@RequestMapping("/api/ai/providers")
@RequiredArgsConstructor
@Tag(name = "AI Providers", description = "AI provider configuration")
public class AiProviderController {

    private final AiProviderService providerService;

    /**
     * 列出所有可用的 Provider 類型
     */
    @GetMapping("/types")
    public ResponseEntity<List<ProviderTypeResponse>> listProviderTypes() {
        return ResponseEntity.ok(providerService.listProviderTypes());
    }

    /**
     * 列出使用者的所有設定
     */
    @GetMapping("/configs")
    public ResponseEntity<List<AiProviderConfigResponse>> listConfigs(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(providerService.listUserConfigs(userId));
    }

    /**
     * 取得預設設定
     */
    @GetMapping("/configs/default")
    public ResponseEntity<AiProviderConfigResponse> getDefaultConfig(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        AiProviderConfigResponse config = providerService.getDefaultConfig(userId);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 取得指定設定
     */
    @GetMapping("/configs/{id}")
    public ResponseEntity<AiProviderConfigResponse> getConfig(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(providerService.getConfig(id, userId));
    }

    /**
     * 建立設定
     */
    @PostMapping("/configs")
    public ResponseEntity<AiProviderConfigResponse> createConfig(
            @Valid @RequestBody CreateAiProviderRequest request,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerService.createConfig(request, userId));
    }

    /**
     * 更新設定
     */
    @PutMapping("/configs/{id}")
    public ResponseEntity<AiProviderConfigResponse> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAiProviderRequest request,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(providerService.updateConfig(id, request, userId));
    }

    /**
     * 刪除設定
     */
    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        providerService.deleteConfig(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 設為預設
     */
    @PostMapping("/configs/{id}/default")
    public ResponseEntity<Void> setAsDefault(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        providerService.setAsDefault(id, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 測試連線
     */
    @PostMapping("/configs/{id}/test")
    public ResponseEntity<TestConnectionResponse> testConnection(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(providerService.testConnection(id, userId));
    }

    /**
     * 取得可用模型清單
     */
    @GetMapping("/configs/{id}/models")
    public ResponseEntity<List<AiModelResponse>> listModels(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(providerService.fetchModels(id, userId));
    }

    /**
     * 直接用 API Key 取得模型清單（建立設定前使用）
     */
    @PostMapping("/models")
    public ResponseEntity<List<AiModelResponse>> fetchModelsWithKey(
            @Valid @RequestBody FetchModelsRequest request) {
        return ResponseEntity.ok(providerService.fetchModelsWithKey(
                request.provider(),
                request.apiKey(),
                request.baseUrl()
        ));
    }

    public record FetchModelsRequest(String provider, String apiKey, String baseUrl) {}
}
