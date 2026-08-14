package com.aiinpocket.n3n.ai.agent.supervisor;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意圖分析器
 * 分析使用者輸入並識別其意圖
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentAnalyzer {

    private final AssistantAiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are an intent analysis expert. Analyze the user's input and identify their intent.
        Respond to the user in the SAME LANGUAGE they used (Chinese, English, or Japanese).

        Possible intent types:
        - SEARCH_NODE: Search for nodes/components (e.g., "What nodes can send email?")
        - GET_DOCUMENTATION: Get documentation (e.g., "How do I use the HTTP node?")
        - FIND_EXAMPLES: Find examples (e.g., "Is there a scheduling example?")
        - SEARCH_SKILL: Search available skills
        - CREATE_FLOW: Create a new workflow (e.g., "Create a daily report flow")
        - ADD_NODE: Add a node to existing flow (e.g., "Add an error handler node")
        - REMOVE_NODE: Remove a node (e.g., "Delete the HTTP node")
        - CONNECT_NODES: Connect nodes (e.g., "Connect trigger to HTTP node")
        - CONFIGURE_NODE: Configure node parameters
        - MODIFY_FLOW: Modify existing flow
        - OPTIMIZE_FLOW: Optimize flow performance
        - EXPLAIN: Explain or describe (e.g., "What does this flow do?")
        - CLARIFY: Needs user clarification
        - CONFIRM: Confirm operation
        - COMPOUND: Multiple intents combined
        - CHITCHAT: Casual chat
        - UNKNOWN: Cannot identify

        Respond in JSON format only, no other text:
        {
          "type": "INTENT_TYPE",
          "confidence": 0.95,
          "understanding": "Understanding of user's request (in user's language)",
          "entities": {
            "targetNode": "extracted node name (if any)",
            "action": "action to perform",
            "nodeType": "node type (if any)",
            "label": "node label (if any)"
          }
        }
        """;

    /**
     * 分析使用者意圖
     */
    public Intent analyze(AgentContext context) {
        if (!aiClient.isAvailable(context.getUserId())) {
            log.warn("AI provider not available, using rule-based analysis");
            return ruleBasedAnalysis(context.getUserInput());
        }

        try {
            String prompt = buildPrompt(context);
            String response = aiClient.chat(prompt, SYSTEM_PROMPT, 1024, 0.3, context.getUserId(),
                com.aiinpocket.n3n.ai.provider.AiTaskType.LIGHT);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Failed to analyze intent with AI, falling back to rules", e);
            return ruleBasedAnalysis(context.getUserInput());
        }
    }

    /**
     * 建構分析提示詞
     */
    private String buildPrompt(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("User input: ").append(context.getUserInput()).append("\n\n");

        if (context.getConversationHistory() != null &&
            !context.getConversationHistory().isEmpty()) {
            sb.append("Recent conversation:\n");
            int count = 0;
            for (Message msg : context.getConversationHistory()) {
                if (count++ >= 10) break;
                sb.append("- ").append(msg.getRole()).append(": ")
                    .append(truncate(msg.getContent(), 200)).append("\n");
            }
        }

        if (context.getFlowDraft() != null && context.getFlowDraft().hasContent()) {
            sb.append("\nCurrent flow draft has ")
                .append(context.getFlowDraft().getNodeCount())
                .append(" nodes\n");
        } else if (context.getCurrentNodes() != null && !context.getCurrentNodes().isEmpty()) {
            sb.append("\nCurrent flow has ")
                .append(context.getCurrentNodes().size())
                .append(" nodes\n");
        }

        sb.append("\nAnalyze the user's intent and respond with JSON only.");
        return sb.toString();
    }

    /**
     * 解析 AI 回應
     */
    private Intent parseResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            String typeStr = root.has("type") ? root.get("type").asText() : "UNKNOWN";
            Intent.IntentType type;
            try {
                type = Intent.IntentType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                type = Intent.IntentType.UNKNOWN;
            }

            return Intent.builder()
                .type(type)
                .confidence(root.has("confidence") ? root.get("confidence").asDouble() : 0.5)
                .understanding(root.has("understanding") ? root.get("understanding").asText() : "")
                .entities(parseEntities(root.get("entities")))
                .build();

        } catch (Exception e) {
            log.warn("Failed to parse intent response: {}", e.getClass().getSimpleName());
            return Intent.builder()
                .type(Intent.IntentType.UNKNOWN)
                .confidence(0.5)
                .build();
        }
    }

    /**
     * 基於規則的意圖分析（Fallback）
     * 僅在 AI provider 不可用或呼叫失敗時使用；結果以 entities.fallback=true 標記，非 AI 產生。
     */
    private Intent ruleBasedAnalysis(String input) {
        if (input == null || input.isBlank()) {
            return Intent.builder()
                .type(Intent.IntentType.UNKNOWN)
                .confidence(0.0)
                .build();
        }

        String lower = input.toLowerCase();
        Map<String, Object> entities = new HashMap<>();
        entities.put("fallback", true);

        // 建立流程 (zh + en + ja)
        if (containsAny(lower, "建立", "創建", "新增", "做一個", "幫我做", "設計",
                "create", "build", "make", "design", "generate",
                "作成", "構築", "フロー作成") &&
            containsAny(lower, "流程", "工作流", "workflow", "自動化",
                "flow", "automation", "pipeline",
                "フロー", "ワークフロー")) {
            return Intent.builder()
                .type(Intent.IntentType.CREATE_FLOW)
                .confidence(0.8)
                .entities(entities)
                .build();
        }

        // 新增節點 (zh + en + ja)
        if (containsAny(lower, "加", "新增", "添加", "放", "加入",
                "add", "insert", "include",
                "追加", "ノード追加") &&
            containsAny(lower, "節點", "node", "元件", "component",
                "ノード", "コンポーネント")) {
            return Intent.builder()
                .type(Intent.IntentType.ADD_NODE)
                .confidence(0.8)
                .entities(entities)
                .build();
        }

        // 移除節點 (zh + en + ja)
        if (containsAny(lower, "刪除", "移除", "刪掉", "拿掉",
                "remove", "delete", "drop",
                "削除", "取り除く")) {
            return Intent.builder()
                .type(Intent.IntentType.REMOVE_NODE)
                .confidence(0.7)
                .entities(entities)
                .build();
        }

        // 搜尋節點 (zh + en + ja)
        if (containsAny(lower, "有什麼", "哪些", "搜尋", "找", "查詢",
                "search", "find", "what", "which", "list",
                "検索", "探す") &&
            containsAny(lower, "節點", "node", "元件", "可以",
                "component", "available",
                "ノード", "コンポーネント")) {
            return Intent.builder()
                .type(Intent.IntentType.SEARCH_NODE)
                .confidence(0.7)
                .entities(entities)
                .build();
        }

        // 解釋 (zh + en + ja)
        if (containsAny(lower, "什麼", "怎麼", "如何", "解釋", "說明", "為什麼",
                "explain", "what is", "how does", "why",
                "説明", "教えて", "なぜ")) {
            return Intent.builder()
                .type(Intent.IntentType.EXPLAIN)
                .confidence(0.6)
                .entities(entities)
                .build();
        }

        // 優化 (zh + en + ja)
        if (containsAny(lower, "優化", "改善", "提升", "效能", "效率",
                "optimize", "improve", "performance", "efficiency",
                "最適化", "改善", "パフォーマンス")) {
            return Intent.builder()
                .type(Intent.IntentType.OPTIMIZE_FLOW)
                .confidence(0.7)
                .entities(entities)
                .build();
        }

        // 預設
        return Intent.builder()
            .type(Intent.IntentType.CHITCHAT)
            .confidence(0.4)
            .entities(entities)
            .build();
    }

    /**
     * 解析實體
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEntities(JsonNode node) {
        if (node == null || node.isNull()) return new HashMap<>();
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 提取 JSON
     */
    private String extractJson(String content) {
        // 先嘗試提取 ```json...``` 區塊
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 否則找 { 和 } 之間的內容
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
