package com.aiinpocket.n3n.ai.billing;

import com.aiinpocket.n3n.ai.repository.AiTokenUsageRepository;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.service.CredentialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 查詢各 AI 供應商的剩餘餘額/配額（平台範圍，管理員專用）。
 *
 * AI 金鑰為平台共用：掃描平台上所有 AI 相關憑證，依供應商呼叫對應的官方餘額 API；
 * 不支援餘額查詢的供應商改以本地 token 用量估算已花費金額。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderBalanceService {

    /** 憑證類型 → 供應商識別（涵蓋 CredentialTypeSeeder 與 AiProviderService 兩種命名） */
    private static final Map<String, String> CREDENTIAL_TYPE_TO_PROVIDER = Map.ofEntries(
            Map.entry("openrouter", "openrouter"),
            Map.entry("ai_openrouter", "openrouter"),
            Map.entry("fal", "fal"),
            Map.entry("elevenlabs", "elevenlabs"),
            Map.entry("openai", "openai"),
            Map.entry("ai_openai", "openai"),
            Map.entry("anthropic", "claude"),
            Map.entry("ai_claude", "claude"),
            Map.entry("google", "gemini"),
            Map.entry("ai_gemini", "gemini")
    );

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final CredentialRepository credentialRepository;
    private final CredentialService credentialService;
    private final AiTokenUsageRepository tokenUsageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    /**
     * 查詢平台所有 AI 憑證的餘額狀態（管理員專用）。
     */
    public List<ProviderBalanceDto> getPlatformBalances() {
        List<Credential> credentials = credentialRepository.findByTypeIn(
                CREDENTIAL_TYPE_TO_PROVIDER.keySet());

        Map<String, Double> localSpent = computePlatformSpentByProvider();

        List<ProviderBalanceDto> results = new ArrayList<>();
        for (Credential credential : credentials) {
            String provider = CREDENTIAL_TYPE_TO_PROVIDER.get(credential.getType());
            results.add(fetchBalance(credential, provider, localSpent.get(provider)));
        }
        return results;
    }

    /**
     * 平台全域：依 provider+model 彙總最近 N 天的本地用量與估算成本。
     */
    public List<Map<String, Object>> getPlatformUsageSummary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = tokenUsageRepository.summarizePlatformByProviderAndModel(since);

        List<Map<String, Object>> summary = new ArrayList<>();
        for (Object[] row : rows) {
            String provider = (String) row[0];
            String model = (String) row[1];
            long callCount = ((Number) row[2]).longValue();
            long inputTokens = row[3] != null ? ((Number) row[3]).longValue() : 0;
            long outputTokens = row[4] != null ? ((Number) row[4]).longValue() : 0;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("provider", provider);
            entry.put("model", model);
            entry.put("callCount", callCount);
            entry.put("inputTokens", inputTokens);
            entry.put("outputTokens", outputTokens);
            entry.put("estimatedCostUsd", round(ModelPricing.estimateCostUsd(model, inputTokens, outputTokens)));
            summary.add(entry);
        }
        return summary;
    }

    /**
     * 平台全域：依成員彙總最近 N 天的用量與估算成本（管理員專用）。
     * 每列包含 userId / email / name / calls / tokens / estimatedCostUsd。
     */
    public List<Map<String, Object>> getUsageByUser(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = tokenUsageRepository.summarizeByUserAndModel(since);

        // 依 userId 合併（查詢按 user+model 分組，成本需要 model 單價）
        Map<UUID, UserUsageAccumulator> byUser = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            String model = (String) row[1];
            long callCount = ((Number) row[2]).longValue();
            long inputTokens = row[3] != null ? ((Number) row[3]).longValue() : 0;
            long outputTokens = row[4] != null ? ((Number) row[4]).longValue() : 0;

            UserUsageAccumulator acc = byUser.computeIfAbsent(userId, id -> new UserUsageAccumulator());
            acc.calls += callCount;
            acc.inputTokens += inputTokens;
            acc.outputTokens += outputTokens;
            acc.estimatedCostUsd += ModelPricing.estimateCostUsd(model, inputTokens, outputTokens);
        }

        // 批次載入成員資訊（email / name）
        Map<UUID, User> users = userRepository.findAllById(byUser.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<UUID, UserUsageAccumulator> entry : byUser.entrySet()) {
            User user = users.get(entry.getKey());
            UserUsageAccumulator acc = entry.getValue();

            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("userId", entry.getKey());
            rowMap.put("email", user != null ? user.getEmail() : null);
            rowMap.put("name", user != null ? user.getName() : null);
            rowMap.put("calls", acc.calls);
            rowMap.put("inputTokens", acc.inputTokens);
            rowMap.put("outputTokens", acc.outputTokens);
            rowMap.put("estimatedCostUsd", round(acc.estimatedCostUsd));
            result.add(rowMap);
        }
        result.sort((a, b) -> Double.compare(
                (double) b.get("estimatedCostUsd"), (double) a.get("estimatedCostUsd")));
        return result;
    }

    /** 成員用量累加器（僅服務內部使用） */
    private static final class UserUsageAccumulator {
        private long calls;
        private long inputTokens;
        private long outputTokens;
        private double estimatedCostUsd;
    }

    private Map<String, Double> computePlatformSpentByProvider() {
        Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
        Map<String, Double> spent = new LinkedHashMap<>();
        for (Object[] row : tokenUsageRepository.summarizePlatformByProviderAndModel(since)) {
            String provider = (String) row[0];
            String model = (String) row[1];
            long inputTokens = row[3] != null ? ((Number) row[3]).longValue() : 0;
            long outputTokens = row[4] != null ? ((Number) row[4]).longValue() : 0;
            spent.merge(provider, ModelPricing.estimateCostUsd(model, inputTokens, outputTokens), Double::sum);
        }
        return spent;
    }

    private ProviderBalanceDto fetchBalance(Credential credential,
                                            String provider, Double localSpentUsd) {
        ProviderBalanceDto.ProviderBalanceDtoBuilder builder = ProviderBalanceDto.builder()
                .credentialId(credential.getId())
                .credentialName(credential.getName())
                .provider(provider);

        String apiKey;
        try {
            // 以憑證擁有者身分解密（平台共用金鑰由建立它的管理員擁有）
            Map<String, Object> data = credentialService.getDecryptedData(
                    credential.getId(), credential.getOwnerId());
            Object key = data.get("apiKey");
            if (key == null) {
                key = data.get("key");
            }
            apiKey = key != null ? key.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to decrypt credential {}: {}", credential.getId(), e.getMessage());
            return builder.kind("ERROR").error("無法解密憑證").build();
        }

        if (apiKey == null || apiKey.isBlank()) {
            return builder.kind("ERROR").error("憑證中沒有 API Key").build();
        }

        try {
            return switch (provider) {
                case "openrouter" -> fetchOpenRouterBalance(builder, apiKey);
                case "fal" -> fetchFalBalance(builder, apiKey);
                case "elevenlabs" -> fetchElevenLabsQuota(builder, apiKey);
                default -> builder.kind("USAGE_ONLY")
                        .localSpentUsd(localSpentUsd != null ? round(localSpentUsd) : 0.0)
                        .build();
            };
        } catch (Exception e) {
            log.warn("Balance query failed for {} ({}): {}", provider, credential.getId(), e.getMessage());
            return builder.kind("ERROR").error("餘額查詢失敗，請確認 API Key 是否有效").build();
        }
    }

    private ProviderBalanceDto fetchOpenRouterBalance(
            ProviderBalanceDto.ProviderBalanceDtoBuilder builder, String apiKey) throws Exception {
        String body = webClientBuilder.build().get()
                .uri("https://openrouter.ai/api/v1/credits")
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        JsonNode data = objectMapper.readTree(body).path("data");
        double totalCredits = data.path("total_credits").asDouble(0);
        double totalUsage = data.path("total_usage").asDouble(0);
        return builder.kind("BALANCE")
                .balance(round(totalCredits - totalUsage))
                .currency("USD")
                .build();
    }

    private ProviderBalanceDto fetchFalBalance(
            ProviderBalanceDto.ProviderBalanceDtoBuilder builder, String apiKey) throws Exception {
        String body = webClientBuilder.build().get()
                .uri("https://api.fal.ai/v1/account/billing?expand=credits")
                .header("Authorization", "Key " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        JsonNode credits = objectMapper.readTree(body).path("credits");
        return builder.kind("BALANCE")
                .balance(round(credits.path("current_balance").asDouble(0)))
                .currency(credits.path("currency").asText("USD"))
                .build();
    }

    private ProviderBalanceDto fetchElevenLabsQuota(
            ProviderBalanceDto.ProviderBalanceDtoBuilder builder, String apiKey) throws Exception {
        String body = webClientBuilder.build().get()
                .uri("https://api.elevenlabs.io/v1/user/subscription")
                .header("xi-api-key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        JsonNode root = objectMapper.readTree(body);
        return builder.kind("QUOTA")
                .quotaUsed(root.path("character_count").asLong(0))
                .quotaLimit(root.path("character_limit").asLong(0))
                .quotaUnit("characters")
                .build();
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
