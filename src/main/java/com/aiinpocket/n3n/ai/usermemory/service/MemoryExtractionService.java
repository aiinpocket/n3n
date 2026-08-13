package com.aiinpocket.n3n.ai.usermemory.service;

import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.repository.UserMemoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 背景記憶萃取管線：對話進行中自動從近期訊息萃取「值得長期記住的使用者事實」，
 * 寫入 UserMemory（source = assistant）。
 *
 * 設計原則：
 * - 完全非同步、失敗只記 log，絕不影響聊天主流程
 * - 每個對話累積 N 則使用者訊息才觸發一次（節流）
 * - 萃取前先與既有記憶去重：內容包含或高度重疊時跳過或更新舊記憶，不重複插入
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryExtractionService {

    /** 一次最多萃取的記憶數 */
    static final int MAX_FACTS_PER_EXTRACTION = 3;

    /** 送給 LLM 的對話視窗：最近幾則訊息 */
    static final int EXTRACTION_WINDOW_MESSAGES = 10;

    /** token 重疊率超過此值視為重複 */
    static final double OVERLAP_THRESHOLD = 0.8;

    private static final String EXTRACTION_SYSTEM_PROMPT = """
        你是記憶萃取助手，從對話中找出「值得跨對話長期記住的使用者事實」。
        規則（務必遵守）：
        1. 最多輸出 3 筆，只留真正耐久的使用者層級事實：偏好（preference）、\
        反覆出現的需求或背景事實（fact）、正在進行的專案（project）、溝通風格（style）。
        2. 絕對不要記錄：密碼、API 金鑰、憑證、個資識別碼等敏感資訊；\
        一次性的任務細節；助手自己說的話；猜測或未經使用者確認的內容。
        3. 每筆 content 用一句話描述，使用對話中使用者所用的語言（例如繁體中文）。
        4. 只輸出「新資訊」；沒有值得記的就輸出空陣列 []。
        5. 嚴格輸出 JSON 陣列，不要加任何說明文字或 markdown 圍欄：
        [{"content": "...", "category": "preference|fact|project|style|general"}]
        """;

    private final AssistantAiClient aiClient;
    private final UserMemoryService userMemoryService;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;

    @Value("${n3n.ai.memory-extraction.enabled:true}")
    private boolean enabled;

    @Value("${n3n.ai.memory-extraction.every-n-messages:4}")
    private int everyNMessages;

    /** 每個對話自上次萃取以來的使用者訊息計數（記憶體內節流器） */
    private final Map<UUID, AtomicInteger> messageCounters = new ConcurrentHashMap<>();

    /** 節流器 map 的硬上限，避免長期運行下無限增長 */
    static final int MAX_TRACKED_CONVERSATIONS = 10_000;

    /**
     * 助手成功回覆後的非同步觸發點。
     * 由 AIAssistantService 在 chat / chatStream 成功儲存助手回應後呼叫。
     *
     * @param conversationId 對話 ID
     * @param userId         使用者 ID
     * @param recentMessages 近期對話訊息（role/content maps，含 user 與 assistant）
     */
    @Async
    public void onAssistantReply(UUID conversationId, UUID userId,
                                 List<Map<String, Object>> recentMessages) {
        try {
            if (!enabled || conversationId == null || userId == null) {
                return;
            }
            if (!shouldExtract(conversationId)) {
                return;
            }
            if (!aiClient.isAvailable(userId)) {
                log.debug("Memory extraction skipped: no AI provider configured for user {}", userId);
                return;
            }
            extractAndStore(userId, recentMessages);
        } catch (Exception e) {
            // 記憶萃取失敗絕不影響聊天流程
            log.warn("Memory extraction failed for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * 節流：每個對話每累積 N 則使用者訊息（= N 次助手回覆）才萃取一次。
     */
    boolean shouldExtract(UUID conversationId) {
        int n = Math.max(1, everyNMessages);
        // 硬上限保護：節流器只是計數器，超過上限直接清空避免記憶體無限增長。
        if (messageCounters.size() > MAX_TRACKED_CONVERSATIONS) {
            messageCounters.clear();
        }
        AtomicInteger counter = messageCounters.computeIfAbsent(conversationId, k -> new AtomicInteger());
        int count = counter.incrementAndGet();
        if (count >= n) {
            // 觸發後直接移除該筆，讓閒置對話不會殘留在 map 中（重置為 0 的等效行為）。
            messageCounters.remove(conversationId);
            return true;
        }
        return false;
    }

    /**
     * 呼叫 LLM 萃取記憶並寫入（含去重）。
     */
    void extractAndStore(UUID userId, List<Map<String, Object>> recentMessages) {
        String conversationText = renderWindow(recentMessages);
        if (conversationText.isBlank()) {
            return;
        }

        String prompt = "請從以下對話萃取值得長期記住的使用者事實：\n\n" + conversationText;
        String rawResponse = aiClient.chat(
            prompt, EXTRACTION_SYSTEM_PROMPT, 800, 0.2, userId);

        List<ExtractedFact> facts = parseFacts(rawResponse);
        if (facts.isEmpty()) {
            return;
        }

        List<UserMemory> existing = userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int stored = 0;
        for (ExtractedFact fact : facts) {
            if (stored >= MAX_FACTS_PER_EXTRACTION) {
                break;
            }
            if (applyWithDedup(userId, fact, existing)) {
                stored++;
            }
        }
        if (stored > 0) {
            log.info("Memory extraction stored {} new memories for user {}", stored, userId);
        }
    }

    /**
     * 解析 LLM 輸出為事實清單。容忍 markdown 圍欄與前後雜訊；
     * 解析失敗回傳空清單（不丟例外）。
     */
    List<ExtractedFact> parseFacts(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }

        String json = stripToJsonArray(rawResponse);
        if (json == null) {
            log.debug("Memory extraction: no JSON array found in LLM output");
            return List.of();
        }

        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            List<ExtractedFact> facts = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (facts.size() >= MAX_FACTS_PER_EXTRACTION) {
                    break;
                }
                Object content = item.get("content");
                Object category = item.get("category");
                if (content instanceof String c && !c.isBlank()
                        && c.trim().length() <= UserMemoryService.MAX_CONTENT_LENGTH) {
                    facts.add(new ExtractedFact(
                        c.trim(),
                        category instanceof String cat ? cat : "general"));
                }
            }
            return List.copyOf(facts);
        } catch (Exception e) {
            log.debug("Memory extraction: failed to parse LLM output as JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 去重後套用一筆事實。
     *
     * 策略（確定性，無 embedding）：
     * - 新內容被既有記憶「正規化包含」→ 跳過（沒有新東西）
     * - 既有記憶被新內容包含 → 更新該筆記憶為較完整的新內容
     * - token 重疊率 ≥ {@link #OVERLAP_THRESHOLD} → 跳過
     * - 其餘 → 新增（source = assistant）
     *
     * @return true 表示有寫入（新增或更新）
     */
    private boolean applyWithDedup(UUID userId, ExtractedFact fact, List<UserMemory> existing) {
        String normalizedNew = normalize(fact.content());

        for (UserMemory memory : existing) {
            String normalizedOld = normalize(memory.getContent());

            if (normalizedOld.contains(normalizedNew)) {
                log.debug("Memory extraction: skip duplicate (contained in existing {})", memory.getId());
                return false;
            }
            if (normalizedNew.contains(normalizedOld)) {
                userMemoryService.update(userId, memory.getId(), fact.content(), fact.category());
                log.debug("Memory extraction: updated existing memory {} with fuller content", memory.getId());
                return true;
            }
            if (tokenOverlapRatio(normalizedNew, normalizedOld) >= OVERLAP_THRESHOLD) {
                log.debug("Memory extraction: skip near-duplicate of memory {}", memory.getId());
                return false;
            }
        }

        userMemoryService.add(userId, fact.content(), fact.category(), "assistant");
        return true;
    }

    /**
     * 從 LLM 輸出擷取最外層 JSON 陣列（容忍 ```json 圍欄與前後說明文字）。
     */
    private String stripToJsonArray(String raw) {
        String text = raw.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /** 正規化：小寫、移除空白與常見標點，供包含比對使用 */
    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}，。、；：！？「」『』（）]+", "");
    }

    /**
     * 字元 bigram 重疊率（Dice 係數的簡化版：交集 / 較小集合），
     * 對中英文皆有效且確定性。
     */
    private double tokenOverlapRatio(String a, String b) {
        Set<String> bigramsA = bigrams(a);
        Set<String> bigramsB = bigrams(b);
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(bigramsA);
        intersection.retainAll(bigramsB);
        return (double) intersection.size() / Math.min(bigramsA.size(), bigramsB.size());
    }

    private Set<String> bigrams(String text) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < text.length(); i++) {
            result.add(text.substring(i, i + 2));
        }
        return result;
    }

    /**
     * 將近期訊息渲染成純文字對話（略過 system 訊息，只取最後 N 則）。
     */
    private String renderWindow(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> nonSystem = messages.stream()
            .filter(m -> !"system".equals(m.get("role")))
            .toList();
        int from = Math.max(0, nonSystem.size() - EXTRACTION_WINDOW_MESSAGES);

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : nonSystem.subList(from, nonSystem.size())) {
            Object role = msg.get("role");
            Object content = msg.get("content");
            if (role instanceof String r && content instanceof String c && !c.isBlank()) {
                sb.append("user".equals(r) ? "User" : "Assistant").append(": ").append(c).append("\n\n");
            }
        }
        return sb.toString();
    }

    /** 萃取出的單筆事實 */
    record ExtractedFact(String content, String category) {}
}
