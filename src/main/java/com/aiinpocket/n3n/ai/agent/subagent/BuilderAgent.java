package com.aiinpocket.n3n.ai.agent.subagent;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.agent.tools.*;
import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builder Agent - 流程建構代理
 *
 * 職責：
 * 1. 根據推薦建構流程
 * 2. 新增、移除、連接節點
 * 3. 配置節點參數
 * 4. 驗證流程完整性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuilderAgent implements Agent {

    private final AgentRegistry agentRegistry;
    private final SimpleAIProviderRegistry providerRegistry;
    private final AddNodeTool addNodeTool;
    private final RemoveNodeTool removeNodeTool;
    private final ConnectNodesTool connectNodesTool;
    private final ConfigureNodeTool configureNodeTool;
    private final ValidateFlowTool validateFlowTool;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        agentRegistry.register(this);
    }

    @Override
    public String getId() {
        return "builder";
    }

    @Override
    public String getName() {
        return "Builder Agent";
    }

    @Override
    public String getDescription() {
        return "Flow builder agent, responsible for creating, modifying, and validating flows";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("create_flow", "add_node", "remove_node",
            "connect_nodes", "configure_node", "modify_flow", "optimize_flow");
    }

    @Override
    public List<AgentTool> getTools() {
        return List.of(addNodeTool, removeNodeTool, connectNodesTool,
            configureNodeTool, validateFlowTool);
    }

    @Override
    public AgentResult execute(AgentContext context) {
        log.info("Builder Agent executing for intent: {}",
            context.getIntent() != null ? context.getIntent().getType() : "null");

        try {
            Intent intent = context.getIntent();
            if (intent == null) {
                return buildFromDiscoveryResults(context);
            }

            return switch (intent.getType()) {
                case CREATE_FLOW -> createFlow(context);
                case ADD_NODE -> addNode(context);
                case REMOVE_NODE -> removeNode(context);
                case CONNECT_NODES -> connectNodes(context);
                case CONFIGURE_NODE -> configureNode(context);
                case MODIFY_FLOW -> modifyFlow(context);
                case OPTIMIZE_FLOW -> optimizeFlow(context);
                default -> buildFromDiscoveryResults(context);
            };

        } catch (Exception e) {
            log.error("Builder Agent execution failed", e);
            return AgentResult.error("Build failed");
        }
    }

    @Override
    public Flux<AgentStreamChunk> executeStream(AgentContext context) {
        return Flux.create(sink -> {
            try {
                sink.next(AgentStreamChunk.thinking("Building flow..."));
                sink.next(AgentStreamChunk.progress(10, "analyzing"));

                // For CREATE_FLOW intent, suggest using the Flow Generator wizard
                // which provides multi-turn clarification + real-time preview
                Intent intent = context.getIntent();
                if (intent != null && intent.getType() == Intent.IntentType.CREATE_FLOW) {
                    sink.next(AgentStreamChunk.structured(Map.of(
                        "action", "suggest_generator",
                        "description", context.getUserInput()
                    )));
                }

                AgentResult result = execute(context);

                sink.next(AgentStreamChunk.progress(80, "validating"));

                if (result.isSuccess()) {
                    sink.next(AgentStreamChunk.text(result.getContent()));
                    if (result.getFlowDefinition() != null) {
                        sink.next(AgentStreamChunk.structured(Map.of(
                            "action", "update_flow",
                            "flowDefinition", result.getFlowDefinition()
                        )));
                    }
                } else {
                    sink.next(AgentStreamChunk.error(result.getError()));
                }

                sink.next(AgentStreamChunk.progress(100, "done"));
                sink.next(AgentStreamChunk.done());
                sink.complete();
            } catch (Exception e) {
                log.error("Builder stream failed", e);
                sink.next(AgentStreamChunk.error("Build failed"));
                sink.complete();
            }
        });
    }

    /**
     * 建立新流程
     */
    private AgentResult createFlow(AgentContext context) {
        log.debug("Creating new flow");

        // 確保有流程草稿
        if (context.getFlowDraft() == null) {
            context.setFlowDraft(new WorkingFlowDraft());
        }

        // 檢查是否有 Discovery 結果
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> discoveryResults = context.getFromMemory(
            "discoveryResults", List.class);

        if (discoveryResults != null && !discoveryResults.isEmpty()) {
            return buildFromRecommendations(discoveryResults, context);
        }

        // 使用 AI 自動規劃流程
        return planAndBuildFlow(context);
    }

    /**
     * 根據推薦結果建構流程
     */
    private AgentResult buildFromRecommendations(
            List<Map<String, Object>> recommendations, AgentContext context) {

        log.debug("Building flow from {} recommendations", recommendations.size());
        WorkingFlowDraft draft = context.getFlowDraft();
        List<String> addedNodeIds = new ArrayList<>();
        List<AgentResult.PendingChange> pendingChanges = new ArrayList<>();

        // 逐一新增節點
        for (Map<String, Object> rec : recommendations) {
            String nodeType = (String) rec.get("type");
            String label = (String) rec.get("label");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) rec.get("config");

            ToolResult result = addNodeTool.execute(
                Map.of(
                    "nodeType", nodeType,
                    "label", label != null ? label : nodeType,
                    "config", config != null ? config : Map.of()
                ),
                context
            );

            if (result.isSuccess()) {
                String nodeId = (String) result.getData().get("nodeId");
                addedNodeIds.add(nodeId);

                pendingChanges.add(AgentResult.PendingChange.builder()
                    .id(UUID.randomUUID().toString())
                    .type("add_node")
                    .description("Add node: " + label)
                    .after(result.getData())
                    .build());
            }
        }

        // 自動連接節點（順序連接）
        for (int i = 0; i < addedNodeIds.size() - 1; i++) {
            connectNodesTool.execute(
                Map.of(
                    "sourceId", addedNodeIds.get(i),
                    "targetId", addedNodeIds.get(i + 1)
                ),
                context
            );
        }

        // 驗證流程
        ToolResult validationResult = validateFlowTool.execute(Map.of(), context);

        StringBuilder sb = new StringBuilder();
        sb.append("Flow created with ").append(draft.getNodeCount()).append(" nodes:\n\n");

        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            sb.append("- **").append(node.label()).append("** (`")
                .append(node.type()).append("`)\n");
        }

        if (validationResult.isSuccess()) {
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) validationResult.getData().get("warnings");
            if (warnings != null && !warnings.isEmpty()) {
                sb.append("\n**Warnings**:\n");
                for (String warning : warnings) {
                    sb.append("- ").append(warning).append("\n");
                }
            }

            @SuppressWarnings("unchecked")
            List<String> missingNodes = (List<String>) validationResult.getData().get("missingNodes");
            if (missingNodes != null && !missingNodes.isEmpty()) {
                sb.append("\n**Missing components**:\n");
                for (String missing : missingNodes) {
                    sb.append("- `").append(missing).append("`\n");
                }
            }
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .flowDefinition(draft.toDefinition())
            .pendingChanges(pendingChanges)
            .build();
    }

    /**
     * 使用 AI 規劃並建構流程
     */
    private AgentResult planAndBuildFlow(AgentContext context) {
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(
            "builder", context.getUserId());

        if (!provider.isAvailable()) {
            return AgentResult.error("AI service unavailable, please select nodes through search first");
        }

        try {
            String prompt = String.format("""
                User requirement: %s

                Plan the flow by specifying nodes to add and how to connect them.
                Respond in JSON format:
                {
                  "nodes": [
                    {"type": "nodeType", "label": "label", "config": {}},
                    ...
                  ],
                  "connections": [
                    {"from": 0, "to": 1},
                    ...
                  ]
                }

                Common node types: trigger, scheduleTrigger, webhookTrigger, httpRequest,
                sendEmail, database, code, condition, slack, telegram
                """, context.getUserInput());

            String response = provider.chat(prompt, BUILDER_SYSTEM_PROMPT, 2000, 0.3);
            return parseAndBuildFromPlan(response, context);

        } catch (Exception e) {
            log.error("AI planning failed", e);
            return AgentResult.error("Flow planning failed");
        }
    }

    private AgentResult parseAndBuildFromPlan(String response, AgentContext context) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null) {
                draft = new WorkingFlowDraft();
                context.setFlowDraft(draft);
            }

            List<String> nodeIds = new ArrayList<>();

            // 新增節點
            JsonNode nodesArray = root.get("nodes");
            if (nodesArray != null && nodesArray.isArray()) {
                for (JsonNode nodeJson : nodesArray) {
                    String type = nodeJson.has("type") ? nodeJson.get("type").asText() : "action";
                    String label = nodeJson.has("label") ? nodeJson.get("label").asText() : type;
                    Map<String, Object> config = new HashMap<>();
                    if (nodeJson.has("config")) {
                        config = objectMapper.convertValue(nodeJson.get("config"), Map.class);
                    }

                    String nodeId = draft.addNode(type, label, config);
                    nodeIds.add(nodeId);
                }
            }

            // 建立連接
            JsonNode connectionsArray = root.get("connections");
            if (connectionsArray != null && connectionsArray.isArray()) {
                for (JsonNode connJson : connectionsArray) {
                    int fromIdx = connJson.get("from").asInt();
                    int toIdx = connJson.get("to").asInt();
                    if (fromIdx >= 0 && fromIdx < nodeIds.size() &&
                        toIdx >= 0 && toIdx < nodeIds.size()) {
                        draft.connectNodes(nodeIds.get(fromIdx), nodeIds.get(toIdx));
                    }
                }
            }

            // 驗證
            validateFlowTool.execute(Map.of(), context);

            return AgentResult.builder()
                .success(true)
                .content("Flow created with " + draft.getNodeCount() + " nodes based on your requirements.")
                .flowDefinition(draft.toDefinition())
                .build();

        } catch (Exception e) {
            log.error("Failed to parse build plan", e);
            return AgentResult.error("Failed to parse build plan");
        }
    }

    /**
     * 新增節點
     */
    private AgentResult addNode(AgentContext context) {
        Map<String, Object> entities = context.getIntent().getEntities();
        String nodeType = (String) entities.get("nodeType");
        String label = (String) entities.get("label");

        if (nodeType == null) {
            return AgentResult.error("Please specify the node type to add");
        }

        ToolResult result = addNodeTool.execute(
            Map.of(
                "nodeType", nodeType,
                "label", label != null ? label : nodeType
            ),
            context
        );

        if (!result.isSuccess()) {
            return AgentResult.error(result.getError());
        }

        WorkingFlowDraft draft = context.getFlowDraft();
        return AgentResult.builder()
            .success(true)
            .content("Added node: " + (label != null ? label : nodeType))
            .flowDefinition(draft.toDefinition())
            .build();
    }

    /**
     * 移除節點
     */
    private AgentResult removeNode(AgentContext context) {
        Map<String, Object> entities = context.getIntent().getEntities();
        String nodeId = (String) entities.get("nodeId");
        String nodeLabel = (String) entities.get("label");

        ToolResult result = removeNodeTool.execute(
            Map.of(
                "nodeId", nodeId != null ? nodeId : "",
                "nodeLabel", nodeLabel != null ? nodeLabel : ""
            ),
            context
        );

        if (!result.isSuccess()) {
            return AgentResult.error(result.getError());
        }

        WorkingFlowDraft draft = context.getFlowDraft();
        return AgentResult.builder()
            .success(true)
            .content("Node removed")
            .flowDefinition(draft.toDefinition())
            .pendingChanges(List.of(
                AgentResult.PendingChange.builder()
                    .id(UUID.randomUUID().toString())
                    .type("remove_node")
                    .description("Remove node")
                    .before(result.getData())
                    .build()
            ))
            .build();
    }

    /**
     * 連接節點
     */
    private AgentResult connectNodes(AgentContext context) {
        Map<String, Object> entities = context.getIntent().getEntities();

        ToolResult result = connectNodesTool.execute(
            Map.of(
                "sourceId", entities.getOrDefault("sourceId", ""),
                "targetId", entities.getOrDefault("targetId", ""),
                "sourceLabel", entities.getOrDefault("sourceLabel", ""),
                "targetLabel", entities.getOrDefault("targetLabel", "")
            ),
            context
        );

        if (!result.isSuccess()) {
            return AgentResult.error(result.getError());
        }

        WorkingFlowDraft draft = context.getFlowDraft();
        return AgentResult.builder()
            .success(true)
            .content("Connection created")
            .flowDefinition(draft.toDefinition())
            .build();
    }

    /**
     * 配置節點
     */
    private AgentResult configureNode(AgentContext context) {
        Map<String, Object> entities = context.getIntent().getEntities();
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) entities.get("config");

        if (config == null || config.isEmpty()) {
            return AgentResult.error("Please specify configuration parameters");
        }

        ToolResult result = configureNodeTool.execute(
            Map.of(
                "nodeId", entities.getOrDefault("nodeId", ""),
                "nodeLabel", entities.getOrDefault("label", ""),
                "config", config
            ),
            context
        );

        if (!result.isSuccess()) {
            return AgentResult.error(result.getError());
        }

        WorkingFlowDraft draft = context.getFlowDraft();
        return AgentResult.builder()
            .success(true)
            .content("Node configuration updated")
            .flowDefinition(draft.toDefinition())
            .build();
    }

    /**
     * 修改流程
     */
    private AgentResult modifyFlow(AgentContext context) {
        // 使用 AI 理解修改需求
        return planAndBuildFlow(context);
    }

    /**
     * 優化流程
     */
    private AgentResult optimizeFlow(AgentContext context) {
        WorkingFlowDraft draft = context.getFlowDraft();
        if (draft == null || !draft.hasContent()) {
            return AgentResult.error("No flow available to optimize");
        }

        // 驗證並給出優化建議
        ToolResult validationResult = validateFlowTool.execute(Map.of(), context);

        List<String> optimizations = new ArrayList<>();

        // 檢查是否有重複節點
        Map<String, Long> typeCounts = new HashMap<>();
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            typeCounts.merge(node.type(), 1L, Long::sum);
        }
        for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
            if (entry.getValue() > 1) {
                optimizations.add("Found " + entry.getValue() + " nodes of type " +
                    entry.getKey() + ", consider merging them");
            }
        }

        if (draft.getNodeCount() > 10) {
            optimizations.add("Flow has many nodes, consider splitting into sub-workflows");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Flow Optimization Analysis\n\n");

        if (optimizations.isEmpty()) {
            sb.append("Flow structure looks good, no obvious optimizations needed.\n");
        } else {
            sb.append("**Suggestions**:\n");
            for (String opt : optimizations) {
                sb.append("- ").append(opt).append("\n");
            }
        }

        if (validationResult.isSuccess()) {
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) validationResult.getData().get("warnings");
            if (warnings != null && !warnings.isEmpty()) {
                sb.append("\n**Notes**:\n");
                for (String warning : warnings) {
                    sb.append("- ").append(warning).append("\n");
                }
            }
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .recommendations(optimizations.stream()
                .map(opt -> Map.<String, Object>of("suggestion", opt))
                .toList())
            .build();
    }

    /**
     * 從 Discovery 結果建構
     */
    private AgentResult buildFromDiscoveryResults(AgentContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = context.getFromMemory("discoveryResults", List.class);

        if (results != null && !results.isEmpty()) {
            return buildFromRecommendations(results, context);
        }

        return AgentResult.needsFollowUp(
            "I need to search for suitable nodes first. Please describe what kind of flow you'd like to create.",
            "discovery"
        );
    }

    private String extractJson(String content) {
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private static final String BUILDER_SYSTEM_PROMPT = """
        You are a workflow construction expert. Plan the flow structure based on user requirements.
        Respond in the same language the user used.

        When planning, consider:
        1. What trigger mechanism the flow needs
        2. How data flows between nodes
        3. Whether conditions or loops are needed
        4. What the final output should be

        Response must be valid JSON containing nodes and connections arrays.
        """;
}
