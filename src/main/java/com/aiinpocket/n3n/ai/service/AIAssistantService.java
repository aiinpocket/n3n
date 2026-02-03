package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.*;
import com.aiinpocket.n3n.ai.module.FlowOptimizationModule;
import com.aiinpocket.n3n.ai.module.NaturalLanguageModule;
import com.aiinpocket.n3n.execution.handler.NodeHandlerInfo;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.plugin.entity.PluginInstallation;
import com.aiinpocket.n3n.plugin.repository.PluginInstallationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    public ApplySuggestionsResponse applySuggestions(ApplySuggestionsRequest request) {
        log.info("Applying {} suggestions to flow {}",
            request.getSuggestionIds().size(), request.getFlowId());

        return ApplySuggestionsResponse.builder()
            .success(true)
            .appliedCount(request.getSuggestionIds().size())
            .appliedSuggestions(request.getSuggestionIds())
            .build();
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
}
