package com.aiinpocket.n3n.ai.agent.subagent;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.agent.tools.SearchNodeTool;
import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.aiinpocket.n3n.execution.handler.NodeHandlerInfo;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.skill.service.SkillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovery Agent - 探索與搜尋代理
 *
 * 職責：
 * 1. 搜尋可用的節點類型
 * 2. 取得節點文件和範例
 * 3. 搜尋可用的技能
 * 4. 根據需求推薦適合的節點
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryAgent implements Agent {

    private final AgentRegistry agentRegistry;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final SimpleAIProviderRegistry providerRegistry;
    private final SkillService skillService;
    private final SearchNodeTool searchNodeTool;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        agentRegistry.register(this);
    }

    @Override
    public String getId() {
        return "discovery";
    }

    @Override
    public String getName() {
        return "Discovery Agent";
    }

    @Override
    public String getDescription() {
        return "探索與搜尋代理，負責搜尋節點、取得文件、尋找範例";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("search_node", "get_documentation", "find_examples", "search_skill");
    }

    @Override
    public List<AgentTool> getTools() {
        return List.of(searchNodeTool);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        log.info("Discovery Agent executing for intent: {}",
            context.getIntent() != null ? context.getIntent().getType() : "null");

        try {
            Intent intent = context.getIntent();
            if (intent == null) {
                return searchBasedOnUserInput(context);
            }

            return switch (intent.getType()) {
                case SEARCH_NODE, SEARCH_SKILL -> searchNodes(context);
                case GET_DOCUMENTATION -> getDocumentation(context);
                case FIND_EXAMPLES -> findExamples(context);
                case CREATE_FLOW -> recommendNodesForFlow(context);
                default -> searchBasedOnUserInput(context);
            };

        } catch (Exception e) {
            log.error("Discovery Agent execution failed", e);
            return AgentResult.error("探索失敗: " + e.getMessage());
        }
    }

    @Override
    public Flux<AgentStreamChunk> executeStream(AgentContext context) {
        return Flux.create(sink -> {
            try {
                sink.next(AgentStreamChunk.thinking("搜尋相關元件..."));

                AgentResult result = execute(context);

                if (result.isSuccess()) {
                    sink.next(AgentStreamChunk.text(result.getContent()));
                    if (result.getData() != null) {
                        sink.next(AgentStreamChunk.structured(result.getData()));
                    }
                } else {
                    sink.next(AgentStreamChunk.error(result.getError()));
                }

                sink.next(AgentStreamChunk.done());
                sink.complete();
            } catch (Exception e) {
                log.error("Discovery stream failed", e);
                sink.next(AgentStreamChunk.error(e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 搜尋節點
     */
    private AgentResult searchNodes(AgentContext context) {
        String query = extractSearchQuery(context);
        log.debug("Searching nodes with query: {}", query);

        ToolResult toolResult = searchNodeTool.execute(
            Map.of("query", query, "limit", 10),
            context
        );

        if (!toolResult.isSuccess()) {
            return AgentResult.error(toolResult.getError());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>)
            toolResult.getData().get("nodes");

        if (nodes.isEmpty()) {
            return AgentResult.builder()
                .success(true)
                .content("沒有找到符合「" + query + "」的節點。")
                .data(Map.of("searchQuery", query, "results", List.of()))
                .build();
        }

        // 格式化回應
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(nodes.size()).append(" 個相關節點：\n\n");

        for (Map<String, Object> node : nodes) {
            sb.append("**").append(node.get("displayName")).append("** (`")
                .append(node.get("type")).append("`)\n");
            if (node.get("description") != null) {
                sb.append("  ").append(node.get("description")).append("\n");
            }
            sb.append("  類別: ").append(node.get("category")).append("\n\n");
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .data(Map.of(
                "searchQuery", query,
                "results", nodes
            ))
            .build();
    }

    /**
     * 取得節點文件
     */
    private AgentResult getDocumentation(AgentContext context) {
        String nodeType = extractTargetNode(context);
        log.debug("Getting documentation for: {}", nodeType);

        if (nodeType == null) {
            return AgentResult.error("請指定要查詢的節點類型");
        }

        // 嘗試找到節點
        var handlerOpt = nodeHandlerRegistry.findHandler(nodeType);
        if (handlerOpt.isEmpty()) {
            // 模糊搜尋
            ToolResult searchResult = searchNodeTool.execute(
                Map.of("query", nodeType, "limit", 5),
                context
            );
            return AgentResult.builder()
                .success(true)
                .content("找不到 `" + nodeType + "` 節點。相近的節點請參考搜尋結果。")
                .data(searchResult.getData())
                .build();
        }

        var handler = handlerOpt.get();
        StringBuilder doc = new StringBuilder();
        doc.append("## ").append(handler.getDisplayName()).append("\n\n");
        doc.append("**類型**: `").append(handler.getType()).append("`\n\n");
        doc.append("**類別**: ").append(handler.getCategory()).append("\n\n");

        if (handler.getDescription() != null) {
            doc.append("**描述**: ").append(handler.getDescription()).append("\n\n");
        }

        // 配置參數說明
        if (handler.getConfigSchema() != null && !handler.getConfigSchema().isEmpty()) {
            doc.append("### 配置參數\n\n");
            doc.append("```json\n");
            try {
                doc.append(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(handler.getConfigSchema()));
            } catch (Exception e) {
                doc.append(handler.getConfigSchema().toString());
            }
            doc.append("\n```\n\n");
        }

        // 介面定義
        if (handler.getInterfaceDefinition() != null && !handler.getInterfaceDefinition().isEmpty()) {
            doc.append("### 輸入/輸出介面\n\n");
            var iface = handler.getInterfaceDefinition();
            if (iface.containsKey("inputs")) {
                doc.append("**輸入**: ").append(iface.get("inputs")).append("\n");
            }
            if (iface.containsKey("outputs")) {
                doc.append("**輸出**: ").append(iface.get("outputs")).append("\n");
            }
        }

        return AgentResult.builder()
            .success(true)
            .content(doc.toString())
            .data(Map.of(
                "nodeType", handler.getType(),
                "displayName", handler.getDisplayName(),
                "configSchema", handler.getConfigSchema() != null ? handler.getConfigSchema() : Map.of()
            ))
            .build();
    }

    /**
     * 尋找範例
     */
    private AgentResult findExamples(AgentContext context) {
        String query = extractSearchQuery(context);
        log.debug("Finding examples for: {}", query);

        // TODO: 整合範例資料庫或 Marketplace
        // 目前先回傳通用範例結構

        StringBuilder sb = new StringBuilder();
        sb.append("## 相關範例\n\n");
        sb.append("以下是一些常見的流程範例模式：\n\n");

        // 根據查詢推薦範例模式
        if (query.contains("郵件") || query.contains("email") || query.contains("報表")) {
            sb.append("### 定時發送報表\n");
            sb.append("```\n排程觸發 → 資料庫查詢 → 資料處理 → 發送郵件\n```\n\n");
        }

        if (query.contains("webhook") || query.contains("API") || query.contains("通知")) {
            sb.append("### Webhook 觸發通知\n");
            sb.append("```\nWebhook 觸發 → 資料驗證 → 條件判斷 → 發送 Slack/Telegram\n```\n\n");
        }

        if (query.contains("資料") || query.contains("同步") || query.contains("ETL")) {
            sb.append("### 資料同步流程\n");
            sb.append("```\n排程觸發 → 來源資料庫查詢 → 資料轉換 → 目標資料庫寫入\n```\n\n");
        }

        sb.append("如需特定範例，請提供更詳細的需求描述。");

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .requiresFollowUp(true)
            .nextAction("builder")
            .build();
    }

    /**
     * 根據使用者需求推薦流程節點
     */
    private AgentResult recommendNodesForFlow(AgentContext context) {
        String userInput = context.getUserInput();
        log.debug("Recommending nodes for: {}", userInput);

        // 使用 AI 分析需求並推薦節點
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(
            "discovery", context.getUserId());

        if (!provider.isAvailable()) {
            return ruleBasedRecommendation(context);
        }

        try {
            // 取得所有可用節點的簡要資訊
            List<NodeHandlerInfo> allNodes = nodeHandlerRegistry.listHandlerInfo();
            String nodeList = allNodes.stream()
                .map(n -> n.getType() + ": " + n.getDisplayName() + " (" + n.getDescription() + ")")
                .collect(Collectors.joining("\n"));

            String prompt = String.format("""
                使用者需求: %s

                可用的節點類型:
                %s

                請分析使用者需求，推薦建構此流程需要的節點。
                以 JSON 格式回應:
                {
                  "recommendedNodes": [
                    {"type": "節點類型", "label": "建議標籤", "reason": "推薦理由"},
                    ...
                  ],
                  "flowStructure": "建議的流程結構說明",
                  "missingCapabilities": ["缺少的功能（如果有）"]
                }
                """, userInput, truncate(nodeList, 3000));

            String response = provider.chat(prompt, RECOMMENDATION_SYSTEM_PROMPT, 2000, 0.3);
            return parseRecommendationResponse(response, context);

        } catch (Exception e) {
            log.warn("AI recommendation failed, using rule-based", e);
            return ruleBasedRecommendation(context);
        }
    }

    /**
     * 規則式推薦（Fallback）
     */
    private AgentResult ruleBasedRecommendation(AgentContext context) {
        String input = context.getUserInput().toLowerCase();
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // 觸發器推薦
        if (input.contains("每天") || input.contains("定時") || input.contains("排程")) {
            recommendations.add(Map.of(
                "type", "scheduleTrigger",
                "label", "排程觸發器",
                "reason", "根據時間定時執行"
            ));
        } else if (input.contains("webhook") || input.contains("api")) {
            recommendations.add(Map.of(
                "type", "webhookTrigger",
                "label", "Webhook 觸發器",
                "reason", "接收外部 API 請求"
            ));
        } else {
            recommendations.add(Map.of(
                "type", "trigger",
                "label", "手動觸發",
                "reason", "手動啟動流程"
            ));
        }

        // 動作推薦
        if (input.contains("郵件") || input.contains("email") || input.contains("mail")) {
            recommendations.add(Map.of(
                "type", "sendEmail",
                "label", "發送郵件",
                "reason", "發送電子郵件通知"
            ));
        }

        if (input.contains("資料庫") || input.contains("查詢") || input.contains("sql")) {
            recommendations.add(Map.of(
                "type", "database",
                "label", "資料庫查詢",
                "reason", "執行 SQL 查詢"
            ));
        }

        if (input.contains("http") || input.contains("api") || input.contains("請求")) {
            recommendations.add(Map.of(
                "type", "httpRequest",
                "label", "HTTP 請求",
                "reason", "呼叫外部 API"
            ));
        }

        if (input.contains("slack")) {
            recommendations.add(Map.of(
                "type", "slack",
                "label", "Slack 訊息",
                "reason", "發送 Slack 通知"
            ));
        }

        if (input.contains("telegram")) {
            recommendations.add(Map.of(
                "type", "telegram",
                "label", "Telegram 訊息",
                "reason", "發送 Telegram 通知"
            ));
        }

        // 儲存推薦結果
        context.setInMemory("discoveryResults", recommendations);

        StringBuilder sb = new StringBuilder();
        sb.append("根據您的需求，我推薦以下節點：\n\n");
        for (Map<String, Object> rec : recommendations) {
            sb.append("- **").append(rec.get("label")).append("** (`")
                .append(rec.get("type")).append("`): ")
                .append(rec.get("reason")).append("\n");
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .data(Map.of("recommendedNodes", recommendations))
            .requiresFollowUp(true)
            .nextAction("builder")
            .build();
    }

    private AgentResult parseRecommendationResponse(String response, AgentContext context) {
        try {
            // 提取 JSON
            String json = extractJson(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>)
                parsed.get("recommendedNodes");

            // 儲存到工作記憶
            context.setInMemory("discoveryResults", nodes);

            StringBuilder sb = new StringBuilder();
            sb.append("根據您的需求分析，推薦以下流程設計：\n\n");

            if (parsed.containsKey("flowStructure")) {
                sb.append("**流程結構**: ").append(parsed.get("flowStructure")).append("\n\n");
            }

            sb.append("**推薦節點**:\n");
            for (Map<String, Object> node : nodes) {
                sb.append("- **").append(node.get("label")).append("** (`")
                    .append(node.get("type")).append("`): ")
                    .append(node.get("reason")).append("\n");
            }

            if (parsed.containsKey("missingCapabilities")) {
                @SuppressWarnings("unchecked")
                List<String> missing = (List<String>) parsed.get("missingCapabilities");
                if (!missing.isEmpty()) {
                    sb.append("\n⚠️ **注意**: 可能需要安裝額外元件: ")
                        .append(String.join(", ", missing));
                }
            }

            return AgentResult.builder()
                .success(true)
                .content(sb.toString())
                .data(parsed)
                .requiresFollowUp(true)
                .nextAction("builder")
                .build();

        } catch (Exception e) {
            log.warn("Failed to parse AI recommendation", e);
            return ruleBasedRecommendation(context);
        }
    }

    private AgentResult searchBasedOnUserInput(AgentContext context) {
        String query = context.getUserInput();
        return searchNodes(AgentContext.builder()
            .userInput(query)
            .userId(context.getUserId())
            .intent(Intent.builder().type(Intent.IntentType.SEARCH_NODE).build())
            .build());
    }

    private String extractSearchQuery(AgentContext context) {
        if (context.getIntent() != null && context.getIntent().getEntities() != null) {
            Object query = context.getIntent().getEntities().get("query");
            if (query != null) return query.toString();

            Object targetNode = context.getIntent().getEntities().get("targetNode");
            if (targetNode != null) return targetNode.toString();
        }
        return context.getUserInput();
    }

    private String extractTargetNode(AgentContext context) {
        if (context.getIntent() != null && context.getIntent().getEntities() != null) {
            Object target = context.getIntent().getEntities().get("targetNode");
            if (target != null) return target.toString();

            Object nodeType = context.getIntent().getEntities().get("nodeType");
            if (nodeType != null) return nodeType.toString();
        }
        return null;
    }

    private String extractJson(String content) {
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

    private static final String RECOMMENDATION_SYSTEM_PROMPT = """
        你是一個流程編排專家。根據使用者的需求，從可用的節點中選擇最適合的組合。

        分析時請考慮：
        1. 使用者的實際需求是什麼
        2. 需要什麼觸發方式
        3. 需要哪些資料處理步驟
        4. 最終要輸出或通知到哪裡

        回應必須是有效的 JSON 格式。
        """;
}
