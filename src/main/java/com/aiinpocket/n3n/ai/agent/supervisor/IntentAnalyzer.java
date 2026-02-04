package com.aiinpocket.n3n.ai.agent.supervisor;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
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

    private final SimpleAIProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是一個意圖分析專家。分析使用者輸入並識別其意圖。

        可能的意圖類型：
        - SEARCH_NODE: 搜尋節點或元件（例如：有什麼節點可以發送郵件？）
        - GET_DOCUMENTATION: 取得文件或說明（例如：HTTP 節點怎麼用？）
        - FIND_EXAMPLES: 尋找範例（例如：有沒有排程的範例？）
        - SEARCH_SKILL: 搜尋可用技能
        - CREATE_FLOW: 建立新的工作流程（例如：建立一個每天發送報表的流程）
        - ADD_NODE: 新增節點到現有流程（例如：加一個錯誤處理節點）
        - REMOVE_NODE: 移除節點（例如：把那個 HTTP 節點刪掉）
        - CONNECT_NODES: 連接節點（例如：把觸發器連到 HTTP 節點）
        - CONFIGURE_NODE: 配置節點參數（例如：把 HTTP 節點的 URL 改成...）
        - MODIFY_FLOW: 修改現有流程
        - OPTIMIZE_FLOW: 優化流程效能
        - EXPLAIN: 解釋或說明（例如：這個流程是做什麼的？）
        - CLARIFY: 需要使用者澄清
        - CONFIRM: 確認操作
        - COMPOUND: 複合意圖（多個意圖組合）
        - CHITCHAT: 閒聊
        - UNKNOWN: 無法識別

        請以 JSON 格式回應，只回應 JSON，不要有其他文字：
        {
          "type": "意圖類型",
          "confidence": 0.95,
          "understanding": "對使用者需求的理解（繁體中文）",
          "entities": {
            "targetNode": "提取的節點名稱（如有）",
            "action": "要執行的動作",
            "nodeType": "節點類型（如有）",
            "label": "節點標籤（如有）"
          }
        }
        """;

    /**
     * 分析使用者意圖
     */
    public Intent analyze(AgentContext context) {
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(
            "supervisor", context.getUserId());

        if (!provider.isAvailable()) {
            log.warn("AI provider not available, using rule-based analysis");
            return ruleBasedAnalysis(context.getUserInput());
        }

        try {
            String prompt = buildPrompt(context);
            String response = provider.chat(prompt, SYSTEM_PROMPT, 1024, 0.3);
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
        sb.append("使用者輸入: ").append(context.getUserInput()).append("\n\n");

        // 加入對話歷史上下文
        if (context.getConversationHistory() != null &&
            !context.getConversationHistory().isEmpty()) {
            sb.append("最近對話:\n");
            int count = 0;
            for (Message msg : context.getConversationHistory()) {
                if (count++ >= 5) break;
                sb.append("- ").append(msg.getRole()).append(": ")
                    .append(truncate(msg.getContent(), 100)).append("\n");
            }
        }

        // 加入當前流程上下文
        if (context.getFlowDraft() != null && context.getFlowDraft().hasContent()) {
            sb.append("\n當前流程草稿有 ")
                .append(context.getFlowDraft().getNodeCount())
                .append(" 個節點\n");
        } else if (context.getCurrentNodes() != null && !context.getCurrentNodes().isEmpty()) {
            sb.append("\n當前流程有 ")
                .append(context.getCurrentNodes().size())
                .append(" 個節點\n");
        }

        sb.append("\n請分析此使用者的意圖，只回應 JSON。");
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
            log.warn("Failed to parse intent response: {}", e.getMessage());
            return Intent.builder()
                .type(Intent.IntentType.UNKNOWN)
                .confidence(0.5)
                .build();
        }
    }

    /**
     * 基於規則的意圖分析（Fallback）
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

        // 建立流程
        if (containsAny(lower, "建立", "創建", "新增", "做一個", "幫我做", "設計") &&
            containsAny(lower, "流程", "工作流", "workflow", "自動化")) {
            return Intent.builder()
                .type(Intent.IntentType.CREATE_FLOW)
                .confidence(0.8)
                .understanding("使用者想要建立新的工作流程")
                .entities(entities)
                .build();
        }

        // 新增節點
        if (containsAny(lower, "加", "新增", "添加", "放", "加入") &&
            containsAny(lower, "節點", "node", "元件")) {
            return Intent.builder()
                .type(Intent.IntentType.ADD_NODE)
                .confidence(0.8)
                .understanding("使用者想要新增節點")
                .entities(entities)
                .build();
        }

        // 移除節點
        if (containsAny(lower, "刪除", "移除", "刪掉", "拿掉", "remove")) {
            return Intent.builder()
                .type(Intent.IntentType.REMOVE_NODE)
                .confidence(0.7)
                .understanding("使用者想要移除節點")
                .entities(entities)
                .build();
        }

        // 搜尋節點
        if (containsAny(lower, "有什麼", "哪些", "搜尋", "找", "查詢") &&
            containsAny(lower, "節點", "node", "元件", "可以")) {
            return Intent.builder()
                .type(Intent.IntentType.SEARCH_NODE)
                .confidence(0.7)
                .understanding("使用者想要搜尋節點")
                .entities(entities)
                .build();
        }

        // 解釋
        if (containsAny(lower, "什麼", "怎麼", "如何", "解釋", "說明", "為什麼")) {
            return Intent.builder()
                .type(Intent.IntentType.EXPLAIN)
                .confidence(0.6)
                .understanding("使用者需要解釋")
                .entities(entities)
                .build();
        }

        // 優化
        if (containsAny(lower, "優化", "改善", "提升", "效能", "效率")) {
            return Intent.builder()
                .type(Intent.IntentType.OPTIMIZE_FLOW)
                .confidence(0.7)
                .understanding("使用者想要優化流程")
                .entities(entities)
                .build();
        }

        // 預設
        return Intent.builder()
            .type(Intent.IntentType.CHITCHAT)
            .confidence(0.4)
            .understanding("無法明確識別意圖")
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
