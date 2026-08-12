package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.agent.AgentContext;
import com.aiinpocket.n3n.ai.agent.AgentResult;
import com.aiinpocket.n3n.ai.agent.AgentStreamChunk;
import com.aiinpocket.n3n.ai.agent.Message;
import com.aiinpocket.n3n.ai.agent.supervisor.SupervisorAgent;
import com.aiinpocket.n3n.ai.conversation.ConversationManager;
import com.aiinpocket.n3n.ai.dto.*;
import com.aiinpocket.n3n.ai.entity.Conversation;
import com.aiinpocket.n3n.ai.module.FlowOptimizationModule;
import com.aiinpocket.n3n.ai.module.NaturalLanguageModule;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.aiinpocket.n3n.ai.usermemory.service.UserMemoryService;
import com.aiinpocket.n3n.execution.handler.NodeHandlerInfo;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.plugin.entity.PluginInstallation;
import com.aiinpocket.n3n.plugin.repository.PluginInstallationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
    private final ConversationManager conversationManager;
    private final UserMemoryService userMemoryService;

    // Node category definitions
    private static final Map<String, CategoryDefinition> CATEGORY_DEFINITIONS = Map.of(
        "trigger", new CategoryDefinition("Triggers", "thunderbolt"),
        "ai", new CategoryDefinition("AI & ML", "robot"),
        "data", new CategoryDefinition("Data Processing", "database"),
        "messaging", new CategoryDefinition("Messaging", "message"),
        "database", new CategoryDefinition("Database", "table"),
        "cloud", new CategoryDefinition("Cloud Services", "cloud"),
        "integration", new CategoryDefinition("Integration", "api"),
        "utility", new CategoryDefinition("Utilities", "tool"),
        "other", new CategoryDefinition("Other", "appstore")
    );

    private record CategoryDefinition(String displayName, String icon) {}

    /**
     * AI 對話串流
     * 使用多代理協作系統處理使用者訊息
     * 支持對話持久化：自動載入歷史、儲存新訊息
     */
    public Flux<ChatStreamChunk> chatStream(ChatStreamRequest request, UUID userId) {
        log.info("Starting chat stream for user: {}", userId);

        // 確保對話存在（如果 conversationId 為空則建立新對話）
        UUID conversationId = ensureConversation(request, userId);

        // 儲存使用者訊息
        try {
            conversationManager.addMessage(conversationId, userId, "user", request.getMessage(), null);
        } catch (Exception e) {
            log.warn("Failed to save user message to conversation {}", conversationId, e);
        }

        // 建立帶有歷史的 Agent 上下文
        AgentContext context = buildAgentContext(request, userId, conversationId);

        // 用於收集完整回應
        StringBuilder responseCollector = new StringBuilder();

        // 先發送含 conversationId 的 metadata chunk
        ChatStreamChunk metaChunk = ChatStreamChunk.builder()
            .type("metadata")
            .conversationId(conversationId.toString())
            .build();

        // 執行 Supervisor Agent 串流
        return Flux.just(metaChunk)
            .concatWith(
                supervisorAgent.executeStream(context)
                    .map(this::convertToChunk)
                    .doOnNext(chunk -> {
                        // 收集文字回應
                        if ("text".equals(chunk.getType()) && chunk.getText() != null) {
                            responseCollector.append(chunk.getText());
                        }
                    })
                    .doOnComplete(() -> {
                        // 儲存 AI 回應
                        String fullResponse = responseCollector.toString();
                        if (!fullResponse.isBlank()) {
                            try {
                                conversationManager.addMessage(conversationId, userId, "assistant", fullResponse, null);
                            } catch (Exception e) {
                                log.warn("Failed to save assistant response to conversation {}", conversationId, e);
                            }
                        }
                    })
            )
            .timeout(Duration.ofMinutes(5))
            .doOnCancel(() -> log.info("Chat stream cancelled by client for conversation {}", conversationId))
            .onErrorResume(e -> {
                log.error("Chat stream error: {}", e.getClass().getSimpleName());
                return Flux.just(ChatStreamChunk.error("AI service error"));
            });
    }

    /**
     * AI 對話（非串流）
     * 支持對話持久化：自動載入歷史、儲存新訊息
     */
    public ChatResponse chat(ChatStreamRequest request, UUID userId) {
        log.info("Starting chat for user: {}", userId);

        try {
            // 確保對話存在
            UUID conversationId = ensureConversation(request, userId);

            // 儲存使用者訊息
            try {
                conversationManager.addMessage(conversationId, userId, "user", request.getMessage(), null);
            } catch (Exception e) {
                log.warn("Failed to save user message to conversation {}", conversationId, e);
            }

            // 建立帶有歷史的 Agent 上下文
            AgentContext context = buildAgentContext(request, userId, conversationId);

            // 執行 Supervisor Agent
            AgentResult result = supervisorAgent.execute(context);

            if (!result.isSuccess()) {
                return ChatResponse.error(result.getError());
            }

            // 儲存 AI 回應
            if (result.getContent() != null && !result.getContent().isBlank()) {
                try {
                    conversationManager.addMessage(conversationId, userId, "assistant", result.getContent(), null);
                } catch (Exception e) {
                    log.warn("Failed to save assistant response to conversation {}", conversationId, e);
                }
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
                conversationId,
                result.getContent(),
                result.getFlowDefinition(),
                pendingChanges
            );

        } catch (Exception e) {
            log.error("Chat error", e);
            return ChatResponse.error("AI service error");
        }
    }

    /**
     * 確保對話存在。如果 request 中有 conversationId 就使用它，
     * 否則建立新的對話。
     */
    private UUID ensureConversation(ChatStreamRequest request, UUID userId) {
        if (request.getConversationId() != null) {
            // 驗證對話存在且屬於當前使用者
            Optional<Conversation> existing = conversationManager.getConversation(request.getConversationId());
            if (existing.isPresent() && userId.equals(existing.get().getUserId())) {
                return request.getConversationId();
            }
            // 對話不存在或不屬於此使用者 → 建立新對話
        }

        // 建立新對話
        UUID flowId = request.getFlowId() != null ? UUID.fromString(request.getFlowId()) : null;
        String title = truncateForTitle(request.getMessage());
        Conversation conversation = conversationManager.createConversation(userId, flowId, title);
        // 回寫到 request 供後續使用
        request.setConversationId(conversation.getId());
        return conversation.getId();
    }

    /**
     * 建立 Agent 執行上下文（含對話歷史）
     */
    private AgentContext buildAgentContext(ChatStreamRequest request, UUID userId, UUID conversationId) {
        AgentContext.AgentContextBuilder builder = AgentContext.builder()
            .conversationId(conversationId)
            .userId(userId)
            .userInput(request.getMessage())
            .flowId(request.getFlowId());

        // 載入對話歷史（並在最前面注入使用者長期記憶）
        try {
            List<Map<String, Object>> historyMaps = conversationManager.getContextForAI(conversationId, userId);
            List<Message> history = new ArrayList<>();

            // 使用者長期記憶：以 system 訊息置於歷史最前，讓各代理都能看見
            String memoryContext = loadUserMemoryContext(userId);
            if (!memoryContext.isEmpty()) {
                history.add(Message.builder()
                    .role("system")
                    .content("使用者的長期記憶（來自過去互動）:\n" + memoryContext)
                    .build());
            }

            historyMaps.stream()
                .map(m -> Message.builder()
                    .role((String) m.get("role"))
                    .content((String) m.get("content"))
                    .build())
                .forEach(history::add);
            builder.conversationHistory(history);
        } catch (Exception e) {
            log.warn("Failed to load conversation history for {}", conversationId, e);
        }

        // 如果有流程定義，設置當前節點和邊
        if (request.getFlowDefinition() != null) {
            builder.currentNodes(request.getFlowDefinition().getNodes());
            builder.currentEdges(request.getFlowDefinition().getEdges());
        }

        return builder.build();
    }

    /**
     * 載入使用者長期記憶區塊；失敗時安靜退回空字串，不影響對話
     */
    private String loadUserMemoryContext(UUID userId) {
        try {
            String context = userMemoryService.buildMemoryContext(userId);
            return context != null ? context : "";
        } catch (Exception e) {
            log.warn("Failed to load user memory context for {}", userId, e);
            return "";
        }
    }

    private String truncateForTitle(String message) {
        if (message == null) return "New Conversation";
        String clean = message.replaceAll("\\s+", " ").trim();
        return clean.length() > 50 ? clean.substring(0, 50) + "..." : clean;
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
                .error("Flow analysis failed")
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
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) updatedDefinition.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawEdges = (List<Map<String, Object>>) updatedDefinition.get("edges");
            List<Map<String, Object>> nodes = new ArrayList<>(rawNodes != null ? rawNodes : List.of());
            List<Map<String, Object>> edges = new ArrayList<>(rawEdges != null ? rawEdges : List.of());

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
            return ApplySuggestionsResponse.error("Error applying suggestions");
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
                        "label", "Error Handler",
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
                0  // custom tool nodes - can be enhanced later
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
            return NodeRecommendationResponse.error("Node recommendation failed");
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
            return GenerateFlowResponse.error("Flow generation failed");
        }
    }

    /**
     * Generate a flow from natural language description with SSE streaming
     * Provides real-time progress updates and incremental flow building.
     */
    public Flux<FlowGenerationChunk> generateFlowStream(GenerateFlowRequest request, UUID userId) {
        log.info("Starting flow generation stream for user: {}", userId);

        Set<String> installedTypes = getInstalledNodeTypes(userId);

        return naturalLanguageModule.generateFlowStreamFull(
                request.getUserInput(),
                userId,
                installedTypes,
                request.getRequirementContext(),
                request.getExistingFlow(),
                request.getFeedback(),
                request.getLanguage()
            )
            .timeout(Duration.ofMinutes(5))
            .doOnCancel(() -> log.info("Flow generation stream cancelled by client for user {}", userId))
            .onErrorResume(e -> {
                log.error("Flow generation stream error: {}", e.getClass().getSimpleName());
                return Flux.just(FlowGenerationChunk.error("Flow generation stream error"));
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
            case "parallel" -> String.format("Can reduce execution time by ~%d%%", Math.min(40, affectedCount * 15));
            case "merge" -> "Reduces API calls and improves efficiency";
            case "remove" -> "Removes redundant nodes and simplifies the flow";
            case "reorder" -> "Optimizes execution order for better efficiency";
            default -> "Improves flow performance";
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
        You are a professional code generation assistant. Your task is to generate correct code from natural language descriptions.
        Respond in the same language the user used.

        Rules:
        1. Output only code, no explanations
        2. Use $input to access input data
        3. Return the result directly, no wrapper functions needed
        4. Handle possible null or undefined values
        5. Code should be concise and performant

        Language-specific rules:
        - JavaScript: Use ES6+ syntax
        - Lodash-style operations (_. functions) are available
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
                return GenerateCodeResponse.failure("Only JavaScript is currently supported");
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
                return GenerateCodeResponse.failure("AI failed to generate valid code");
            }

            return GenerateCodeResponse.success(
                result.code,
                result.explanation,
                request.getLanguage()
            );

        } catch (Exception e) {
            log.error("Code generation failed", e);
            return GenerateCodeResponse.failure("Code generation failed");
        }
    }

    private String buildCodeGenerationPrompt(GenerateCodeRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate JavaScript code based on the following description:\n\n");
        prompt.append("Description: ").append(request.getDescription()).append("\n");

        if (request.getInputSchema() != null && !request.getInputSchema().isEmpty()) {
            prompt.append("\nInput data structure:\n");
            prompt.append(formatSchema(request.getInputSchema())).append("\n");
        }

        if (request.getSampleInput() != null && !request.getSampleInput().isBlank()) {
            prompt.append("\nSample input:\n");
            prompt.append(request.getSampleInput()).append("\n");
        }

        if (request.getOutputSchema() != null && !request.getOutputSchema().isEmpty()) {
            prompt.append("\nExpected output structure:\n");
            prompt.append(formatSchema(request.getOutputSchema())).append("\n");
        }

        prompt.append("\nOutput executable JavaScript code directly (use $input to access input data):");

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
