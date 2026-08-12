package com.aiinpocket.n3n.ai.billing;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 帳務 API：各供應商餘額查詢 + 本地用量成本估算。
 */
@RestController
@RequestMapping("/api/ai/billing")
@RequiredArgsConstructor
@Tag(name = "AI Billing", description = "AI provider balance and usage tracking")
public class AiBillingController {

    private final ProviderBalanceService balanceService;

    /**
     * 查詢使用者所有 AI 憑證的餘額/配額/本地用量估算。
     */
    @GetMapping("/balances")
    public ResponseEntity<List<ProviderBalanceDto>> getBalances(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(balanceService.getBalances(userId));
    }

    /**
     * 最近 N 天的 token 用量彙總（依 provider + model）。
     */
    @GetMapping("/usage")
    public ResponseEntity<List<Map<String, Object>>> getUsage(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "30") int days) {
        UUID userId = UUID.fromString(user.getUsername());
        int clampedDays = Math.max(1, Math.min(days, 365));
        return ResponseEntity.ok(balanceService.getUsageSummary(userId, clampedDays));
    }
}
