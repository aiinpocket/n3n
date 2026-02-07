package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.agent.AgentContext;
import com.aiinpocket.n3n.ai.agent.AgentResult;
import com.aiinpocket.n3n.ai.agent.AgentStreamChunk;
import com.aiinpocket.n3n.ai.agent.supervisor.SupervisorAgent;
import com.aiinpocket.n3n.ai.dto.*;
import com.aiinpocket.n3n.ai.module.FlowOptimizationModule;
import com.aiinpocket.n3n.ai.module.NaturalLanguageModule;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.aiinpocket.n3n.execution.handler.NodeHandlerInfo;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.plugin.entity.PluginInstallation;
import com.aiinpocket.n3n.plugin.repository.PluginInstallationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIAssistantService {

    private final FlowOptimizationModule flowOptimizationModule;
    private final NaturalLanguageModule naturalLanguageModule;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final PluginInstallationRepository pluginInstallationRepository;
    private final SupervisorAgent supervisorAgent;
    private final SimpleAIProviderRegistry simpleAIProviderRegistry;

    // Node category definitions
    private static final Map<String, CategoryDefinition> CATEGORY_DEFINITIONS = Map.of(
        "trigger", new CategoryDefinition("觸發器", "thunderbolt"),
        "ai", new CategoryDefinition("AI & ML", "robot"),
        "data", new CategoryDefinition("資料處理", "database"),
        "messaging", new CategoryDefinition("訊息通知", "message"),
        "database", new CategoryDefinition("資料庫", "table"),
        "cloud", new CategoryDefinition("雲端服務", "cloud"),
        "integration", new CategoryDefinition("外部整合", "api"),
        "utility", new CategoryDefinition("工具類", "tool"),
        "other", new CategoryDefinition("其他", "appstore")
    );

    private record CategoryDefinition(String displayName, String icon) {}

    /**
     * AI 對話串流
     * 使用多代理協作系統處理使用者訊息
     */
    public Flux<ChatStreamChunk> chatStream(ChatStreamRequest request, UUID userId) {
        log.info("Starting chat stream for user: {}", userId);

        // 建立 Agent 上下文
        AgentContext context = buildAgentContext(request, userId);

        // 執行 Supervisor Agent 串流
        return supervisorAgent.executeStream(context)
            .map(this::convertToChunk)
            .onErrorResume(e -> {
                log.error("Chat stream error", e);
                return Flux.just(ChatStreamChunk.error(e.getMessage()));
            });
    }

    /**
     * AI 對話（非串流）
     */
    public ChatResponse chat(ChatStreamRequest request, UUID userId) {
        log.info("Starting chat for user: {}", userId);

        try {
            // 建立 Agent 上下文
            AgentContext context = buildAgentContext(request, userId);

            // 執行 Supervisor Agent
            AgentResult result = supervisorAgent.execute(context);

            if (!result.isSuccess()) {
                return ChatResponse.error(result.getError());
            }

            // 轉換待確認變更
            List<ChatResponse.PendingChange> pendingChanges = null;
            if (result.getPendingChanges() != null) {
                pendingChanges = result.getPendingChanges().stream()
                    .map(pc -> ChatResponse.PendingChange.builder()
                        .id(pc.getId())
                        .type(pc.getType())
                        .description(pc.getDescription())
                        .before(pc.getBefore())
                        .after(pc.getAfter())
                        .build())
                    .toList();
            }

            return ChatResponse.successWithFlow(
                context.getConversationId(),
                result.getContent(),
                result.getFlowDefinition(),
                pendingChanges
            );

        } catch (Exception e) {
            log.error("Chat error", e);
            return ChatResponse.error(e.getMessage());
        }
    }

    /**
     * 建立 Agent 執行上下文
     */
    private AgentContext buildAgentContext(ChatStreamRequest request, UUID userId) {
        AgentContext.AgentContextBuilder builder = AgentContext.builder()
            .conversationId(request.getConversationId() != null ?
                request.getConversationId() : UUID.randomUUID())
            .userId(userId)
            .userInput(request.getMessage())
            .flowId(request.getFlowId());

        // 如果有流程定義，設置當前節點和邊
        if (request.getFlowDefinition() != null) {
            builder.currentNodes(request.getFlowDefinition().getNodes());
            builder.currentEdges(request.getFlowDefinition().getEdges());
        }

        return builder.build();
    }

    /**
     * 轉換 Agent 串流片段為 DTO
     */
    private ChatStreamChunk convertToChunk(AgentStreamChunk agentChunk) {
        return switch (agentChunk.getType()) {
            case THINKING -> ChatStreamChunk.thinking(agentChunk.getText());
            case TEXT -> ChatStreamChunk.text(agentChunk.getText());
            case STRUCTURED -> ChatStreamChunk.structured(agentChunk.getStructuredData());
            case PROGRESS -> ChatStreamChunk.progress(
                agentChunk.getProgress() != null ? agentChunk.getProgress() : 0,
                agentChunk.getStage()
            );
            case ERROR -> ChatStreamChunk.error(agentChunk.getText());
            case DONE -> ChatStreamChunk.done();
        };
    }

    /**
     * Analyze flow for publish - provides optimization suggestions before publishing
     */
    public PublishAnalysisResponse analyzeForPublish(AnalyzeForPublishRequest request, UUID userId) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Build flow summary
            PublishAnalysisResponse.FlowSummary summary = buildFlowSummary(
                request.getDefinition(),
                request.getVersion()
            );

            // 2. Call flow optimization module
            FlowOptimizationModule.OptimizationResult optResult =
                flowOptimizationModule.analyzeFlow(request.getDefinition(), userId);

            if (!optResult.success() || !optResult.available()) {
                return PublishAnalysisResponse.builder()
                    .success(true)
                    .summary(summary)
                    .suggestions(List.of())
                    .analysisTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }

            // 3. Convert suggestions
            List<PublishAnalysisResponse.OptimizationSuggestion> suggestions = optResult.suggestions().stream()
                .map(this::convertSuggestion)
                .toList();

            return PublishAnalysisResponse.builder()
                .success(true)
                .summary(summary)
                .suggestions(suggestions)
                .analysisTimeMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            log.error("Error analyzing flow for publish", e);
            return PublishAnalysisResponse.builder()
                .success(false)
                .error(e.getMessage())
                .analysisTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * Apply selected suggestions to the flow
     */
    @SuppressWarnings("unchecked")
    public ApplySuggestionsResponse applySuggestions(ApplySuggestionsRequest request) {
        log.info("Applying {} suggestions to flow {}",
            request.getSuggestionIds().size(), request.getFlowId());

        try {
            if (request.getDefinition() == null) {
                return ApplySuggestionsResponse.error("Flow definition cannot be null");
            }

            // Clone the definition to avoid modifying the original
            Map<String, Object> updatedDefinition = new HashMap<>(request.getDefinition());
            List<Map<String, Object>> nodes = new ArrayList<>((List<Map<String, Object>>) updatedDefinition.get("nodes"));
            List<Map<String, Object>> edges = new ArrayList<>((List<Map<String, Object>>) updatedDefinition.get("edges"));

            List<String> appliedIds = new ArrayList<>();

            // Apply each suggestion
            for (String suggestionId : request.getSuggestionIds()) {
                // Find the suggestion details
                ApplySuggestionsRequest.SuggestionInfo suggestion = null;
                if (request.getSuggestions() != null) {
                    suggestion = request.getSuggestions().stream()
                        .filter(s -> suggestionId.equals(s.getId()))
                        .findFirst()
                        .orElse(null);
                }

                if (suggestion != null) {
                    boolean applied = applySingleSuggestion(suggestion, nodes, edges);
                    if (applied) {
                        appliedIds.add(suggestionId);
                    }
                } else {
                    // Fallback: just mark as applied without modification
                    appliedIds.add(suggestionId);
                }
            }

            updatedDefinition.put("nodes", nodes);
            updatedDefinition.put("edges", edges);

            return ApplySuggestionsResponse.success(appliedIds.size(), appliedIds, updatedDefinition);

        } catch (Exception e) {
            log.error("Error applying suggestions", e);
            return ApplySuggestionsResponse.error("Error applying suggestions: " + e.getMessage());
        }
    }

    /**
     * Apply a single suggestion to the flow
     */
    private boolean applySingleSuggestion(
            ApplySuggestionsRequest.SuggestionInfo suggestion,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges) {

        String type = suggestion.getType();
        List<String> affectedNodes = suggestion.getAffectedNodes();

        switch (type) {
            case "parallel" -> {
                // Mark affected nodes for parallel execution
                // This is metadata that the flow engine can use
                for (String nodeId : affectedNodes) {
                    nodes.stream()
                        .filter(n -> nodeId.equals(n.get("id")))
                        .findFirst()
                        .ifPresent(n -> {
                            Map<String, Object> data = (Map<String, Object>) n.get("data");
                            if (data != null) {
                                data.put("parallelExecution", true);
                            }
                        });
                }
                return true;
            }
            case "merge" -> {
                // Merge sequential similar operations
                // For now, just mark them as merged
                if (affectedNodes.size() >= 2) {
                    // Keep the first node, remove others
                    String keepNodeId = affectedNodes.get(0);
                    List<String> removeNodeIds = affectedNodes.subList(1, affectedNodes.size());

                    // Update edges to point to the kept node
                    for (Map<String, Object> edge : edges) {
                        if (removeNodeIds.contains(edge.get("source"))) {
                            edge.put("source", keepNodeId);
                        }
                        if (removeNodeIds.contains(edge.get("target"))) {
                            edge.put("target", keepNodeId);
                        }
                    }

                    // Remove duplicate edges
                    edges.removeIf(e ->
                        e.get("source").equals(e.get("target"))
                    );

                    // Remove the merged nodes
                    nodes.removeIf(n -> removeNodeIds.contains(n.get("id")));

                    return true;
                }
                return false;
            }
            case "remove" -> {
                // Remove specified nodes
                for (String nodeId : affectedNodes) {
                    nodes.removeIf(n -> nodeId.equals(n.get("id")));
                    edges.removeIf(e ->
                        nodeId.equals(e.get("source")) || nodeId.equals(e.get("target"))
                    );
                }
                return true;
            }
            case "reorder" -> {
                // Reorder suggestions typically require more complex graph analysis
                // For now, just mark as applied
                return true;
            }
            case "add_error_handler" -> {
                // Add error handler node
                for (String nodeId : affectedNodes) {
                    String errorHandlerId = "error_" + nodeId + "_" + System.currentTimeMillis();
                    Map<String, Object> errorNode = new HashMap<>();
                    errorNode.put("id", errorHandlerId);
                    errorNode.put("type", "errorHandler");
                    errorNode.put("data", Map.of(
                        "label", "錯誤處理",
                        "nodeType", "errorHandler",
                        "targetNodeId", nodeId
                    ));
                    nodes.add(errorNode);

                    // Connect error output to handler
                    Map<String, Object> errorEdge = new HashMap<>();
                    errorEdge.put("id", "edge_error_" + System.currentTimeMillis());
                    errorEdge.put("source", nodeId);
                    errorEdge.put("target", errorHandlerId);
                    errorEdge.put("sourceHandle", "error");
                    edges.add(errorEdge);
                }
                return true;
            }
            default -> {
                log.debug("Unknown suggestion type: {}", type);
                return false;
            }
        }
    }

    /**
     * Get all node categories with counts
     */
    public List<NodeCategoryInfo> getNodeCategories(UUID userId) {
        List<NodeHandlerInfo> allNodes = nodeHandlerRegistry.listHandlerInfo();
        Set<String> installedTypes = getInstalledNodeTypes(userId);

        // Group by category
        Map<String, List<NodeHandlerInfo>> byCategory = allNodes.stream()
            .collect(Collectors.groupingBy(n -> n.getCategory() != null ? n.getCategory() : "other"));

        List<NodeCategoryInfo> categories = new ArrayList<>();
        for (Map.Entry<String, CategoryDefinition> entry : CATEGORY_DEFINITIONS.entrySet()) {
            String catId = entry.getKey();
            CategoryDefinition def = entry.getValue();
            List<NodeHandlerInfo> catNodes = byCategory.getOrDefault(catId, List.of());

            int installed = (int) catNodes.stream()
                .filter(n -> installedTypes.contains(n.getType()))
                .count();

            // All builtin nodes are "installed" by default
            int builtinCount = catNodes.size();

            categories.add(NodeCategoryInfo.of(
                catId,
                def.displayName(),
                def.icon(),
                builtinCount,  // builtin nodes are always available
                0  // marketplace nodes - can be enhanced later
            ));
        }

        return categories;
    }

    /**
     * Get installed/available nodes, optionally filtered by category
     */
    public List<InstalledNodeInfo> getInstalledNodes(UUID userId, String category) {
        List<NodeHandlerInfo> allNodes = nodeHandlerRegistry.listHandlerInfo();

        // Filter by category if specified
        if (category != null && !category.isEmpty() && !category.equals("all")) {
            allNodes = allNodes.stream()
                .filter(n -> category.equals(n.getCategory()))
                .toList();
        }

        // Convert to InstalledNodeInfo
        return allNodes.stream()
            .map(n -> InstalledNodeInfo.builder()
                .nodeType(n.getType())
                .displayName(n.getDisplayName())
                .description(n.getDescription())
                .category(n.getCategory())
                .icon(n.getIcon())
                .source("builtin")
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Get AI-powered node recommendations based on current flow context
     */
    public NodeRecommendationResponse recommendNodes(
            NodeRecommendationRequest request,
            UUID userId) {

        try {
            // Get categories and installed nodes
            List<NodeCategoryInfo> categories = getNodeCategories(userId);
            List<InstalledNodeInfo> installed = getInstalledNodes(userId, request.getCategory());
            Set<String> installedTypes = getInstalledNodeTypes(userId);

            // Call NL module for AI recommendations
            NaturalLanguageModule.NodeRecommendationResult aiResult =
                naturalLanguageModule.recommendNodes(
                    request.getCurrentFlow(),
                    request.getSearchQuery(),
                    userId,
                    installedTypes
                );

            if (!aiResult.available()) {
                return NodeRecommendationResponse.aiUnavailable(categories, installed);
            }

            // Convert AI recommendations
            List<NodeRecommendation> aiRecs = aiResult.recommendations().stream()
                .map(r -> NodeRecommendation.builder()
                    .nodeType(r.nodeType())
                    .displayName(r.displayName())
                    .description(r.reason())
                    .category(null)
                    .matchReason(r.reason())
                    .pros(r.pros())
                    .cons(r.cons())
                    .source("builtin")
                    .needsInstall(r.needsInstall())
                    .build())
                .toList();

            return NodeRecommendationResponse.success(categories, installed, aiRecs, List.of());

        } catch (Exception e) {
            log.error("Node recommendation failed", e);
            return NodeRecommendationResponse.error(e.getMessage());
        }
    }

    /**
     * Generate a flow from natural language description
     */
    public GenerateFlowResponse generateFlow(GenerateFlowRequest request, UUID userId) {
        try {
            Set<String> installedTypes = getInstalledNodeTypes(userId);

            NaturalLanguageModule.FlowGenerationResult result =
                naturalLanguageModule.generateFlow(
                    request.getUserInput(),
                    userId,
                    installedTypes
                );

            if (!result.available()) {
                return GenerateFlowResponse.aiUnavailable();
            }

            if (!result.success()) {
                return GenerateFlowResponse.error(result.error());
            }

            return GenerateFlowResponse.success(
                result.understanding(),
                result.flowDefinition(),
                result.requiredNodes(),
                result.missingNodes()
            );

        } catch (Exception e) {
            log.error("Flow generation failed", e);
            return GenerateFlowResponse.error(e.getMessage());
        }
    }

    /**
     * Generate a flow from natural language description with SSE streaming
     * Provides real-time progress updates and incremental flow building.
     */
    public Flux<FlowGenerationChunk> generateFlowStream(GenerateFlowRequest request, UUID userId) {
        log.info("Starting flow generation stream for user: {}", userId);

        Set<String> installedTypes = getInstalledNodeTypes(userId);

        return naturalLanguageModule.generateFlowStream(
                request.getUserInput(),
                userId,
                installedTypes
            )
            .onErrorResume(e -> {
                log.error("Flow generation stream error", e);
                return Flux.just(FlowGenerationChunk.error(e.getMessage()));
            });
    }

    /**
     * Get installed node types for a user (builtin + plugins)
     */
    private Set<String> getInstalledNodeTypes(UUID userId) {
        // Start with all builtin node types
        Set<String> types = nodeHandlerRegistry.listHandlerInfo().stream()
            .map(NodeHandlerInfo::getType)
            .collect(Collectors.toSet());

        // Add plugin node types if user has installed plugins
        if (userId != null) {
            try {
                List<PluginInstallation> installations = pluginInstallationRepository.findByUserId(userId);
                // Plugin nodes would be added here based on installation
                // For now, builtin nodes are sufficient
            } catch (Exception e) {
                log.debug("Could not load plugin installations for user {}", userId);
            }
        }

        return types;
    }

    private PublishAnalysisResponse.FlowSummary buildFlowSummary(Map<String, Object> definition, String version) {
        List<Map<String, Object>> nodes = getNodes(definition);
        List<Map<String, Object>> edges = getEdges(definition);

        Set<String> nodeTypes = nodes.stream()
            .map(node -> (String) node.get("type"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<String> connectedNodeIds = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            connectedNodeIds.add((String) edge.get("source"));
            connectedNodeIds.add((String) edge.get("target"));
        }

        boolean hasUnconnectedNodes = nodes.stream()
            .map(node -> (String) node.get("id"))
            .anyMatch(id -> !connectedNodeIds.contains(id) && nodes.size() > 1);

        return PublishAnalysisResponse.FlowSummary.builder()
            .nodeCount(nodes.size())
            .edgeCount(edges.size())
            .version(version)
            .nodeTypes(new ArrayList<>(nodeTypes))
            .hasUnconnectedNodes(hasUnconnectedNodes)
            .hasCycles(false)
            .build();
    }

    private PublishAnalysisResponse.OptimizationSuggestion convertSuggestion(
            FlowOptimizationModule.OptimizationSuggestion original) {

        String benefit = generateBenefitText(original.type(), original.affectedNodes().size());

        return PublishAnalysisResponse.OptimizationSuggestion.builder()
            .id(original.id())
            .type(original.type())
            .title(original.title())
            .description(original.description())
            .benefit(benefit)
            .priority(original.priority())
            .affectedNodes(original.affectedNodes())
            .build();
    }

    private String generateBenefitText(String type, int affectedCount) {
        return switch (type) {
            case "parallel" -> String.format("可減少約 %d%% 執行時間", Math.min(40, affectedCount * 15));
            case "merge" -> "減少 API 呼叫次數，提升效率";
            case "remove" -> "移除冗餘節點，簡化流程";
            case "reorder" -> "優化執行順序，提升效率";
            default -> "改善流程效能";
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getNodes(Map<String, Object> definition) {
        Object nodes = definition.get("nodes");
        if (nodes instanceof List) {
            return (List<Map<String, Object>>) nodes;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getEdges(Map<String, Object> definition) {
        Object edges = definition.get("edges");
        if (edges instanceof List) {
            return (List<Map<String, Object>>) edges;
        }
        return List.of();
    }

    // ==================== Code Generation ====================

    private static final String CODE_GENERATION_SYSTEM_PROMPT = """
        你是一個專業的程式碼生成助手。你的任務是根據使用者的自然語言描述生成正確的程式碼。

        規則：
        1. 只輸出程式碼，不要有任何解釋或說明
        2. 使用 $input 來存取輸入資料
        3. 直接 return 結果，不需要包裝函數
        4. 處理可能的 null 或 undefined
        5. 程式碼需要簡潔且效能良好

        語言特定規則：
        - JavaScript: 使用 ES6+ 語法
        - 可以使用 lodash 風格的操作（_. 函數）
        """;

    /**
     * 使用 AI 生成程式碼
     */
    public GenerateCodeResponse generateCode(GenerateCodeRequest request, UUID userId) {
        log.info("Generating code for user: {}, language: {}", userId, request.getLanguage());

        try {
            // 目前僅支援 JavaScript
            if (!"javascript".equalsIgnoreCase(request.getLanguage()) &&
                !"js".equalsIgnoreCase(request.getLanguage())) {
                return GenerateCodeResponse.failure("目前僅支援 JavaScript");
            }

            // 建構提示詞
            String prompt = buildCodeGenerationPrompt(request);

            // 使用 Failover 機制呼叫 AI
            String aiResponse;
            try {
                aiResponse = simpleAIProviderRegistry.chatWithFailover(
                    prompt,
                    CODE_GENERATION_SYSTEM_PROMPT,
                    2000, // maxTokens
                    0.3,  // temperature (低一點以獲得更穩定的輸出)
                    userId
                );
            } catch (Exception e) {
                log.warn("AI provider not available for code generation", e);
                return GenerateCodeResponse.aiUnavailable();
            }

            // 解析 AI 回應
            CodeGenerationResult result = parseCodeGenerationResponse(aiResponse);

            if (result.code == null || result.code.isBlank()) {
                return GenerateCodeResponse.failure("AI 未能生成有效的程式碼");
            }

            return GenerateCodeResponse.success(
                result.code,
                result.explanation,
                request.getLanguage()
            );

        } catch (Exception e) {
            log.error("Code generation failed", e);
            return GenerateCodeResponse.failure("程式碼生成失敗: " + e.getMessage());
        }
    }

    private String buildCodeGenerationPrompt(GenerateCodeRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("請根據以下描述生成 JavaScript 程式碼：\n\n");
        prompt.append("描述：").append(request.getDescription()).append("\n");

        if (request.getInputSchema() != null && !request.getInputSchema().isEmpty()) {
            prompt.append("\n輸入資料結構：\n");
            prompt.append(formatSchema(request.getInputSchema())).append("\n");
        }

        if (request.getSampleInput() != null && !request.getSampleInput().isBlank()) {
            prompt.append("\n輸入範例：\n");
            prompt.append(request.getSampleInput()).append("\n");
        }

        if (request.getOutputSchema() != null && !request.getOutputSchema().isEmpty()) {
            prompt.append("\n預期輸出結構：\n");
            prompt.append(formatSchema(request.getOutputSchema())).append("\n");
        }

        prompt.append("\n請直接輸出可執行的 JavaScript 程式碼（使用 $input 存取輸入資料）：");

        return prompt.toString();
    }

    private String formatSchema(Map<String, Object> schema) {
        try {
            // 簡單的 JSON 格式化
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            schema.forEach((key, value) -> {
                sb.append("  ").append(key).append(": ").append(value).append(",\n");
            });
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return schema.toString();
        }
    }

    private CodeGenerationResult parseCodeGenerationResponse(String response) {
        String code = response;
        String explanation = null;

        // 嘗試提取程式碼區塊
        if (response.contains("```javascript")) {
            int start = response.indexOf("```javascript") + "```javascript".length();
            int end = response.indexOf("```", start);
            if (end > start) {
                code = response.substring(start, end).trim();
                // 提取解釋（程式碼區塊之後的文字）
                if (end + 3 < response.length()) {
                    explanation = response.substring(end + 3).trim();
                }
            }
        } else if (response.contains("```js")) {
            int start = response.indexOf("```js") + "```js".length();
            int end = response.indexOf("```", start);
            if (end > start) {
                code = response.substring(start, end).trim();
            }
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            // 跳過可能的語言標籤
            int newlineIndex = response.indexOf('\n', start);
            if (newlineIndex > start && newlineIndex - start < 20) {
                start = newlineIndex + 1;
            }
            int end = response.indexOf("```", start);
            if (end > start) {
                code = response.substring(start, end).trim();
            }
        }

        return new CodeGenerationResult(code, explanation);
    }

    private record CodeGenerationResult(String code, String explanation) {}
}
