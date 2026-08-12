package com.aiinpocket.n3n.ai.billing;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 單一 AI 供應商憑證的餘額/配額查詢結果。
 *
 * kind:
 * - BALANCE:    供應商提供餘額 API，balance/currency 有值（OpenRouter、fal.ai）
 * - QUOTA:      供應商提供配額 API，quotaUsed/quotaLimit 有值（ElevenLabs 字元數）
 * - USAGE_ONLY: 供應商無餘額 API，只能顯示本地累計用量估算（OpenAI、Anthropic、Gemini）
 * - ERROR:      查詢失敗，error 有值
 */
@Data
@Builder
public class ProviderBalanceDto {

    private UUID credentialId;
    private String credentialName;
    private String provider;
    private String kind;

    private Double balance;
    private String currency;

    private Long quotaUsed;
    private Long quotaLimit;
    private String quotaUnit;

    /** 本地累計估算花費（USD），USAGE_ONLY 時提供 */
    private Double localSpentUsd;

    private String error;
}
