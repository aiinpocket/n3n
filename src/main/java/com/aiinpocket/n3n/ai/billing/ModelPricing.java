package com.aiinpocket.n3n.ai.billing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 各模型每百萬 token 的美元價格表，用於本地用量成本估算。
 * 供應商不提供餘額 API 時（OpenAI / Anthropic / Gemini 直連），
 * 管理頁面以此估算已花費金額。價格若有異動請更新此表。
 */
public final class ModelPricing {

    private ModelPricing() {
    }

    /** [input price per 1M tokens, output price per 1M tokens] in USD */
    private static final Map<String, double[]> PRICES = new LinkedHashMap<>();

    static {
        // OpenAI
        PRICES.put("gpt-5", new double[]{1.25, 10.0});
        PRICES.put("gpt-5-mini", new double[]{0.25, 2.0});
        PRICES.put("gpt-5-nano", new double[]{0.05, 0.4});
        PRICES.put("gpt-4o", new double[]{2.5, 10.0});
        PRICES.put("gpt-4o-mini", new double[]{0.15, 0.6});
        PRICES.put("gpt-4-turbo", new double[]{10.0, 30.0});
        PRICES.put("gpt-3.5-turbo", new double[]{0.5, 1.5});
        // Anthropic
        PRICES.put("claude-opus", new double[]{15.0, 75.0});
        PRICES.put("claude-sonnet", new double[]{3.0, 15.0});
        PRICES.put("claude-haiku", new double[]{0.8, 4.0});
        PRICES.put("claude-3-5-sonnet", new double[]{3.0, 15.0});
        PRICES.put("claude-3-opus", new double[]{15.0, 75.0});
        PRICES.put("claude-3-haiku", new double[]{0.25, 1.25});
        // Google Gemini
        PRICES.put("gemini-2.5-pro", new double[]{1.25, 10.0});
        PRICES.put("gemini-2.5-flash", new double[]{0.3, 2.5});
        PRICES.put("gemini-1.5-pro", new double[]{1.25, 5.0});
        PRICES.put("gemini-1.5-flash", new double[]{0.075, 0.3});
    }

    private static final double[] DEFAULT_PRICE = {1.0, 3.0};

    /**
     * 以最長前綴比對估算成本（USD）。未知模型使用保守預設價。
     */
    public static double estimateCostUsd(String model, long inputTokens, long outputTokens) {
        double[] price = lookup(model);
        return (inputTokens / 1_000_000.0) * price[0] + (outputTokens / 1_000_000.0) * price[1];
    }

    private static double[] lookup(String model) {
        if (model == null) {
            return DEFAULT_PRICE;
        }
        String normalized = model.toLowerCase();
        // OpenRouter 模型 ID 格式為 "vendor/model"，取斜線後段比對
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        double[] best = null;
        int bestLen = -1;
        for (Map.Entry<String, double[]> entry : PRICES.entrySet()) {
            if (normalized.startsWith(entry.getKey()) && entry.getKey().length() > bestLen) {
                best = entry.getValue();
                bestLen = entry.getKey().length();
            }
        }
        return best != null ? best : DEFAULT_PRICE;
    }
}
