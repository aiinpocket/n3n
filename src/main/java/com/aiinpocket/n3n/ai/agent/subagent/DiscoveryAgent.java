package com.aiinpocket.n3n.ai.agent.subagent;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.agent.tools.SearchNodeTool;
import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
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
    private final AssistantAiClient aiClient;
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
        return "Discovery agent for searching nodes, documentation, and examples";
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
            return AgentResult.error("Discovery failed");
        }
    }

    @Override
    public Flux<AgentStreamChunk> executeStream(AgentContext context) {
        return Flux.create(sink -> {
            try {
                sink.next(AgentStreamChunk.thinking("Searching related components..."));

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
                sink.next(AgentStreamChunk.error("Discovery failed"));
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
                .content("No nodes found matching \"" + query + "\".")
                .data(Map.of("searchQuery", query, "results", List.of()))
                .build();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(nodes.size()).append(" matching node(s):\n\n");

        for (Map<String, Object> node : nodes) {
            sb.append("**").append(node.get("displayName")).append("** (`")
                .append(node.get("type")).append("`)\n");
            if (node.get("description") != null) {
                sb.append("  ").append(node.get("description")).append("\n");
            }
            sb.append("  Category: ").append(node.get("category")).append("\n\n");
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
            return AgentResult.error("Please specify the node type to query");
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
                .content("Node `" + nodeType + "` not found. See search results for similar nodes.")
                .data(searchResult.getData())
                .build();
        }

        var handler = handlerOpt.get();
        StringBuilder doc = new StringBuilder();
        doc.append("## ").append(handler.getDisplayName()).append("\n\n");
        doc.append("**Type**: `").append(handler.getType()).append("`\n\n");
        doc.append("**Category**: ").append(handler.getCategory()).append("\n\n");

        if (handler.getDescription() != null) {
            doc.append("**Description**: ").append(handler.getDescription()).append("\n\n");
        }

        if (handler.getConfigSchema() != null && !handler.getConfigSchema().isEmpty()) {
            doc.append("### Configuration\n\n");
            doc.append("```json\n");
            try {
                doc.append(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(handler.getConfigSchema()));
            } catch (Exception e) {
                doc.append(handler.getConfigSchema().toString());
            }
            doc.append("\n```\n\n");
        }

        if (handler.getInterfaceDefinition() != null && !handler.getInterfaceDefinition().isEmpty()) {
            doc.append("### Input/Output Interface\n\n");
            var iface = handler.getInterfaceDefinition();
            if (iface.containsKey("inputs")) {
                doc.append("**Inputs**: ").append(iface.get("inputs")).append("\n");
            }
            if (iface.containsKey("outputs")) {
                doc.append("**Outputs**: ").append(iface.get("outputs")).append("\n");
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

        StringBuilder sb = new StringBuilder();
        sb.append("## Related Examples\n\n");

        sb.append("### Common Flow Patterns\n\n");

        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("email") || lowerQuery.contains("mail") ||
            lowerQuery.contains("report") || lowerQuery.contains("郵件") || lowerQuery.contains("報表")) {
            sb.append("#### Scheduled Report\n");
            sb.append("```\nSchedule Trigger → Database Query → Data Transform → Send Email\n```\n\n");
        }

        if (lowerQuery.contains("webhook") || lowerQuery.contains("api") ||
            lowerQuery.contains("notification") || lowerQuery.contains("通知")) {
            sb.append("#### Webhook Notification\n");
            sb.append("```\nWebhook Trigger → Data Validation → Condition → Send Slack/Telegram\n```\n\n");
        }

        if (lowerQuery.contains("data") || lowerQuery.contains("sync") ||
            lowerQuery.contains("etl") || lowerQuery.contains("資料") || lowerQuery.contains("同步")) {
            sb.append("#### Data Sync Pipeline\n");
            sb.append("```\nSchedule Trigger → Source DB Query → Data Transform → Target DB Write\n```\n\n");
        }

        sb.append("For specific examples, please describe your requirements in more detail.");

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
        if (!aiClient.isAvailable(context.getUserId())) {
            return ruleBasedRecommendation(context);
        }

        try {
            // 取得所有可用節點的簡要資訊（整行截斷，不會切在行中間）
            List<NodeHandlerInfo> allNodes = nodeHandlerRegistry.listHandlerInfo();
            String nodeList = buildBoundedNodeList(allNodes, NODE_LIST_MAX_CHARS);

            String prompt = String.format("""
                User requirement: %s

                Available node types:
                %s

                Analyze the user's requirement and recommend nodes needed for this flow.
                Only recommend node types that appear in the list above.
                Respond in JSON format:
                {
                  "recommendedNodes": [
                    {"type": "node_type", "label": "suggested label", "reason": "recommendation reason"},
                    ...
                  ],
                  "flowStructure": "suggested flow structure description",
                  "missingCapabilities": ["missing capabilities (if any)"]
                }
                """, userInput, nodeList);

            String response = aiClient.chat(prompt, RECOMMENDATION_SYSTEM_PROMPT, 2000, 0.3, context.getUserId());
            return parseRecommendationResponse(response, context);

        } catch (Exception e) {
            log.warn("AI recommendation failed, using rule-based", e);
            return ruleBasedRecommendation(context);
        }
    }

    /**
     * 規則式推薦（Fallback）
     * 僅在 AI provider 不可用或呼叫失敗時使用；結果以 fallback 標記，非 AI 產生。
     */
    private AgentResult ruleBasedRecommendation(AgentContext context) {
        log.info("Using rule-based node recommendation fallback (AI provider unavailable or failed)");
        String input = context.getUserInput().toLowerCase();
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // Trigger recommendation
        if (input.contains("every") || input.contains("daily") || input.contains("schedule") ||
            input.contains("cron") || input.contains("每天") || input.contains("定時") || input.contains("排程")) {
            recommendations.add(Map.of(
                "type", "scheduleTrigger",
                "label", "Schedule Trigger",
                "reason", "Trigger flow on a time-based schedule"
            ));
        } else if (input.contains("webhook") || input.contains("api")) {
            recommendations.add(Map.of(
                "type", "webhookTrigger",
                "label", "Webhook Trigger",
                "reason", "Trigger flow from external API requests"
            ));
        } else {
            recommendations.add(Map.of(
                "type", "trigger",
                "label", "Manual Trigger",
                "reason", "Start flow manually"
            ));
        }

        // Action recommendations
        if (input.contains("email") || input.contains("mail") || input.contains("郵件")) {
            recommendations.add(Map.of(
                "type", "sendEmail",
                "label", "Send Email",
                "reason", "Send email notifications"
            ));
        }

        if (input.contains("database") || input.contains("query") || input.contains("sql") ||
            input.contains("資料庫") || input.contains("查詢")) {
            recommendations.add(Map.of(
                "type", "database",
                "label", "Database Query",
                "reason", "Execute SQL queries"
            ));
        }

        if (input.contains("http") || input.contains("api") || input.contains("request") ||
            input.contains("請求")) {
            recommendations.add(Map.of(
                "type", "httpRequest",
                "label", "HTTP Request",
                "reason", "Call external APIs"
            ));
        }

        if (input.contains("slack")) {
            recommendations.add(Map.of(
                "type", "slack",
                "label", "Slack Message",
                "reason", "Send Slack notifications"
            ));
        }

        if (input.contains("telegram")) {
            recommendations.add(Map.of(
                "type", "telegram",
                "label", "Telegram Message",
                "reason", "Send Telegram notifications"
            ));
        }

        context.setInMemory("discoveryResults", recommendations);

        StringBuilder sb = new StringBuilder();
        sb.append("Based on your requirements, I recommend the following nodes:\n\n");
        for (Map<String, Object> rec : recommendations) {
            sb.append("- **").append(rec.get("label")).append("** (`")
                .append(rec.get("type")).append("`): ")
                .append(rec.get("reason")).append("\n");
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .data(Map.of("recommendedNodes", recommendations, "fallback", true))
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
            sb.append("Based on your requirements, here is the recommended flow design:\n\n");

            if (parsed.containsKey("flowStructure")) {
                sb.append("**Flow Structure**: ").append(parsed.get("flowStructure")).append("\n\n");
            }

            sb.append("**Recommended Nodes**:\n");
            for (Map<String, Object> node : nodes) {
                sb.append("- **").append(node.get("label")).append("** (`")
                    .append(node.get("type")).append("`): ")
                    .append(node.get("reason")).append("\n");
            }

            if (parsed.containsKey("missingCapabilities")) {
                @SuppressWarnings("unchecked")
                List<String> missing = (List<String>) parsed.get("missingCapabilities");
                if (!missing.isEmpty()) {
                    sb.append("\n⚠️ **Note**: Additional components may be needed: ")
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

    private static final int NODE_LIST_MAX_CHARS = 8000;

    /**
     * 以「整行」為單位組出節點清單，超出上限就停止（絕不切在行中間）。
     * 排序固定（依 type），輸出具決定性。
     */
    private String buildBoundedNodeList(List<NodeHandlerInfo> allNodes, int maxChars) {
        StringBuilder sb = new StringBuilder();
        List<NodeHandlerInfo> sorted = allNodes.stream()
            .sorted(Comparator.comparing(NodeHandlerInfo::getType))
            .toList();
        for (NodeHandlerInfo n : sorted) {
            String line = n.getType() + ": " + n.getDisplayName()
                + (n.getDescription() != null ? " (" + n.getDescription() + ")" : "") + "\n";
            if (sb.length() + line.length() > maxChars) {
                break;
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private static final String RECOMMENDATION_SYSTEM_PROMPT = """
        You are a workflow orchestration expert. Select the best combination of nodes based on the user's requirements.
        Respond in the same language the user used (Chinese, English, or Japanese).

        When analyzing, consider:
        1. What the user actually needs
        2. What trigger type is appropriate
        3. What data processing steps are needed
        4. Where the final output or notification should go

        Response must be valid JSON format.
        """;
}
