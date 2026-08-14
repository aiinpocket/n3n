package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import com.aiinpocket.n3n.execution.service.NodeProbeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AI 編排時的背景驗證：生成流程後由系統逐節點「真的執行一次」，
 * 把實際輸出往下游餵，讓編排結果有理有據而不是盲猜。
 *
 * 安全原則：
 * - 只有「無外部副作用」的節點會真打（HTTP GET、程式碼、資料轉換、AI 呼叫等）。
 * - 會寄信、發訊息、寫資料庫、跑指令的節點絕不背景執行——只做設定驗證，
 *   標示為 skipped 請使用者手動測試。
 * - 試打失敗時讓 AI 帶著錯誤訊息自動修一次設定再重試；仍失敗就標示缺什麼。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationProbeService {

    /** 單節點試打逾時（生成情境要快） */
    private static final long PER_NODE_TIMEOUT_SECONDS = 15;
    /** 整體驗證時間預算（毫秒），超過就跳過剩餘節點 */
    private static final long TOTAL_BUDGET_MS = 90_000;
    private static final int OUTPUT_SAMPLE_CHARS = 400;

    /**
     * 無外部副作用、可安全背景試打的節點類型。
     * 保守原則：不在名單內一律不打。
     */
    private static final Set<String> SAFE_TYPES = Set.of(
        // 觸發（回模擬 payload，無副作用）
        "trigger", "scheduleTrigger", "webhookTrigger", "formTrigger", "emailTrigger", "errorTrigger",
        // 程式與資料轉換
        "code", "setFields", "json", "xml", "text", "regex", "markdown", "html", "urlParser",
        "base64", "crypto", "datetime", "convertFile", "spreadsheet", "itemLists", "renameKeys",
        "removeDuplicates", "compareDatasets", "filter", "sort", "merge", "splitOut", "aggregate",
        "condition", "switch", "noOp", "output", "jwt",
        // 讀取類
        "readFile", "browser",
        // AI 文字呼叫（有 token 成本但無外部副作用）
        "aiChat", "aiChain", "aiTransform", "aiRouter", "aiTextSplitter", "aiEmbedding",
        "openai", "claude", "gemini"
    );

    /** httpRequest 只在唯讀方法時安全 */
    private static final Set<String> SAFE_HTTP_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final NodeProbeService nodeProbeService;
    private final NodeHandlerRegistry handlerRegistry;
    private final AssistantAiClient aiClient;
    private final ObjectMapper objectMapper;

    /**
     * 單一節點的驗證結果。
     * status: verified（真打成功）/ needsInput（失敗且需要使用者提供資訊）
     *         / skipped（有副作用不背景打，或時間預算用盡）
     */
    public record NodeVerification(String nodeId, String status, String message,
                                   long durationMs, String outputSample,
                                   Map<String, Object> repairedConfig) {}

    /**
     * 逐節點背景驗證整條生成的流程。
     *
     * @param nodes    生成的節點（含 config），驗證中若 AI 修好設定會「就地更新」config
     * @param edges    邊（source/target）
     * @param onResult 每個節點驗完即回呼（供 SSE 串流即時推送）
     */
    public List<NodeVerification> verifyFlow(UUID userId,
                                             List<Map<String, Object>> nodes,
                                             List<Map<String, String>> edges,
                                             Consumer<NodeVerification> onResult) {
        List<NodeVerification> results = new ArrayList<>();
        Map<String, Object> realOutputs = new HashMap<>();
        long startedAt = Instant.now().toEpochMilli();

        for (Map<String, Object> node : topologicalOrder(nodes, edges)) {
            String nodeId = String.valueOf(node.get("id"));
            String nodeType = String.valueOf(node.get("type"));
            Map<String, Object> config = configOf(node);

            NodeVerification verification;
            if (Instant.now().toEpochMilli() - startedAt > TOTAL_BUDGET_MS) {
                verification = new NodeVerification(nodeId, "skipped",
                    "驗證時間預算已用盡，此節點未試打", 0, null, null);
            } else if (!isSafeToProbe(nodeType, config)) {
                verification = validateOnly(nodeId, nodeType, config);
            } else {
                verification = probeWithRepair(userId, nodeId, nodeType, config, realOutputs, node);
            }

            results.add(verification);
            try {
                onResult.accept(verification);
            } catch (Exception e) {
                log.debug("Probe result callback failed: {}", e.getMessage());
            }
        }
        return results;
    }

    /** 有副作用的節點：不執行，只跑 handler 的設定驗證。 */
    private NodeVerification validateOnly(String nodeId, String nodeType, Map<String, Object> config) {
        String handlerType = NodeProbeService.normalizeNodeType(nodeType);
        if (!handlerRegistry.hasHandler(handlerType)) {
            return new NodeVerification(nodeId, "skipped",
                "未知節點類型，未驗證", 0, null, null);
        }
        try {
            ValidationResult validation = handlerRegistry.getHandler(handlerType).validateConfig(config);
            if (validation.isValid()) {
                return new NodeVerification(nodeId, "skipped",
                    "此節點有外部副作用（不背景執行），設定格式檢查通過，請於節點面板手動測試", 0, null, null);
            }
            return new NodeVerification(nodeId, "needsInput",
                "設定不完整：" + validation.getErrors(), 0, null, null);
        } catch (Exception e) {
            return new NodeVerification(nodeId, "skipped",
                "設定驗證異常：" + e.getMessage(), 0, null, null);
        }
    }

    /** 安全節點：真打一次；失敗時 AI 修一次設定再重試。 */
    private NodeVerification probeWithRepair(UUID userId, String nodeId, String nodeType,
                                             Map<String, Object> config,
                                             Map<String, Object> realOutputs,
                                             Map<String, Object> node) {
        NodeProbeService.ProbeResult result = nodeProbeService.probe(
            userId, nodeType, nodeId, config, realOutputs, PER_NODE_TIMEOUT_SECONDS);

        if (!result.success()) {
            Map<String, Object> repaired = attemptRepair(userId, nodeType, config,
                result.errorMessage(), realOutputs);
            if (repaired != null) {
                NodeProbeService.ProbeResult retry = nodeProbeService.probe(
                    userId, nodeType, nodeId, repaired, realOutputs, PER_NODE_TIMEOUT_SECONDS);
                if (retry.success()) {
                    // 修好了：把修正後的設定寫回節點定義
                    node.put("config", repaired);
                    realOutputs.put(nodeId, retry.output());
                    return new NodeVerification(nodeId, "verified",
                        "AI 已自動修正設定並驗證通過", retry.durationMs(),
                        sample(retry.output()), repaired);
                }
            }
            return new NodeVerification(nodeId, "needsInput",
                friendlyReason(result.errorMessage()), result.durationMs(), null, null);
        }

        realOutputs.put(nodeId, result.output());
        return new NodeVerification(nodeId, "verified", null,
            result.durationMs(), sample(result.output()), null);
    }

    /** AI 修設定：帶錯誤訊息與上游輸出樣本，只回 JSON config。 */
    private Map<String, Object> attemptRepair(UUID userId, String nodeType,
                                              Map<String, Object> config, String error,
                                              Map<String, Object> realOutputs) {
        try {
            String prompt = """
                節點類型：%s
                目前設定：%s
                試打錯誤：%s
                上游節點的實際輸出（可用 {{ $node["節點id"].json.欄位 }} 引用）：%s

                請修正這個節點的設定讓它能成功執行。只回傳修正後的設定 JSON 物件，不要任何說明文字。
                如果錯誤是缺少憑證、金鑰或只有使用者知道的資訊，回傳字串 "CANNOT_FIX"。
                """.formatted(nodeType, toJson(config, 2000), error, toJson(realOutputs, 1500));
            String answer = aiClient.chat(prompt,
                "你是流程節點設定修復器，只輸出 JSON。", 1500, 0.2, userId).trim();
            if (answer.contains("CANNOT_FIX")) {
                return null;
            }
            String json = answer.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            @SuppressWarnings("unchecked")
            Map<String, Object> repaired = objectMapper.readValue(json, Map.class);
            return repaired.isEmpty() ? null : repaired;
        } catch (Exception e) {
            log.debug("Config repair attempt failed: {}", e.getMessage());
            return null;
        }
    }

    static boolean isSafeToProbe(String nodeType, Map<String, Object> config) {
        String handlerType = NodeProbeService.normalizeNodeType(nodeType);
        if (SAFE_TYPES.contains(handlerType)) {
            return true;
        }
        if ("httpRequest".equals(handlerType)) {
            String method = String.valueOf(config.getOrDefault("method", "GET")).toUpperCase();
            return SAFE_HTTP_METHODS.contains(method);
        }
        return false;
    }

    /** Kahn 拓撲排序；有環時剩餘節點按原順序附加。 */
    private List<Map<String, Object>> topologicalOrder(List<Map<String, Object>> nodes,
                                                       List<Map<String, String>> edges) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            byId.put(String.valueOf(node.get("id")), node);
        }
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        byId.keySet().forEach(id -> inDegree.put(id, 0));
        if (edges != null) {
            for (Map<String, String> edge : edges) {
                String source = edge.get("source");
                String target = edge.get("target");
                if (byId.containsKey(source) && byId.containsKey(target)) {
                    outgoing.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                    inDegree.merge(target, 1, Integer::sum);
                }
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        List<Map<String, Object>> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            visited.add(id);
            ordered.add(byId.get(id));
            for (String next : outgoing.getOrDefault(id, List.of())) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }
        byId.forEach((id, node) -> { if (!visited.contains(id)) ordered.add(node); });
        return ordered;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> configOf(Map<String, Object> node) {
        Object config = node.get("config");
        return config instanceof Map ? (Map<String, Object>) config : Map.of();
    }

    private String sample(Map<String, Object> output) {
        return toJson(output, OUTPUT_SAMPLE_CHARS);
    }

    private String toJson(Object value, int maxChars) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > maxChars ? json.substring(0, maxChars) + "…" : json;
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 把常見技術錯誤翻成使用者看得懂的原因。 */
    private String friendlyReason(String error) {
        if (error == null) return "試打失敗，需要更多資訊";
        String lower = error.toLowerCase();
        if (lower.contains("credential") || lower.contains("unauthorized") || lower.contains("401")
            || lower.contains("api key") || lower.contains("apikey")) {
            return "需要憑證或 API 金鑰：" + error;
        }
        if (lower.contains("validation failed") || lower.contains("required")) {
            return "設定不完整：" + error;
        }
        return error;
    }
}
