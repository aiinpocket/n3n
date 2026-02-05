package com.aiinpocket.n3n.ai.module;

import com.aiinpocket.n3n.ai.codex.NodeCodex;
import com.aiinpocket.n3n.ai.codex.NodeKnowledgeBase;
import com.aiinpocket.n3n.ai.dto.FlowGenerationChunk;
import com.aiinpocket.n3n.ai.prompt.PromptBuilder;
import com.aiinpocket.n3n.execution.handler.NodeHandlerInfo;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.plugin.entity.Plugin;
import com.aiinpocket.n3n.plugin.repository.PluginRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Natural Language Module - Independent AI module for NL to flow generation.
 * Uses configured AI provider (can be different from flow optimization).
 * Enhanced with NodeKnowledgeBase and PromptBuilder for better accuracy.
 *
 * Security features:
 * - Input validation and sanitization to prevent prompt injection
 * - Length limits on user input
 * - Suspicious pattern detection
 */
@Service
@Slf4j
public class NaturalLanguageModule {

    public static final String FEATURE_NAME = "naturalLanguage";

    // 安全限制：最大輸入長度
    private static final int MAX_INPUT_LENGTH = 5000;

    // 可疑模式列表（用於檢測提示注入嘗試）
    private static final List<String> SUSPICIOUS_PATTERNS = List.of(
            "忽略上面", "忽略以上", "忽略前面",
            "ignore above", "ignore previous", "ignore the above",
            "disregard", "forget everything",
            "你現在是", "你是一個", "you are now", "you are a",
            "system prompt", "系統提示",
            "jailbreak", "DAN mode", "developer mode",
            "<|im_start|>", "<|im_end|>", "```system",
            "\\n\\nHuman:", "\\n\\nAssistant:"
    );

    private final SimpleAIProviderRegistry providerRegistry;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final NodeKnowledgeBase nodeKnowledgeBase;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final PluginRepository pluginRepository;

    public NaturalLanguageModule(
            SimpleAIProviderRegistry providerRegistry,
            NodeHandlerRegistry nodeHandlerRegistry,
            NodeKnowledgeBase nodeKnowledgeBase,
            PromptBuilder promptBuilder,
            ObjectMapper objectMapper,
            @Qualifier("pluginPluginRepository") PluginRepository pluginRepository) {
        this.providerRegistry = providerRegistry;
        this.nodeHandlerRegistry = nodeHandlerRegistry;
        this.nodeKnowledgeBase = nodeKnowledgeBase;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.pluginRepository = pluginRepository;
    }

    /**
     * Generate a flow from natural language description
     * Enhanced with NodeKnowledgeBase for better node selection and PromptBuilder for richer prompts.
     */
    public FlowGenerationResult generateFlow(String userInput, UUID userId, Set<String> installedNodeTypes) {
        // 安全檢查：驗證和清理輸入
        String sanitizedInput = sanitizeUserInput(userInput);
        if (sanitizedInput == null) {
            log.warn("User input rejected due to security concerns");
            return FlowGenerationResult.error("輸入內容包含不允許的字符或模式，請重新描述您的需求");
        }

        SimpleAIProvider provider = providerRegistry.getProviderForFeature(FEATURE_NAME, userId);

        if (!provider.isAvailable()) {
            log.warn("AI provider {} not available for flow generation", provider.getName());
            return FlowGenerationResult.unavailable();
        }

        try {
            // Get available node types from knowledge base (enhanced with codex info)
            List<NodeHandlerInfo> availableNodes = nodeHandlerRegistry.listHandlerInfo();

            // Build enhanced prompts using PromptBuilder
            String systemPrompt = promptBuilder.buildSystemPrompt(sanitizedInput);
            String userPrompt = promptBuilder.buildFlowGenerationPrompt(sanitizedInput, installedNodeTypes);

            log.debug("Generated system prompt length: {}, user prompt length: {}",
                    systemPrompt.length(), userPrompt.length());

            String response = provider.chat(userPrompt, systemPrompt, 4096, 0.7);
            return parseFlowGenerationResponse(response, availableNodes, installedNodeTypes);
        } catch (Exception e) {
            log.error("Flow generation failed", e);
            return FlowGenerationResult.error(e.getMessage());
        }
    }

    /**
     * Generate a flow from natural language description with SSE streaming
     * Provides real-time progress updates during flow generation.
     */
    public Flux<FlowGenerationChunk> generateFlowStream(String userInput, UUID userId, Set<String> installedNodeTypes) {
        Sinks.Many<FlowGenerationChunk> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 安全檢查：驗證和清理輸入
        String sanitizedInput = sanitizeUserInput(userInput);
        if (sanitizedInput == null) {
            log.warn("User input rejected due to security concerns");
            sink.tryEmitNext(FlowGenerationChunk.error("輸入內容包含不允許的字符或模式，請重新描述您的需求"));
            sink.tryEmitComplete();
            return sink.asFlux();
        }

        // 使用清理後的輸入
        final String safeInput = sanitizedInput;

        // Execute flow generation in background thread
        Thread.startVirtualThread(() -> {
            try {
                // Phase 1: Analyzing request (0-10%)
                emitProgress(sink, 0, "正在分析您的需求...", "分析請求");
                Thread.sleep(300); // Small delay for UX

                SimpleAIProvider provider = providerRegistry.getProviderForFeature(FEATURE_NAME, userId);
                if (!provider.isAvailable()) {
                    sink.tryEmitNext(FlowGenerationChunk.error("AI 服務不可用"));
                    sink.tryEmitComplete();
                    return;
                }

                emitProgress(sink, 10, "AI 服務已連接", "準備生成");

                // Phase 2: Getting available nodes (10-20%)
                emitProgress(sink, 15, "正在載入可用節點類型...", "載入節點");
                List<NodeHandlerInfo> availableNodes = nodeHandlerRegistry.listHandlerInfo();
                emitProgress(sink, 20, String.format("已載入 %d 種節點類型", availableNodes.size()), "節點就緒");

                // Phase 3: Building prompts (20-30%)
                emitProgress(sink, 25, "正在建構 AI 提示...", "建構提示");
                String systemPrompt = promptBuilder.buildSystemPrompt(safeInput);
                String userPrompt = promptBuilder.buildFlowGenerationPrompt(safeInput, installedNodeTypes);
                emitProgress(sink, 30, "提示建構完成", "準備生成");

                // Phase 4: Calling AI (30-70%)
                sink.tryEmitNext(FlowGenerationChunk.thinking("正在使用 AI 生成流程架構..."));
                emitProgress(sink, 35, "呼叫 AI 模型中...", "AI 生成");

                String response = provider.chat(userPrompt, systemPrompt, 4096, 0.7);
                emitProgress(sink, 70, "AI 回應已接收", "解析結果");

                // Phase 5: Parsing response (70-85%)
                emitProgress(sink, 75, "正在解析 AI 回應...", "解析中");
                FlowGenerationResult result = parseFlowGenerationResponse(response, availableNodes, installedNodeTypes);

                if (!result.success()) {
                    sink.tryEmitNext(FlowGenerationChunk.error(result.error()));
                    sink.tryEmitComplete();
                    return;
                }

                // Emit understanding
                if (result.understanding() != null) {
                    sink.tryEmitNext(FlowGenerationChunk.understanding(result.understanding()));
                }
                emitProgress(sink, 80, "需求理解完成", "建構流程");

                // Phase 6: Emit nodes one by one (85-95%)
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.flowDefinition().get("nodes");
                @SuppressWarnings("unchecked")
                List<Map<String, String>> edges = (List<Map<String, String>>) result.flowDefinition().get("edges");

                int totalNodes = nodes.size();
                int nodeIndex = 0;
                double nodeX = 100;
                double nodeY = 100;

                for (Map<String, Object> node : nodes) {
                    int progressPercent = 85 + (int) ((nodeIndex / (double) totalNodes) * 10);

                    FlowGenerationChunk.NodeData nodeData = FlowGenerationChunk.NodeData.builder()
                            .id((String) node.get("id"))
                            .type((String) node.get("type"))
                            .label((String) node.get("label"))
                            .config(node.get("config") != null ? (Map<String, Object>) node.get("config") : Map.of())
                            .position(FlowGenerationChunk.Position.builder()
                                    .x(nodeX)
                                    .y(nodeY + nodeIndex * 150)
                                    .build())
                            .build();

                    sink.tryEmitNext(FlowGenerationChunk.nodeAdded(nodeData));
                    emitProgress(sink, progressPercent,
                            String.format("新增節點: %s", nodeData.getLabel()), "建構流程");

                    nodeIndex++;
                    Thread.sleep(150); // Small delay for animation effect
                }

                // Emit edges
                for (Map<String, String> edge : edges) {
                    FlowGenerationChunk.EdgeData edgeData = FlowGenerationChunk.EdgeData.builder()
                            .id("edge_" + edge.get("source") + "_" + edge.get("target"))
                            .source(edge.get("source"))
                            .target(edge.get("target"))
                            .build();

                    sink.tryEmitNext(FlowGenerationChunk.edgeAdded(edgeData));
                    Thread.sleep(100);
                }

                // Phase 7: Check for missing nodes (95-100%)
                if (!result.missingNodes().isEmpty()) {
                    emitProgress(sink, 95, "檢測到缺失的節點類型", "檢查依賴");

                    List<FlowGenerationChunk.MissingNodeInfo> missingNodeInfos =
                            buildMissingNodeInfos(result.missingNodes());
                    sink.tryEmitNext(FlowGenerationChunk.missingNodes(missingNodeInfos));
                }

                // Final: Emit completion
                emitProgress(sink, 100, "流程生成完成！", "完成");
                sink.tryEmitNext(FlowGenerationChunk.done(
                        result.flowDefinition(),
                        result.requiredNodes()
                ));

                sink.tryEmitComplete();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sink.tryEmitNext(FlowGenerationChunk.error("流程生成被中斷"));
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.error("Flow generation stream failed", e);
                sink.tryEmitNext(FlowGenerationChunk.error("流程生成失敗: " + e.getMessage()));
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux()
                .timeout(Duration.ofMinutes(2))
                .onErrorResume(e -> {
                    log.error("Flow generation stream error", e);
                    return Flux.just(FlowGenerationChunk.error(e.getMessage()));
                });
    }

    private void emitProgress(Sinks.Many<FlowGenerationChunk> sink, int percent, String message, String stage) {
        sink.tryEmitNext(FlowGenerationChunk.builder()
                .type("progress")
                .progress(percent)
                .message(message)
                .stage(stage)
                .build());
    }

    private List<FlowGenerationChunk.MissingNodeInfo> buildMissingNodeInfos(List<String> missingNodeTypes) {
        List<FlowGenerationChunk.MissingNodeInfo> result = new ArrayList<>();

        for (String nodeType : missingNodeTypes) {
            // Try to find matching plugin
            Optional<Plugin> pluginOpt = pluginRepository.findByName(nodeType);

            FlowGenerationChunk.MissingNodeInfo.MissingNodeInfoBuilder builder =
                    FlowGenerationChunk.MissingNodeInfo.builder()
                    .nodeType(nodeType)
                    .displayName(humanizeNodeType(nodeType))
                    .description(getNodeTypeDescription(nodeType));

            if (pluginOpt.isPresent()) {
                Plugin plugin = pluginOpt.get();
                builder.pluginId(plugin.getId().toString())
                        .canAutoInstall(true);
            } else {
                builder.canAutoInstall(false);
            }

            result.add(builder.build());
        }

        return result;
    }

    private String humanizeNodeType(String nodeType) {
        // Convert camelCase or kebab-case to human readable
        return nodeType
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("-", " ")
                .replaceAll("_", " ");
    }

    private String getNodeTypeDescription(String nodeType) {
        // Get description from knowledge base if available
        List<NodeCodex> codexList = nodeKnowledgeBase.searchNodes(nodeType, 1);
        if (!codexList.isEmpty()) {
            return codexList.get(0).getDescription();
        }
        return "需要安裝此節點類型才能使用";
    }

    /**
     * Recommend nodes based on current flow context
     * Enhanced with NodeKnowledgeBase for smarter recommendations.
     */
    public NodeRecommendationResult recommendNodes(
            Map<String, Object> currentFlow,
            String searchQuery,
            UUID userId,
            Set<String> installedNodeTypes) {

        SimpleAIProvider provider = providerRegistry.getProviderForFeature(FEATURE_NAME, userId);

        if (!provider.isAvailable()) {
            return NodeRecommendationResult.unavailable();
        }

        try {
            // Use enhanced prompt from PromptBuilder
            String prompt = promptBuilder.buildNodeRecommendationPrompt(
                    currentFlow, searchQuery, installedNodeTypes);
            String systemPrompt = getNodeRecommendationSystemPrompt();

            String response = provider.chat(prompt, systemPrompt, 2048, 0.5);
            return parseNodeRecommendationResponse(response, installedNodeTypes);
        } catch (Exception e) {
            log.error("Node recommendation failed", e);
            return NodeRecommendationResult.error(e.getMessage());
        }
    }

    /**
     * Search for nodes using the knowledge base
     */
    public List<NodeCodex> searchNodes(String query, int limit) {
        return nodeKnowledgeBase.searchNodes(query, limit);
    }

    /**
     * Get related nodes for a given node type
     */
    public List<NodeCodex> findRelatedNodes(String nodeType) {
        return nodeKnowledgeBase.findRelatedNodes(nodeType);
    }

    private String buildNodesContext(List<NodeHandlerInfo> nodes, Set<String> installed) {
        Map<String, List<NodeHandlerInfo>> byCategory = nodes.stream()
            .collect(Collectors.groupingBy(n -> n.getCategory() != null ? n.getCategory() : "other"));

        StringBuilder sb = new StringBuilder("Available node types by category:\n");
        for (Map.Entry<String, List<NodeHandlerInfo>> entry : byCategory.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            sb.append(entry.getValue().stream()
                .map(n -> n.getType() + (installed.contains(n.getType()) ? "*" : ""))
                .collect(Collectors.joining(", ")));
            sb.append("\n");
        }
        sb.append("(* = already installed/available)\n");
        return sb.toString();
    }

    private String getFlowGenerationSystemPrompt() {
        return """
            You are a workflow automation expert. Generate workflow definitions from user descriptions.
            Always respond with valid JSON only.
            Use Traditional Chinese for display names and descriptions.
            """;
    }

    private String buildFlowGenerationPrompt(String userInput, String nodesContext) {
        return """
            User request: %s

            %s

            Generate a workflow definition that fulfills the user's request.
            Respond ONLY with this JSON format:
            ```json
            {
              "understanding": "Brief summary of what you understood (in Traditional Chinese)",
              "nodes": [
                {
                  "id": "node_1",
                  "type": "trigger|scheduleTrigger|httpRequest|code|condition|...",
                  "label": "Node display name in Traditional Chinese",
                  "config": {}
                }
              ],
              "edges": [
                {"source": "node_1", "target": "node_2"}
              ],
              "requiredNodes": ["nodeType1", "nodeType2"],
              "missingNodes": ["nodeType that is not in available list"]
            }
            ```
            """.formatted(userInput, nodesContext);
    }

    private FlowGenerationResult parseFlowGenerationResponse(
            String content,
            List<NodeHandlerInfo> availableNodes,
            Set<String> installedNodeTypes) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);

            String understanding = root.has("understanding") ? root.get("understanding").asText() : "";

            // Parse nodes
            List<Map<String, Object>> nodes = new ArrayList<>();
            JsonNode nodesNode = root.get("nodes");
            if (nodesNode != null && nodesNode.isArray()) {
                for (JsonNode n : nodesNode) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", n.get("id").asText());
                    node.put("type", n.get("type").asText());
                    node.put("label", n.has("label") ? n.get("label").asText() : n.get("type").asText());
                    if (n.has("config")) {
                        node.put("config", objectMapper.convertValue(n.get("config"), Map.class));
                    }
                    nodes.add(node);
                }
            }

            // Parse edges
            List<Map<String, String>> edges = new ArrayList<>();
            JsonNode edgesNode = root.get("edges");
            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    edges.add(Map.of(
                        "source", e.get("source").asText(),
                        "target", e.get("target").asText()
                    ));
                }
            }

            // Identify missing nodes
            Set<String> availableTypes = availableNodes.stream()
                .map(NodeHandlerInfo::getType)
                .collect(Collectors.toSet());
            List<String> requiredTypes = nodes.stream()
                .map(n -> (String) n.get("type"))
                .distinct()
                .toList();
            List<String> missingTypes = requiredTypes.stream()
                .filter(t -> !availableTypes.contains(t) && !installedNodeTypes.contains(t))
                .toList();

            return FlowGenerationResult.success(
                understanding,
                Map.of("nodes", nodes, "edges", edges),
                requiredTypes,
                missingTypes
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse flow generation response: {}", content);
            return FlowGenerationResult.error("Failed to parse AI response");
        }
    }

    private Set<String> extractCategories(Map<String, Object> flow, List<NodeHandlerInfo> availableNodes) {
        Map<String, String> typeToCategory = availableNodes.stream()
            .collect(Collectors.toMap(
                NodeHandlerInfo::getType,
                n -> n.getCategory() != null ? n.getCategory() : "other",
                (a, b) -> a
            ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) flow.get("nodes");
        if (nodes == null) return Set.of();

        return nodes.stream()
            .map(n -> (String) n.get("type"))
            .filter(Objects::nonNull)
            .map(t -> typeToCategory.getOrDefault(t, "other"))
            .collect(Collectors.toSet());
    }

    private String buildRecommendationContext(
            Map<String, Object> flow,
            Set<String> usedCategories,
            Set<String> installedTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current flow categories: ").append(String.join(", ", usedCategories)).append("\n");
        sb.append("Installed nodes: ").append(String.join(", ", installedTypes)).append("\n");
        return sb.toString();
    }

    private String getNodeRecommendationSystemPrompt() {
        return """
            You are a workflow automation expert. Recommend useful nodes based on context.
            Always respond with valid JSON only.
            Use Traditional Chinese for descriptions and reasons.
            """;
    }

    private String buildNodeRecommendationPrompt(
            String context,
            String searchQuery,
            List<NodeHandlerInfo> availableNodes,
            Set<String> installedTypes) {

        String nodesList = availableNodes.stream()
            .filter(n -> !installedTypes.contains(n.getType()))
            .map(n -> String.format("- %s (%s): %s",
                n.getType(),
                n.getCategory(),
                n.getDescription()))
            .collect(Collectors.joining("\n"));

        return """
            %s

            User search query: %s

            Available nodes NOT yet installed:
            %s

            Recommend 3-5 nodes that would be useful. For each recommendation, explain why.
            Respond ONLY with this JSON format:
            ```json
            {
              "recommendations": [
                {
                  "nodeType": "httpRequest",
                  "displayName": "HTTP 請求",
                  "reason": "Why this node is useful (in Traditional Chinese)",
                  "pros": ["優點1", "優點2"],
                  "cons": ["注意事項1"]
                }
              ]
            }
            ```
            """.formatted(context, searchQuery != null ? searchQuery : "(無特定搜尋)", nodesList);
    }

    private NodeRecommendationResult parseNodeRecommendationResponse(String content, Set<String> installedTypes) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);

            List<NodeRecommendation> recommendations = new ArrayList<>();
            JsonNode recsNode = root.get("recommendations");

            if (recsNode != null && recsNode.isArray()) {
                for (JsonNode n : recsNode) {
                    String nodeType = n.get("nodeType").asText();
                    // Skip if already installed
                    if (installedTypes.contains(nodeType)) continue;

                    recommendations.add(new NodeRecommendation(
                        nodeType,
                        n.has("displayName") ? n.get("displayName").asText() : nodeType,
                        n.has("reason") ? n.get("reason").asText() : "",
                        extractStringList(n.get("pros")),
                        extractStringList(n.get("cons")),
                        !installedTypes.contains(nodeType)
                    ));
                }
            }

            return NodeRecommendationResult.success(recommendations);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse node recommendation response: {}", content);
            return NodeRecommendationResult.error("Failed to parse AI response");
        }
    }

    private String extractJson(String content) {
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return content.trim();
    }

    private List<String> extractStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * 清理和驗證使用者輸入以防止提示注入攻擊
     *
     * @param input 使用者原始輸入
     * @return 清理後的輸入，如果輸入包含可疑內容則返回 null
     */
    private String sanitizeUserInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        // 長度檢查
        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("User input exceeds maximum length ({} > {})", input.length(), MAX_INPUT_LENGTH);
            return null;
        }

        String lowerInput = input.toLowerCase();

        // 檢查可疑模式
        for (String pattern : SUSPICIOUS_PATTERNS) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                log.warn("Suspicious pattern detected in user input: '{}'", pattern);
                return null;
            }
        }

        // 移除潛在的控制字符（保留正常的空白字符）
        String sanitized = input
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "") // 移除控制字符
                .replaceAll("\\p{Cc}", ""); // 移除不可見字符

        // 限制連續空白
        sanitized = sanitized.replaceAll("\\s{10,}", " ".repeat(10));

        // 確保清理後仍有有效內容
        if (sanitized.isBlank() || sanitized.length() < 5) {
            log.warn("User input too short after sanitization");
            return null;
        }

        return sanitized.trim();
    }

    // Result records
    public record FlowGenerationResult(
        boolean success,
        boolean available,
        String understanding,
        Map<String, Object> flowDefinition,
        List<String> requiredNodes,
        List<String> missingNodes,
        String error
    ) {
        public static FlowGenerationResult success(
                String understanding,
                Map<String, Object> definition,
                List<String> required,
                List<String> missing) {
            return new FlowGenerationResult(true, true, understanding, definition, required, missing, null);
        }

        public static FlowGenerationResult error(String message) {
            return new FlowGenerationResult(false, true, null, null, List.of(), List.of(), message);
        }

        public static FlowGenerationResult unavailable() {
            return new FlowGenerationResult(true, false, null, null, List.of(), List.of(), "AI service unavailable");
        }
    }

    public record NodeRecommendation(
        String nodeType,
        String displayName,
        String reason,
        List<String> pros,
        List<String> cons,
        boolean needsInstall
    ) {}

    public record NodeRecommendationResult(
        boolean success,
        boolean available,
        List<NodeRecommendation> recommendations,
        String error
    ) {
        public static NodeRecommendationResult success(List<NodeRecommendation> recommendations) {
            return new NodeRecommendationResult(true, true, recommendations, null);
        }

        public static NodeRecommendationResult error(String message) {
            return new NodeRecommendationResult(false, true, List.of(), message);
        }

        public static NodeRecommendationResult unavailable() {
            return new NodeRecommendationResult(true, false, List.of(), "AI service unavailable");
        }
    }
}
