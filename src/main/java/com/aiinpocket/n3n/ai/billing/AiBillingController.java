package com.aiinpocket.n3n.ai.billing;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 帳務 API（管理員專用）：平台各供應商餘額查詢 + 全平台用量成本估算。
 * AI 金鑰為平台共用，帳務也以平台為範圍（SecurityConfig 另以路徑規則加固）。
 */
@RestController
@RequestMapping("/api/ai/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI Billing", description = "Platform AI provider balance and usage tracking (admin only)")
public class AiBillingController {

    private final ProviderBalanceService balanceService;

    /**
     * 查詢平台所有 AI 憑證的餘額/配額/本地用量估算。
     */
    @GetMapping("/balances")
    public ResponseEntity<List<ProviderBalanceDto>> getBalances() {
        return ResponseEntity.ok(balanceService.getPlatformBalances());
    }

    /**
     * 最近 N 天的平台 token 用量彙總（依 provider + model）。
     */
    @GetMapping("/usage")
    public ResponseEntity<List<Map<String, Object>>> getUsage(
            @RequestParam(defaultValue = "30") int days) {
        int clampedDays = Math.max(1, Math.min(days, 365));
        return ResponseEntity.ok(balanceService.getPlatformUsageSummary(clampedDays));
    }

    /**
     * 最近 N 天的成員用量彙總（依使用者，含 email / name / 估算成本）。
     */
    @GetMapping("/usage/by-user")
    public ResponseEntity<List<Map<String, Object>>> getUsageByUser(
            @RequestParam(defaultValue = "30") int days) {
        int clampedDays = Math.max(1, Math.min(days, 365));
        return ResponseEntity.ok(balanceService.getUsageByUser(clampedDays));
    }
}
