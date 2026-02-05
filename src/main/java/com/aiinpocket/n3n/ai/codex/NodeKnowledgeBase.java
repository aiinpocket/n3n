package com.aiinpocket.n3n.ai.codex;

import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Node Knowledge Base - Central repository for node codex information.
 * Combines runtime node information with static knowledge from JSON files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeKnowledgeBase {

    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final ObjectMapper objectMapper;

    private final Map<String, NodeCodex> codexMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> keywordIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing Node Knowledge Base...");

        // 1. Load base info from registered handlers
        loadFromHandlers();

        // 2. Enrich with static knowledge from JSON
        loadStaticKnowledge();

        // 3. Build indices
        buildIndices();

        log.info("Node Knowledge Base initialized with {} nodes", codexMap.size());
    }

    /**
     * Get codex for a specific node type
     */
    public Optional<NodeCodex> getCodex(String nodeType) {
        return Optional.ofNullable(codexMap.get(nodeType));
    }

    /**
     * Get all codex entries
     */
    public Collection<NodeCodex> getAllCodex() {
        return Collections.unmodifiableCollection(codexMap.values());
    }

    /**
     * Search nodes by query
     */
    public List<NodeCodex> searchNodes(String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(codexMap.values());
        }

        return codexMap.values().stream()
                .filter(codex -> codex.matchesQuery(query))
                .sorted((a, b) -> Double.compare(
                        b.calculateRelevance(query),
                        a.calculateRelevance(query)))
                .collect(Collectors.toList());
    }

    /**
     * Search nodes by query with limit
     */
    public List<NodeCodex> searchNodes(String query, int limit) {
        return searchNodes(query).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get nodes by category
     */
    public List<NodeCodex> getNodesByCategory(String category) {
        Set<String> types = categoryIndex.get(category);
        if (types == null) {
            return List.of();
        }
        return types.stream()
                .map(codexMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all categories
     */
    public Set<String> getAllCategories() {
        return Collections.unmodifiableSet(categoryIndex.keySet());
    }

    /**
     * Find related nodes for a given node type
     */
    public List<NodeCodex> findRelatedNodes(String nodeType) {
        NodeCodex codex = codexMap.get(nodeType);
        if (codex == null || codex.getRelatedNodes() == null) {
            return List.of();
        }
        return codex.getRelatedNodes().stream()
                .map(codexMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get trigger nodes only
     */
    public List<NodeCodex> getTriggerNodes() {
        return codexMap.values().stream()
                .filter(NodeCodex::isTrigger)
                .collect(Collectors.toList());
    }

    /**
     * Generate prompt context for relevant nodes
     */
    public String generatePromptContext(String userQuery, int maxNodes) {
        List<NodeCodex> relevantNodes = searchNodes(userQuery, maxNodes);

        StringBuilder sb = new StringBuilder();
        sb.append("# 可用節點\n\n");

        for (NodeCodex codex : relevantNodes) {
            sb.append(codex.toPromptDescription()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate category summary for prompt
     */
    public String generateCategorySummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 節點分類\n\n");

        for (String category : getAllCategories()) {
            List<NodeCodex> nodes = getNodesByCategory(category);
            sb.append("## ").append(getCategoryDisplayName(category))
                    .append(" (").append(nodes.size()).append(" 個節點)\n");

            for (NodeCodex node : nodes) {
                sb.append("- ").append(node.getDisplayName())
                        .append(" (").append(node.getType()).append("): ")
                        .append(node.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Load base information from registered handlers
     */
    private void loadFromHandlers() {
        List<NodeHandler> handlers = nodeHandlerRegistry.getAllHandlers();

        for (NodeHandler handler : handlers) {
            try {
                NodeCodex codex = NodeCodex.fromBasicInfo(
                        handler.getType(),
                        handler.getDisplayName(),
                        handler.getDescription(),
                        handler.getCategory(),
                        handler.getIcon(),
                        handler.isTrigger(),
                        handler.supportsAsync(),
                        handler.getConfigSchema()
                );
                codexMap.put(handler.getType(), codex);
            } catch (Exception e) {
                log.warn("Failed to load codex for handler {}: {}", handler.getType(), e.getMessage());
            }
        }

        log.info("Loaded {} handlers into knowledge base", codexMap.size());
    }

    /**
     * Load and merge static knowledge from JSON resource
     */
    private void loadStaticKnowledge() {
        try {
            ClassPathResource resource = new ClassPathResource("ai/node-codex.json");
            if (!resource.exists()) {
                log.warn("Static knowledge file not found: ai/node-codex.json");
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                Map<String, Map<String, Object>> staticData = objectMapper.readValue(
                        is, new TypeReference<>() {});

                for (Map.Entry<String, Map<String, Object>> entry : staticData.entrySet()) {
                    String nodeType = entry.getKey();
                    Map<String, Object> data = entry.getValue();

                    NodeCodex existing = codexMap.get(nodeType);
                    if (existing != null) {
                        // Merge static data into existing codex
                        mergeStaticData(existing, data);
                    } else {
                        // Create new codex from static data only
                        NodeCodex newCodex = createFromStaticData(nodeType, data);
                        codexMap.put(nodeType, newCodex);
                    }
                }

                log.info("Merged static knowledge for {} nodes", staticData.size());
            }
        } catch (IOException e) {
            log.warn("Failed to load static knowledge: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeStaticData(NodeCodex codex, Map<String, Object> data) {
        if (data.containsKey("keywords")) {
            codex.setKeywords((List<String>) data.get("keywords"));
        }
        if (data.containsKey("useCases")) {
            codex.setUseCases((List<String>) data.get("useCases"));
        }
        if (data.containsKey("relatedNodes")) {
            codex.setRelatedNodes((List<String>) data.get("relatedNodes"));
        }
        if (data.containsKey("inputFormats")) {
            codex.setInputFormats((List<String>) data.get("inputFormats"));
        }
        if (data.containsKey("outputFormats")) {
            codex.setOutputFormats((List<String>) data.get("outputFormats"));
        }
        if (data.containsKey("bestPractices")) {
            codex.setBestPractices((String) data.get("bestPractices"));
        }
        if (data.containsKey("commonPatterns")) {
            codex.setCommonPatterns((List<String>) data.get("commonPatterns"));
        }
        if (data.containsKey("examples")) {
            List<Map<String, Object>> exampleMaps = (List<Map<String, Object>>) data.get("examples");
            List<NodeCodex.NodeExample> examples = exampleMaps.stream()
                    .map(this::parseExample)
                    .collect(Collectors.toList());
            codex.setExamples(examples);
        }
    }

    @SuppressWarnings("unchecked")
    private NodeCodex createFromStaticData(String nodeType, Map<String, Object> data) {
        return NodeCodex.builder()
                .type(nodeType)
                .displayName((String) data.getOrDefault("displayName", nodeType))
                .description((String) data.getOrDefault("description", ""))
                .category((String) data.getOrDefault("category", "other"))
                .icon((String) data.getOrDefault("icon", "appstore"))
                .isTrigger(Boolean.TRUE.equals(data.get("isTrigger")))
                .supportsAsync(Boolean.TRUE.equals(data.get("supportsAsync")))
                .keywords((List<String>) data.getOrDefault("keywords", List.of()))
                .useCases((List<String>) data.getOrDefault("useCases", List.of()))
                .relatedNodes((List<String>) data.getOrDefault("relatedNodes", List.of()))
                .inputFormats((List<String>) data.getOrDefault("inputFormats", List.of()))
                .outputFormats((List<String>) data.getOrDefault("outputFormats", List.of()))
                .bestPractices((String) data.get("bestPractices"))
                .commonPatterns((List<String>) data.getOrDefault("commonPatterns", List.of()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private NodeCodex.NodeExample parseExample(Map<String, Object> data) {
        return NodeCodex.NodeExample.builder()
                .scenario((String) data.get("scenario"))
                .description((String) data.get("description"))
                .config((Map<String, Object>) data.get("config"))
                .expectedInput((String) data.get("expectedInput"))
                .expectedOutput((String) data.get("expectedOutput"))
                .build();
    }

    /**
     * Build search indices
     */
    private void buildIndices() {
        categoryIndex.clear();
        keywordIndex.clear();

        for (NodeCodex codex : codexMap.values()) {
            // Category index
            String category = codex.getCategory() != null ? codex.getCategory() : "other";
            categoryIndex.computeIfAbsent(category, k -> new HashSet<>()).add(codex.getType());

            // Keyword index
            if (codex.getKeywords() != null) {
                for (String keyword : codex.getKeywords()) {
                    keywordIndex.computeIfAbsent(keyword.toLowerCase(), k -> new HashSet<>())
                            .add(codex.getType());
                }
            }
        }

        log.info("Built indices: {} categories, {} keywords",
                categoryIndex.size(), keywordIndex.size());
    }

    /**
     * Refresh the knowledge base
     */
    public void refresh() {
        codexMap.clear();
        categoryIndex.clear();
        keywordIndex.clear();
        initialize();
    }

    private String getCategoryDisplayName(String category) {
        return switch (category) {
            case "trigger" -> "觸發器";
            case "ai" -> "AI & ML";
            case "data" -> "資料處理";
            case "messaging" -> "訊息通知";
            case "database" -> "資料庫";
            case "cloud" -> "雲端服務";
            case "integration" -> "外部整合";
            case "utility" -> "工具類";
            case "flow" -> "流程控制";
            default -> "其他";
        };
    }
}
