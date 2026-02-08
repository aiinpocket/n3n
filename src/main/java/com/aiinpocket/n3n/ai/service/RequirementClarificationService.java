package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.conversation.ConversationManager;
import com.aiinpocket.n3n.ai.dto.RequirementClarificationRequest;
import com.aiinpocket.n3n.ai.dto.RequirementClarificationResponse;
import com.aiinpocket.n3n.ai.dto.RequirementClarificationResponse.RequirementSummary;
import com.aiinpocket.n3n.ai.entity.Conversation;
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
    private final ConversationManager conversationManager;
    private final ObjectMapper objectMapper;

    private static final int MAX_INPUT_LENGTH = 5000;
    private static final List<String> SUSPICIOUS_PATTERNS = List.of(
        "ignore above", "disregard", "forget everything",
        "you are now", "jailbreak", "dan mode", "system prompt",
        "<|im_start|>", "<|im_end|>"
    );

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        You are an N3N workflow design assistant. Your task is to help users clarify their automation workflow requirements through conversation.

        You need to understand the following key information to design a complete workflow:
        1. **Trigger**: What event or condition triggers this workflow? (Scheduled cron, Webhook, Manual trigger, File change, etc.)
        2. **Data Source**: Where does the data come from? (REST API, Database, File, User input, etc.)
        3. **Processing Steps**: What processing is needed? (Data transformation, Filtering, Conditions, AI processing, etc.)
        4. **Output Target**: Where should the results go? (Email, Database, API, Notification, etc.)
        5. **Error Handling**: What happens on failure? (Retry, Notify, Log, etc.)

        **Conversation Strategy**:
        - Ask only 1-2 questions at a time, never overwhelm the user
        - Intelligently skip questions the user has already answered
        - Provide concrete suggested options for the user to choose from
        - Be friendly and conversational, like a helpful colleague
        - If the user's description is already comprehensive, confirm directly
        - When the user says something vague like "process the data", ask specifically what processing they need
        - If the user mentions a service (e.g. "Slack", "Gmail"), infer the trigger/output type

        **Response language**: %s

        **Response format (strict JSON only)**:
        ```json
        {
          "requirementComplete": false,
          "message": "Your response message",
          "suggestedReplies": ["Suggested reply 1", "Suggested reply 2", "Suggested reply 3"],
          "summary": null
        }
        ```

        When requirements are sufficiently complete, set requirementComplete to true with summary:
        ```json
        {
          "requirementComplete": true,
          "message": "Great! I've understood your requirements. Please confirm the summary below:",
          "suggestedReplies": null,
          "summary": {
            "triggerType": "schedule",
            "triggerDescription": "Every day at 9am",
            "dataSource": "Fetch sales data from REST API",
            "processSteps": ["Fetch API data", "Filter current month", "Calculate statistics"],
            "outputTarget": "Send email report to management team",
            "errorHandling": "Retry 3 times on failure, then send Slack notification",
            "fullDescription": "Complete workflow description..."
          }
        }
        ```

        IMPORTANT: Respond ONLY with JSON, no other text.
        """;

    private String getSystemPrompt(String language) {
        String langInstruction;
        if (language != null && language.startsWith("zh")) {
            langInstruction = "Respond in Traditional Chinese (繁體中文)";
        } else if (language != null && language.startsWith("ja")) {
            langInstruction = "Respond in Japanese (日本語)";
        } else {
            langInstruction = "Respond in English";
        }
        return String.format(SYSTEM_PROMPT_TEMPLATE, langInstruction);
    }

    /**
     * 處理需求釐清對話
     */
    public RequirementClarificationResponse clarify(
            RequirementClarificationRequest request,
            UUID userId) {

        // Sanitize user input
        String sanitizedMessage = sanitizeInput(request.getMessage());
        if (sanitizedMessage == null) {
            return RequirementClarificationResponse.error("Invalid input");
        }

        SimpleAIProvider provider = providerRegistry.getProviderForFeature("assistant", userId);

        if (!provider.isAvailable()) {
            return RequirementClarificationResponse.error("AI service is not available");
        }

        try {
            // Ensure conversation exists for persistence
            UUID conversationId = request.getConversationId();
            if (conversationId == null) {
                Conversation conv = conversationManager.createConversation(
                    userId, null, "Requirement Clarification", "CLARIFICATION");
                conversationId = conv.getId();
            }

            // Persist user message
            conversationManager.addMessage(conversationId, userId, "user", sanitizedMessage, null);

            // Build prompt and call AI
            String prompt = buildPrompt(request);
            String systemPrompt = getSystemPrompt(request.getLanguage());
            String response = provider.chat(prompt, systemPrompt, 2048, 0.7);

            RequirementClarificationResponse clarifyResponse = parseResponse(response, conversationId);

            // Persist AI response
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("requirementComplete", clarifyResponse.isRequirementComplete());
            conversationManager.addMessage(
                conversationId, userId, "assistant",
                clarifyResponse.getMessage(), metadata);

            return clarifyResponse;

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

    /**
     * Sanitize user input: length check, suspicious pattern detection, control char removal.
     */
    private String sanitizeInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Clarification input exceeds max length ({} > {})", input.length(), MAX_INPUT_LENGTH);
            return null;
        }
        String lowerInput = input.toLowerCase();
        for (String pattern : SUSPICIOUS_PATTERNS) {
            if (lowerInput.contains(pattern)) {
                log.warn("Suspicious pattern in clarification input: '{}'", pattern);
                return null;
            }
        }
        String sanitized = input
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
            .replaceAll("\\p{Cc}", "");
        sanitized = sanitized.replaceAll("\\s{10,}", " ".repeat(10));
        if (sanitized.isBlank() || sanitized.length() < 2) {
            return null;
        }
        return sanitized.trim();
    }
}
