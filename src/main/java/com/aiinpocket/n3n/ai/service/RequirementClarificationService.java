package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.RequirementClarificationRequest;
import com.aiinpocket.n3n.ai.dto.RequirementClarificationResponse;
import com.aiinpocket.n3n.ai.dto.RequirementClarificationResponse.RequirementSummary;
import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 需求釐清服務
 * 透過多輪對話幫助使用者釐清流程需求，
 * 直到需求足夠完整後才進入流程生成階段。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RequirementClarificationService {

    private final SimpleAIProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是 N3N 流程設計助手。你的任務是透過對話幫助使用者釐清自動化流程的需求。

        你需要了解以下關鍵資訊才能設計出完整的流程：
        1. **觸發方式**：什麼事件或條件觸發這個流程？（定時排程、Webhook、手動觸發、檔案變更等）
        2. **數據來源**：數據從哪裡來？（API、資料庫、檔案、使用者輸入等）
        3. **處理步驟**：中間需要做什麼處理？（資料轉換、過濾、條件判斷、AI 處理等）
        4. **輸出目的**：處理完的結果要送到哪裡？（Email、資料庫、API、通知等）
        5. **錯誤處理**：出錯時要怎麼辦？（重試、通知、記錄等）

        **對話策略**：
        - 每次只問 1-2 個問題，不要一次問太多
        - 根據使用者已提供的資訊，智慧地跳過不需要的問題
        - 提供具體的選項建議讓使用者選擇
        - 語氣親切友善，像同事間的討論
        - 如果使用者的描述已經很完整，直接確認即可

        **回應格式（嚴格 JSON）**：
        ```json
        {
          "requirementComplete": false,
          "message": "你的回應訊息（用繁體中文）",
          "suggestedReplies": ["建議回答1", "建議回答2", "建議回答3"],
          "summary": null
        }
        ```

        當你認為需求已經足夠完整時，設置 requirementComplete 為 true 並提供 summary：
        ```json
        {
          "requirementComplete": true,
          "message": "太好了！我已經理解你的需求了。請確認以下摘要：",
          "suggestedReplies": null,
          "summary": {
            "triggerType": "定時排程",
            "triggerDescription": "每天早上 9 點執行",
            "dataSource": "從 REST API 取得銷售數據",
            "processSteps": ["取得 API 數據", "過濾本月數據", "計算統計"],
            "outputTarget": "發送 Email 報表給管理團隊",
            "errorHandling": "失敗時重試 3 次，最終失敗發送 Slack 通知",
            "fullDescription": "完整的流程描述..."
          }
        }
        ```

        重要：只回應 JSON，不要有其他文字。
        """;

    /**
     * 處理需求釐清對話
     */
    public RequirementClarificationResponse clarify(
            RequirementClarificationRequest request,
            UUID userId) {

        SimpleAIProvider provider = providerRegistry.getProviderForFeature("assistant", userId);

        if (!provider.isAvailable()) {
            return RequirementClarificationResponse.error("AI service is not available");
        }

        try {
            // 建構包含歷史的提示詞
            String prompt = buildPrompt(request);

            String response = provider.chat(prompt, SYSTEM_PROMPT, 2048, 0.7);

            return parseResponse(response, request.getConversationId());

        } catch (Exception e) {
            log.error("Requirement clarification failed", e);
            return RequirementClarificationResponse.error("Requirement clarification failed");
        }
    }

    private String buildPrompt(RequirementClarificationRequest request) {
        StringBuilder sb = new StringBuilder();

        // 加入對話歷史
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            sb.append("=== 對話歷史 ===\n");
            for (RequirementClarificationRequest.ChatMessage msg : request.getHistory()) {
                String roleLabel = "user".equals(msg.getRole()) ? "使用者" : "助手";
                sb.append(roleLabel).append("：").append(msg.getContent()).append("\n\n");
            }
            sb.append("=== 最新訊息 ===\n");
        }

        sb.append("使用者：").append(request.getMessage());

        return sb.toString();
    }

    private RequirementClarificationResponse parseResponse(String response, UUID conversationId) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            boolean complete = root.has("requirementComplete") && root.get("requirementComplete").asBoolean();
            String message = root.has("message") ? root.get("message").asText() : "";

            if (complete && root.has("summary") && !root.get("summary").isNull()) {
                JsonNode summaryNode = root.get("summary");
                RequirementSummary summary = RequirementSummary.builder()
                    .triggerType(getTextOrNull(summaryNode, "triggerType"))
                    .triggerDescription(getTextOrNull(summaryNode, "triggerDescription"))
                    .dataSource(getTextOrNull(summaryNode, "dataSource"))
                    .processSteps(getStringList(summaryNode, "processSteps"))
                    .outputTarget(getTextOrNull(summaryNode, "outputTarget"))
                    .errorHandling(getTextOrNull(summaryNode, "errorHandling"))
                    .fullDescription(getTextOrNull(summaryNode, "fullDescription"))
                    .build();

                return RequirementClarificationResponse.complete(conversationId, message, summary);
            }

            List<String> suggestions = getStringList(root, "suggestedReplies");

            return RequirementClarificationResponse.question(conversationId, message, suggestions);

        } catch (Exception e) {
            log.warn("Failed to parse clarification response: {}", e.getClass().getSimpleName());
            return RequirementClarificationResponse.question(
                conversationId,
                "I'm thinking about your requirements. Could you describe the flow you want in more detail?",
                List.of()
            );
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private List<String> getStringList(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull() || !node.get(field).isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node.get(field)) {
            result.add(item.asText());
        }
        return result;
    }

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
}
