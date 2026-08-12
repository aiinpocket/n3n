package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.service.AiProviderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 一般成員的 AI 可用性查詢。
 *
 * AI Provider 金鑰為平台共用、僅管理員可管理（/api/ai/providers/** 需 ADMIN），
 * 此端點讓所有登入成員知道「AI 功能是否可用」，不揭露任何秘密。
 */
@RestController
@RequestMapping("/api/ai/availability")
@RequiredArgsConstructor
@Tag(name = "AI Availability", description = "Whether platform AI is configured (no secrets)")
public class AiAvailabilityController {

    private final AiProviderService providerService;

    @GetMapping
    public ResponseEntity<AiProviderService.AiAvailabilityResponse> getAvailability(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(providerService.getAvailability(userId));
    }
}
