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
import java.util.Comparator;
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

    /** 詢問中要使用者填寫的單一欄位（白話標籤與範例，非技術 key） */
    public record InputField(String key, String label, String hint, String example) {}

    /**
     * 發給前端的詢問內容。question 是給一般使用者看的白話說明，
     * fields 是白話標籤的填寫欄位；reason 保留技術原因供除錯。
     */
    public record InputRequest(String sessionId, String nodeId, String nodeLabel, String nodeType,
                               String reason, boolean sideEffect, Map<String, Object> config,
                               String question, List<InputField> fields) {}

    /** 常見設定鍵的白話對照（AI 產生失敗時的 fallback） */
    private static final Map<String, String[]> FRIENDLY_FIELD_LABELS = Map.ofEntries(
        Map.entry("to", new String[]{"收件人 Email", "要把信寄給誰", "name@example.com"}),
        Map.entry("subject", new String[]{"信件主旨", "郵件的標題", "每日資料摘要"}),
        Map.entry("body", new String[]{"信件內容", "郵件正文要寫什麼", "您好，附上今日摘要…"}),
        Map.entry("url", new String[]{"網址", "要連到哪個網頁或 API", "https://example.com/api"}),
        Map.entry("method", new String[]{"HTTP 方法", "GET 讀取、POST 送出資料", "GET"}),
        Map.entry("provider", new String[]{"AI 服務商", "要用哪家 AI（openai / claude / gemini）", "openai"}),
        Map.entry("model", new String[]{"AI 模型", "要用的模型名稱", "gpt-4o-mini"}),
        Map.entry("messages", new String[]{"要給 AI 的指示", "想請 AI 做什麼", "幫我把資料整理成摘要"}),
        Map.entry("prompt", new String[]{"要給 AI 的指示", "想請 AI 做什麼", "幫我把資料整理成摘要"}),
        Map.entry("apiKey", new String[]{"API 金鑰", "服務提供的存取金鑰", "sk-…"}),
        Map.entry("credentialId", new String[]{"憑證", "在「憑證管理」建立後選擇", ""}),
        Map.entry("host", new String[]{"主機位址", "伺服器的網址或 IP", "db.example.com"}),
        Map.entry("channel", new String[]{"頻道", "要發到哪個頻道", "#general"}),
        Map.entry("token", new String[]{"存取權杖", "服務提供的 Token", ""})
    );

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
        return verifyFlow(userId, sessionId, "zh-TW", nodes, edges, onResult, onInputRequired, heartbeat);
    }

    /**
     * @param language 詢問使用者時的語言（口語化問題與欄位標籤以此語言產生）
     */
    public List<NodeVerification> verifyFlow(UUID userId, String sessionId, String language,
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
                    verification = confirmAndProbe(userId, session, sessionId, language, nodeId, nodeLabel,
                        nodeType, config, realOutputs, node, onInputRequired, heartbeat);
                } else {
                    verification = probeWithRepair(userId, session, sessionId, language, nodeId, nodeLabel,
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
                                             String language,
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

        UserInputResponse response = askUser(userId, session, sessionId, language, nodeId, nodeLabel,
            nodeType, reason, true, config, onInputRequired, heartbeat);
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
                                             String language,
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
        UserInputResponse response = askUser(userId, session, sessionId, language, nodeId, nodeLabel,
            nodeType, friendlyReason(result.errorMessage()), false, config, onInputRequired, heartbeat);
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
    private UserInputResponse askUser(UUID userId, ProbeSession session, String sessionId,
                                      String language, String nodeId,
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
            FriendlyAsk friendly = buildFriendlyAsk(userId, language, nodeLabel, nodeType,
                config, reason, sideEffect);
            onInputRequired.accept(new InputRequest(sessionId, nodeId, nodeLabel, nodeType,
                reason, sideEffect, config, friendly.question(), friendly.fields()));
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

    private record FriendlyAsk(String question, List<InputField> fields) {}

    /**
     * 把技術性的錯誤與設定鍵，轉成一般使用者看得懂的白話問題與填寫欄位。
     * 先請 AI 產生（依使用者語言）；失敗時退回靜態白話對照表。
     */
    private FriendlyAsk buildFriendlyAsk(UUID userId, String language, String nodeLabel,
                                         String nodeType, Map<String, Object> config,
                                         String technicalReason, boolean sideEffect) {
        try {
            String prompt = """
                你在協助一位完全不懂技術的使用者完成自動化流程。系統在設定「%s」這個步驟（節點類型 %s）時需要使用者補充資訊。
                目前設定：%s
                技術原因：%s
                %s

                只能問「只有使用者本人才知道」的資訊，例如：通知要寄給誰、要抓哪個網址、
                要用哪個帳號、想產生什麼內容。

                絕對不要問這些——它們是系統自己的事，使用者不會知道也無從回答：
                - 伺服器或執行環境的設定（流程跑在雲端伺服器上，不是使用者的電腦）
                - 任何程式的安裝路徑、執行檔位置、連接埠
                - 使用者本機是否安裝了什麼軟體
                - 內部技術參數（逾時秒數、重試次數、headless 模式等）

                如果這個步驟缺的只是上述系統面的東西，就不要編出欄位來問：
                回傳 {"question":"...","fields":[]}，question 直接說明這個做法在目前環境不可行、
                建議改用別的方式。

                請用 %s 產生：
                1. question：一到兩句白話說明「需要使用者提供什麼、為什麼」，不要出現技術行話、錯誤代碼、JSON 等字眼。
                2. fields：使用者需要填寫或確認的欄位（最多 5 個），每個欄位給白話名稱、提示與範例。

                只回傳 JSON，格式：
                {"question":"...","fields":[{"key":"對應的設定鍵","label":"白話名稱","hint":"要填什麼的說明","example":"範例值"}]}
                """.formatted(nodeLabel, nodeType, toJson(config, 1200),
                    technicalReason == null ? "缺少必要資訊" : technicalReason,
                    sideEffect ? "注意：此步驟會實際對外執行動作（例如寄出郵件），question 要提醒使用者確認後會真的執行一次。" : "",
                    language == null || language.isBlank() ? "zh-TW" : language);
            String answer = aiClient.chat(prompt,
                "你是把技術設定翻譯成白話的助手，只輸出 JSON。", 900, 0.3, userId).trim();
            String json = answer.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            Map<String, Object> parsed = objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String question = parsed.get("question") instanceof String q && !q.isBlank() ? q : null;
            List<InputField> fields = new ArrayList<>();
            if (parsed.get("fields") instanceof List<?> rawFields) {
                for (Object item : rawFields) {
                    if (item instanceof Map<?, ?> f && fields.size() < 5) {
                        Object label = f.get("label") != null ? f.get("label") : f.get("key");
                        fields.add(new InputField(
                            String.valueOf(f.get("key")),
                            String.valueOf(label),
                            f.get("hint") != null ? String.valueOf(f.get("hint")) : "",
                            f.get("example") != null ? String.valueOf(f.get("example")) : ""));
                    }
                }
            }
            if (question != null && !fields.isEmpty()) {
                return new FriendlyAsk(question, fields);
            }
        } catch (Exception e) {
            log.debug("Friendly ask generation failed: {}", e.getMessage());
        }
        return fallbackFriendlyAsk(nodeType, nodeLabel, config, sideEffect);
    }

    /**
     * AI 不可用時的白話 fallback。
     *
     * <p>兩個原則：缺值的欄位排在前面（那才是真正擋住執行的東西），
     * 標籤一律用人看得懂的字——查對照表，查不到就用節點 schema 的 title，
     * 再不行才把 camelCase 鍵名拆成單字。直接把 {@code operation}、{@code selector}
     * 這種鍵名丟給使用者，等於要他們自己去猜要填什麼。
     */
    private FriendlyAsk fallbackFriendlyAsk(String nodeType, String nodeLabel,
                                            Map<String, Object> config, boolean sideEffect) {
        Map<String, Object> properties = schemaProperties(nodeType);

        // 缺值的欄位優先：使用者最該處理的是這些
        List<String> keys = new ArrayList<>(config.keySet());
        keys.sort(Comparator.comparing(k -> hasValue(config.get(k))));

        List<InputField> fields = new ArrayList<>();
        boolean anyMissing = false;
        for (String key : keys) {
            if (fields.size() >= 5) break;
            if (!hasValue(config.get(key))) {
                anyMissing = true;
            }
            fields.add(describeField(key, properties));
        }

        String question;
        if (sideEffect) {
            question = "「" + nodeLabel + "」這一步會實際對外執行（例如真的寄出郵件）。"
                + "請確認下面的內容沒問題，或補齊缺少的欄位；也可以先跳過，之後再設定。";
        } else if (anyMissing) {
            question = "「" + nodeLabel + "」這一步還缺一些資訊才能運作，"
                + "請幫忙補齊下面的欄位；也可以先跳過，之後再設定。";
        } else {
            // 欄位都有值卻仍試打失敗：不要說「還缺資訊」，那會讓使用者對著填好的欄位發呆
            question = "「" + nodeLabel + "」這一步試跑時沒有成功。"
                + "下面是目前的設定，你可以調整後再試一次；也可以先跳過，之後再設定。";
        }
        return new FriendlyAsk(question, fields);
    }

    /** 把設定鍵轉成使用者看得懂的欄位說明。 */
    private InputField describeField(String key, Map<String, Object> properties) {
        String[] friendly = FRIENDLY_FIELD_LABELS.get(key);
        if (friendly != null) {
            return new InputField(key, friendly[0], friendly[1], friendly[2]);
        }
        if (properties.get(key) instanceof Map<?, ?> schema) {
            String title = schema.get("title") instanceof String s && !s.isBlank() ? s : humanize(key);
            String hint = schema.get("description") instanceof String d && !d.isBlank() ? d : "";
            String example = schema.get("x-placeholder") instanceof String p && !p.isBlank() ? p : "";
            return new InputField(key, title, hint, example);
        }
        return new InputField(key, humanize(key), "", "");
    }

    /** camelCase／snake_case 鍵名拆成空白分隔的單字，至少不再是一團 code。 */
    private String humanize(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String spaced = key.replace('_', ' ')
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .trim();
        return spaced.isEmpty() ? key : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (value instanceof List<?> l) {
            return !l.isEmpty();
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaProperties(String nodeType) {
        try {
            return handlerRegistry.findHandler(nodeType)
                .map(handler -> handler.getConfigSchema())
                .filter(schema -> schema.get("properties") instanceof Map)
                .map(schema -> (Map<String, Object>) schema.get("properties"))
                .orElse(Map.of());
        } catch (Exception e) {
            log.debug("No config schema for {}: {}", nodeType, e.getMessage());
            return Map.of();
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
