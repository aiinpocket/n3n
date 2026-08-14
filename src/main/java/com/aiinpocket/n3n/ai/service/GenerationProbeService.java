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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * AI 編排時的背景驗證：生成流程後由系統逐節點「真的執行一次」，
 * 把實際輸出往下游餵，讓編排結果有理有據而不是盲猜。
 *
 * 互動原則（不做風險阻擋、不留死路）：
 * - 缺資訊或節點有外部副作用時，即時透過 SSE 詢問使用者；
 *   使用者提供／確認後就「真的去打」（包含寄信等有副作用的動作——已取得使用者同意）。
 * - 使用者可選「先跳過這段，之後提供」：以模擬資料代替該節點輸出繼續往下游驗證，
 *   確保中間一個節點缺料不會讓後面整段斷鏈。
 * - 等待使用者回覆有時間上限，逾時自動跳過；等待期間送心跳保持 SSE 存活，
 *   串流結束時強制喚醒所有等待中的驗證緒，絕不留殭屍執行緒。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationProbeService {

    /** 單節點試打逾時（生成情境要快） */
    private static final long PER_NODE_TIMEOUT_SECONDS = 15;
    /** 整體「實際試打」時間預算（毫秒）——等待使用者的時間不計入 */
    private static final long TOTAL_BUDGET_MS = 90_000;
    /** 等待使用者提供資訊的上限（秒），逾時自動跳過該節點 */
    private static final long WAIT_FOR_INPUT_SECONDS = 180;
    /** 等待期間心跳間隔（秒），避免 SSE 串流因無輸出被逾時切斷 */
    private static final long WAIT_HEARTBEAT_SECONDS = 10;
    private static final int OUTPUT_SAMPLE_CHARS = 400;

    /**
     * 會對外部世界產生副作用的節點（寄信、發訊息、寫資料、跑指令等）。
     * 這些節點不會未經同意就背景執行——會先詢問使用者，確認後才真打。
     */
    private static final Set<String> SIDE_EFFECT_TYPES = Set.of(
        // 郵件與訊息
        "sendEmail", "email", "gmail", "slack", "discord", "telegram", "line", "whatsapp",
        "facebook", "instagram", "threads",
        // 資料庫與儲存寫入
        "database", "postgres", "mysql", "mongodb", "redis", "elasticsearch", "bigQuery",
        "writeFile", "googleCloudStorage", "googleDrive", "googleSheets", "googleCalendar",
        // 指令與遠端操作
        "executeCommand", "ssh", "ftp", "respondWebhook"
    );

    /** httpRequest 唯讀方法無副作用，可直接試打 */
    private static final Set<String> SAFE_HTTP_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final NodeProbeService nodeProbeService;
    private final NodeHandlerRegistry handlerRegistry;
    private final AssistantAiClient aiClient;
    private final ObjectMapper objectMapper;

    // ========== 互動 session ==========

    /** 使用者對單一節點詢問的回覆：skip=true 表示「先跳過這段，之後提供」 */
    public record UserInputResponse(boolean skip, Map<String, Object> config) {}

    /** 發給前端的詢問內容 */
    public record InputRequest(String sessionId, String nodeId, String nodeLabel, String nodeType,
                               String reason, boolean sideEffect, Map<String, Object> config) {}

    private static final class ProbeSession {
        final UUID userId;
        volatile boolean cancelled;
        final Map<String, CompletableFuture<UserInputResponse>> pending = new ConcurrentHashMap<>();

        ProbeSession(UUID userId) {
            this.userId = userId;
        }
    }

    private final Map<String, ProbeSession> sessions = new ConcurrentHashMap<>();

    /**
     * 前端提交詢問的回覆。回傳 false 表示 session/節點已不存在（逾時或串流已結束）。
     */
    public boolean submitInput(String sessionId, String nodeId, UUID userId,
                               boolean skip, Map<String, Object> config) {
        ProbeSession session = sessions.get(sessionId);
        if (session == null || !session.userId.equals(userId)) {
            return false;
        }
        CompletableFuture<UserInputResponse> future = session.pending.remove(nodeId);
        if (future == null) {
            return false;
        }
        return future.complete(new UserInputResponse(skip, config != null ? config : Map.of()));
    }

    /**
     * 串流結束（完成、取消、錯誤）時呼叫：喚醒所有等待中的驗證緒並移除 session，
     * 確保不會有執行緒卡在 future.get 變殭屍。
     */
    public void cancelSession(String sessionId) {
        ProbeSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.cancelled = true;
        session.pending.values().forEach(f -> f.complete(new UserInputResponse(true, Map.of())));
        session.pending.clear();
    }

    // ========== 驗證主流程 ==========

    /**
     * 單一節點的驗證結果。
     * status: verified（真打成功）/ needsInput（仍缺資訊，已以模擬資料續串）
     *         / skipped（使用者選擇之後提供，或時間預算用盡）
     */
    public record NodeVerification(String nodeId, String status, String message,
                                   long durationMs, String outputSample,
                                   Map<String, Object> repairedConfig) {}

    /**
     * 逐節點背景驗證整條生成的流程（互動式）。
     *
     * @param sessionId       互動 session 識別碼（前端以此提交 probe-input 回覆）
     * @param nodes           生成的節點（含 config），驗證中修正的設定會「就地更新」
     * @param onResult        每個節點驗完即回呼（供 SSE 即時推送）
     * @param onInputRequired 需要使用者提供資訊／確認副作用時回呼（發 node_input_required）
     * @param heartbeat       等待使用者期間的心跳回呼（保持 SSE 存活）
     */
    public List<NodeVerification> verifyFlow(UUID userId, String sessionId,
                                             List<Map<String, Object>> nodes,
                                             List<Map<String, String>> edges,
                                             Consumer<NodeVerification> onResult,
                                             Consumer<InputRequest> onInputRequired,
                                             Runnable heartbeat) {
        ProbeSession session = new ProbeSession(userId);
        sessions.put(sessionId, session);
        List<NodeVerification> results = new ArrayList<>();
        Map<String, Object> realOutputs = new HashMap<>();
        long probeBudgetUsedMs = 0;

        try {
            for (Map<String, Object> node : topologicalOrder(nodes, edges)) {
                String nodeId = String.valueOf(node.get("id"));
                String nodeType = String.valueOf(node.get("type"));
                String nodeLabel = String.valueOf(node.getOrDefault("label", nodeType));
                Map<String, Object> config = configOf(node);

                NodeVerification verification;
                long nodeStart = Instant.now().toEpochMilli();
                if (session.cancelled) {
                    verification = new NodeVerification(nodeId, "skipped",
                        "驗證已中止，此節點未試打", 0, null, null);
                } else if (probeBudgetUsedMs > TOTAL_BUDGET_MS) {
                    verification = new NodeVerification(nodeId, "skipped",
                        "驗證時間預算已用盡，此節點未試打", 0, null, null);
                } else if (hasSideEffect(nodeType, config)) {
                    verification = confirmAndProbe(userId, session, sessionId, nodeId, nodeLabel,
                        nodeType, config, realOutputs, node, onInputRequired, heartbeat);
                } else {
                    verification = probeWithRepair(userId, session, sessionId, nodeId, nodeLabel,
                        nodeType, config, realOutputs, node, onInputRequired, heartbeat);
                }
                // 只把「實際試打」時間計入預算；等待使用者不算（在 askUser 中另計）
                probeBudgetUsedMs += Math.max(0,
                    Instant.now().toEpochMilli() - nodeStart - lastWaitMs.get());
                lastWaitMs.set(0L);

                results.add(verification);
                try {
                    onResult.accept(verification);
                } catch (Exception e) {
                    log.debug("Probe result callback failed: {}", e.getMessage());
                }
            }
        } finally {
            cancelSession(sessionId);
            lastWaitMs.remove();
        }
        return results;
    }

    /** 本節點在 askUser 中花掉的等待時間（不計入試打預算） */
    private final ThreadLocal<Long> lastWaitMs = ThreadLocal.withInitial(() -> 0L);

    /**
     * 有副作用的節點：先做設定檢查，然後一律詢問使用者——
     * 確認（可補資訊）後「真的執行一次」；或先跳過用模擬資料續串。
     */
    private NodeVerification confirmAndProbe(UUID userId, ProbeSession session, String sessionId,
                                             String nodeId, String nodeLabel, String nodeType,
                                             Map<String, Object> config,
                                             Map<String, Object> realOutputs,
                                             Map<String, Object> node,
                                             Consumer<InputRequest> onInputRequired,
                                             Runnable heartbeat) {
        String reason = "此節點會實際產生外部動作（例如真的寄出郵件、寫入資料）。"
            + "確認或補齊設定後系統會真的執行一次驗證；也可以先跳過，之後再提供。";
        String handlerType = NodeProbeService.normalizeNodeType(nodeType);
        if (handlerRegistry.hasHandler(handlerType)) {
            try {
                ValidationResult validation = handlerRegistry.getHandler(handlerType).validateConfig(config);
                if (!validation.isValid()) {
                    reason = "設定不完整：" + validation.getErrors() + "。補齊後系統會真的執行一次驗證；也可以先跳過，之後再提供。";
                }
            } catch (Exception e) {
                log.debug("validateConfig failed for {}: {}", nodeType, e.getMessage());
            }
        }

        UserInputResponse response = askUser(session, sessionId, nodeId, nodeLabel, nodeType,
            reason, true, config, onInputRequired, heartbeat);
        if (response == null || response.skip()) {
            return skipWithMock(userId, nodeId, nodeType, config, realOutputs,
                response == null ? "等候回覆逾時，已自動跳過並以模擬資料繼續驗證下游"
                                 : "已先跳過（之後提供），以模擬資料繼續驗證下游");
        }

        Map<String, Object> merged = mergeConfig(config, response.config());
        NodeProbeService.ProbeResult result = nodeProbeService.probe(
            userId, nodeType, nodeId, merged, realOutputs, PER_NODE_TIMEOUT_SECONDS);
        if (result.success()) {
            node.put("config", merged);
            realOutputs.put(nodeId, result.output());
            return new NodeVerification(nodeId, "verified",
                "已依你提供的資訊實際執行成功", result.durationMs(), sample(result.output()), merged);
        }
        // 真打失敗：仍以模擬資料續串，避免下游斷鏈
        realOutputs.put(nodeId, mockOutput(userId, nodeType, merged));
        node.put("config", merged);
        return new NodeVerification(nodeId, "needsInput",
            friendlyReason(result.errorMessage()) + "（已以模擬資料繼續驗證下游）",
            result.durationMs(), null, merged);
    }

    /**
     * 無副作用節點：直接真打；失敗先讓 AI 修一次，仍失敗就問使用者，
     * 提供後再真打；跳過或再失敗都以模擬資料續串。
     */
    private NodeVerification probeWithRepair(UUID userId, ProbeSession session, String sessionId,
                                             String nodeId, String nodeLabel, String nodeType,
                                             Map<String, Object> config,
                                             Map<String, Object> realOutputs,
                                             Map<String, Object> node,
                                             Consumer<InputRequest> onInputRequired,
                                             Runnable heartbeat) {
        NodeProbeService.ProbeResult result = nodeProbeService.probe(
            userId, nodeType, nodeId, config, realOutputs, PER_NODE_TIMEOUT_SECONDS);

        if (result.success()) {
            realOutputs.put(nodeId, result.output());
            return new NodeVerification(nodeId, "verified", null,
                result.durationMs(), sample(result.output()), null);
        }

        Map<String, Object> repaired = attemptRepair(userId, nodeType, config,
            result.errorMessage(), realOutputs);
        if (repaired != null) {
            NodeProbeService.ProbeResult retry = nodeProbeService.probe(
                userId, nodeType, nodeId, repaired, realOutputs, PER_NODE_TIMEOUT_SECONDS);
            if (retry.success()) {
                node.put("config", repaired);
                realOutputs.put(nodeId, retry.output());
                return new NodeVerification(nodeId, "verified",
                    "AI 已自動修正設定並驗證通過", retry.durationMs(),
                    sample(retry.output()), repaired);
            }
        }

        // AI 修不好：問使用者
        UserInputResponse response = askUser(session, sessionId, nodeId, nodeLabel, nodeType,
            friendlyReason(result.errorMessage()), false, config, onInputRequired, heartbeat);
        if (response != null && !response.skip()) {
            Map<String, Object> merged = mergeConfig(config, response.config());
            NodeProbeService.ProbeResult retry = nodeProbeService.probe(
                userId, nodeType, nodeId, merged, realOutputs, PER_NODE_TIMEOUT_SECONDS);
            if (retry.success()) {
                node.put("config", merged);
                realOutputs.put(nodeId, retry.output());
                return new NodeVerification(nodeId, "verified",
                    "已依你提供的資訊實際執行成功", retry.durationMs(), sample(retry.output()), merged);
            }
            realOutputs.put(nodeId, mockOutput(userId, nodeType, merged));
            node.put("config", merged);
            return new NodeVerification(nodeId, "needsInput",
                friendlyReason(retry.errorMessage()) + "（已以模擬資料繼續驗證下游）",
                retry.durationMs(), null, merged);
        }

        return skipWithMock(userId, nodeId, nodeType, config, realOutputs,
            response == null ? "等候回覆逾時，已自動跳過並以模擬資料繼續驗證下游"
                             : "已先跳過（之後提供），以模擬資料繼續驗證下游");
    }

    /**
     * 詢問使用者並等待回覆。回傳 null 代表逾時；等待期間每 10 秒送一次心跳。
     * session 已取消時立即回傳 skip，不進入等待。
     */
    private UserInputResponse askUser(ProbeSession session, String sessionId, String nodeId,
                                      String nodeLabel, String nodeType, String reason,
                                      boolean sideEffect, Map<String, Object> config,
                                      Consumer<InputRequest> onInputRequired, Runnable heartbeat) {
        if (session.cancelled || onInputRequired == null) {
            return new UserInputResponse(true, Map.of());
        }
        CompletableFuture<UserInputResponse> future = new CompletableFuture<>();
        session.pending.put(nodeId, future);
        long waitStart = Instant.now().toEpochMilli();
        try {
            onInputRequired.accept(new InputRequest(sessionId, nodeId, nodeLabel, nodeType,
                reason, sideEffect, config));
            long waitedSeconds = 0;
            while (waitedSeconds < WAIT_FOR_INPUT_SECONDS) {
                try {
                    return future.get(WAIT_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    waitedSeconds += WAIT_HEARTBEAT_SECONDS;
                    if (session.cancelled) {
                        return new UserInputResponse(true, Map.of());
                    }
                    if (heartbeat != null) {
                        try {
                            heartbeat.run();
                        } catch (Exception ignored) {
                            // 心跳失敗不影響等待
                        }
                    }
                }
            }
            return null; // 逾時
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new UserInputResponse(true, Map.of());
        } catch (Exception e) {
            log.debug("askUser failed: {}", e.getMessage());
            return new UserInputResponse(true, Map.of());
        } finally {
            session.pending.remove(nodeId);
            lastWaitMs.set(lastWaitMs.get() + (Instant.now().toEpochMilli() - waitStart));
        }
    }

    /** 跳過節點：產生模擬輸出餵給下游，讓後面還能繼續串接驗證。 */
    private NodeVerification skipWithMock(UUID userId, String nodeId, String nodeType,
                                          Map<String, Object> config,
                                          Map<String, Object> realOutputs, String message) {
        realOutputs.put(nodeId, mockOutput(userId, nodeType, config));
        return new NodeVerification(nodeId, "skipped", message, 0, null, null);
    }

    /**
     * 產生節點成功執行時的示範輸出（AI 一次呼叫，失敗時退回簡單占位），
     * 一律標記 _mock=true 讓下游與使用者能辨識。
     */
    private Map<String, Object> mockOutput(UUID userId, String nodeType, Map<String, Object> config) {
        Map<String, Object> mock = new HashMap<>();
        mock.put("_mock", true);
        try {
            String prompt = """
                節點類型：%s
                節點設定：%s

                請產生此節點「成功執行一次」時最可能的輸出 JSON（欄位要貼近真實情境）。
                只回傳 JSON 物件，不要任何說明文字。
                """.formatted(nodeType, toJson(config, 1200));
            String answer = aiClient.chat(prompt,
                "你是流程節點輸出模擬器，只輸出 JSON。", 600, 0.3, userId).trim();
            String json = answer.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            @SuppressWarnings("unchecked")
            Map<String, Object> generated = objectMapper.readValue(json, Map.class);
            mock.putAll(generated);
        } catch (Exception e) {
            log.debug("Mock output generation failed: {}", e.getMessage());
            mock.put("note", "使用者選擇之後提供，以模擬資料代替此節點輸出");
        }
        return mock;
    }

    private Map<String, Object> mergeConfig(Map<String, Object> base, Map<String, Object> provided) {
        Map<String, Object> merged = new HashMap<>(base);
        if (provided != null) {
            merged.putAll(provided);
        }
        return merged;
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

    /** 判斷節點是否會對外部世界產生副作用（需要使用者確認後才真打）。 */
    static boolean hasSideEffect(String nodeType, Map<String, Object> config) {
        String handlerType = NodeProbeService.normalizeNodeType(nodeType);
        if (SIDE_EFFECT_TYPES.contains(handlerType)) {
            return true;
        }
        if ("httpRequest".equals(handlerType)) {
            String method = String.valueOf(config.getOrDefault("method", "GET")).toUpperCase();
            return !SAFE_HTTP_METHODS.contains(method);
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
