package com.aiinpocket.n3n.ai.prompt;

import com.aiinpocket.n3n.ai.codex.NodeCodex;
import com.aiinpocket.n3n.ai.codex.NodeKnowledgeBase;
import com.aiinpocket.n3n.ai.dto.GenerateFlowRequest.ExistingFlowDefinition;
import com.aiinpocket.n3n.ai.dto.GenerateFlowRequest.RequirementContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prompt Builder - Constructs enhanced prompts for AI flow generation.
 * Dynamically injects relevant node knowledge and few-shot examples.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptBuilder {

    private final NodeKnowledgeBase nodeKnowledgeBase;
    private final ObjectMapper objectMapper;

    private String systemPromptTemplate;
    private List<FewShotExample> fewShotExamples;

    private static final int MAX_RELEVANT_NODES = 10;
    private static final int MAX_FEW_SHOT_EXAMPLES = 3;

    @PostConstruct
    public void initialize() {
        loadSystemPromptTemplate();
        loadFewShotExamples();
    }

    /**
     * Build enhanced system prompt with node knowledge
     */
    public String buildSystemPrompt(String userQuery) {
        StringBuilder sb = new StringBuilder();

        // 1. Base system prompt
        sb.append(getBaseSystemPrompt()).append("\n\n");

        // 2. Relevant nodes based on query
        List<NodeCodex> relevantNodes = nodeKnowledgeBase.searchNodes(userQuery, MAX_RELEVANT_NODES);
        if (!relevantNodes.isEmpty()) {
            sb.append("# Relevant Nodes\n\n");
            sb.append("The following nodes are most relevant to the user's requirements:\n\n");
            for (NodeCodex node : relevantNodes) {
                sb.append(node.toPromptDescription()).append("\n");
            }
        }

        // 3. Category summary for reference
        sb.append("# All Node Categories\n\n");
        for (String category : nodeKnowledgeBase.getAllCategories()) {
            List<NodeCodex> nodes = nodeKnowledgeBase.getNodesByCategory(category);
            if (!nodes.isEmpty()) {
                sb.append("- **").append(getCategoryDisplayName(category)).append("**: ");
                sb.append(nodes.stream()
                        .map(n -> n.getType())
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }
        }

        // 4. Few-shot examples
        List<FewShotExample> relevantExamples = findRelevantExamples(userQuery, MAX_FEW_SHOT_EXAMPLES);
        if (!relevantExamples.isEmpty()) {
            sb.append("\n# Examples\n\n");
            for (FewShotExample example : relevantExamples) {
                sb.append("## Example: ").append(example.title).append("\n");
                sb.append("**Request**: ").append(example.userRequest).append("\n");
                sb.append("**Solution**:\n```json\n").append(example.solution).append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Build user prompt for flow generation (without structured requirements).
     */
    public String buildFlowGenerationPrompt(String userInput, Set<String> installedNodeTypes) {
        return buildFlowGenerationPrompt(userInput, null, installedNodeTypes);
    }

    /**
     * Build user prompt for flow generation with optional structured requirements.
     */
    public String buildFlowGenerationPrompt(String userInput, RequirementContext context, Set<String> installedNodeTypes) {
        return buildFlowGenerationPrompt(userInput, context, installedNodeTypes, null, null, null);
    }

    /**
     * Build user prompt for flow generation with full context.
     */
    public String buildFlowGenerationPrompt(String userInput, RequirementContext context,
            Set<String> installedNodeTypes, ExistingFlowDefinition existingFlow, String feedback, String language) {
        StringBuilder sb = new StringBuilder();

        // If iterating on existing flow, adjust the instruction
        if (existingFlow != null) {
            sb.append("# Improve Existing Flow\n\n");
            sb.append("The user wants to improve the following existing flow, not create a new one.\n\n");

            if (existingFlow.getUnderstanding() != null) {
                sb.append("## Previous Understanding\n");
                sb.append(existingFlow.getUnderstanding()).append("\n\n");
            }

            sb.append("## Existing Flow Definition\n");
            try {
                ObjectMapper mapper = new ObjectMapper();
                sb.append("```json\n");
                sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    Map.of("nodes", existingFlow.getNodes(), "edges", existingFlow.getEdges())));
                sb.append("\n```\n\n");
            } catch (Exception e) {
                sb.append("(Failed to serialize existing flow)\n\n");
            }

            if (feedback != null && !feedback.isBlank()) {
                sb.append("## User Feedback\n");
                sb.append(feedback).append("\n\n");
            }

            sb.append("## Modification Instructions\n");
            sb.append(userInput).append("\n\n");
        } else {
            sb.append("# User Requirements\n\n");
            sb.append(userInput).append("\n\n");
        }

        // Inject structured requirements from clarification conversation
        if (context != null) {
            sb.append("# Requirements Analysis (confirmed via conversation)\n\n");
            if (context.getTriggerType() != null || context.getTriggerDescription() != null) {
                sb.append("- **Trigger**: ");
                if (context.getTriggerType() != null) sb.append("[").append(context.getTriggerType()).append("] ");
                if (context.getTriggerDescription() != null) sb.append(context.getTriggerDescription());
                sb.append("\n");
            }
            if (context.getDataSource() != null) {
                sb.append("- **Data Source**: ").append(context.getDataSource()).append("\n");
            }
            if (context.getProcessSteps() != null && !context.getProcessSteps().isEmpty()) {
                sb.append("- **Processing Steps**:\n");
                for (int i = 0; i < context.getProcessSteps().size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(context.getProcessSteps().get(i)).append("\n");
                }
            }
            if (context.getOutputTarget() != null) {
                sb.append("- **Output Target**: ").append(context.getOutputTarget()).append("\n");
            }
            if (context.getErrorHandling() != null) {
                sb.append("- **Error Handling**: ").append(context.getErrorHandling()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("# Available Nodes\n\n");

        // Group by category
        Map<String, List<NodeCodex>> nodesByCategory = new HashMap<>();
        for (NodeCodex codex : nodeKnowledgeBase.getAllCodex()) {
            String category = codex.getCategory() != null ? codex.getCategory() : "other";
            nodesByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(codex);
        }

        for (Map.Entry<String, List<NodeCodex>> entry : nodesByCategory.entrySet()) {
            sb.append("## ").append(getCategoryDisplayName(entry.getKey())).append("\n");
            for (NodeCodex node : entry.getValue()) {
                String installed = installedNodeTypes.contains(node.getType()) ? " ✓" : "";
                sb.append("- **").append(node.getType()).append("**").append(installed);
                sb.append(": ").append(node.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        // Language instruction for user-facing text
        if (language != null && !language.isBlank()) {
            sb.append("# Language Requirement\n\n");
            if (language.startsWith("zh")) {
                sb.append("All understanding, label, and description fields must be in Traditional Chinese.\n\n");
            } else if (language.startsWith("ja")) {
                sb.append("All understanding, label, and description fields must be in Japanese.\n\n");
            } else {
                sb.append("All understanding, label, and description fields must be in English.\n\n");
            }
        }

        sb.append("# Output Format\n\n");
        sb.append(getOutputFormatInstructions());

        return sb.toString();
    }

    /**
     * Build prompt for node recommendation
     */
    public String buildNodeRecommendationPrompt(
            Map<String, Object> currentFlow,
            String searchQuery,
            Set<String> installedNodeTypes) {

        StringBuilder sb = new StringBuilder();

        sb.append("# Current Flow Context\n\n");
        if (currentFlow != null && currentFlow.containsKey("nodes")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) currentFlow.get("nodes");
            sb.append("Current flow contains ").append(nodes.size()).append(" nodes:\n");
            for (Map<String, Object> node : nodes) {
                sb.append("- ").append(node.get("type")).append("\n");
            }
        } else {
            sb.append("No nodes in the current flow.\n");
        }

        sb.append("\n# User Search\n\n");
        sb.append(searchQuery != null ? searchQuery : "(no specific search)").append("\n\n");

        sb.append("# Available Nodes for Recommendation\n\n");
        List<NodeCodex> candidates = nodeKnowledgeBase.searchNodes(searchQuery, 15);
        for (NodeCodex node : candidates) {
            if (!installedNodeTypes.contains(node.getType())) {
                sb.append("- **").append(node.getType()).append("** (").append(node.getCategory()).append("): ");
                sb.append(node.getDescription());
                if (node.getKeywords() != null && !node.getKeywords().isEmpty()) {
                    sb.append(" [keywords: ").append(String.join(", ", node.getKeywords())).append("]");
                }
                sb.append("\n");
            }
        }

        sb.append("\nBased on the current flow and search query, recommend 3-5 most suitable nodes.\n");
        sb.append("For each recommendation, explain why the node is suitable and any usage considerations.\n");

        return sb.toString();
    }

    /**
     * Get base system prompt
     */
    private String getBaseSystemPrompt() {
        if (systemPromptTemplate != null && !systemPromptTemplate.isBlank()) {
            return systemPromptTemplate;
        }

        return """
            # Role

            You are an N3N workflow design expert, specializing in helping users build automation workflows.
            You are proficient in various integration services (APIs, databases, messaging, etc.).
            Respond in the same language the user used (Chinese, English, or Japanese).

            # Core Capabilities

            1. **Understand requirements**: Accurately interpret natural language descriptions, identifying triggers, processing steps, and outputs
            2. **Select nodes**: Choose the most suitable node types based on requirements, considering performance and maintainability
            3. **Design flows**: Create proper node connections and data flow
            4. **Error handling**: Consider potential failures and suggest appropriate handling

            # Design Principles

            1. **Simplicity first**: Use the minimum nodes needed to complete the task
            2. **Readability**: Use clear, descriptive node labels in the user's language
            3. **Maintainability**: Split complex logic into multiple nodes
            4. **Error handling**: Add error handling for external service calls

            # Response Format

            Respond strictly in JSON format with no additional text.
            """;
    }

    /**
     * Get output format instructions
     */
    private String getOutputFormatInstructions() {
        return """
            Respond strictly in the following JSON format:

            ```json
            {
              "understanding": "Summary of your understanding of the user's requirements (in user's language)",
              "nodes": [
                {
                  "id": "node_1",
                  "type": "node type (e.g. trigger, httpRequest, condition)",
                  "label": "node display label (in user's language)",
                  "config": {
                    "configKey": "configValue"
                  }
                }
              ],
              "edges": [
                {"source": "node_1", "target": "node_2"}
              ],
              "requiredNodes": ["list of node types used"],
              "missingNodes": ["node types not in available list"]
            }
            ```

            Important notes:
            1. Each node must have a unique id
            2. The first node is usually a trigger node
            3. edges define connections between nodes
            4. If a required node type is not in the available list, add it to missingNodes
            """;
    }

    /**
     * Load system prompt template from resource
     */
    private void loadSystemPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("ai/prompts/flow-generation.md");
            if (resource.exists()) {
                try (var is = resource.getInputStream()) {
                    systemPromptTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.info("Loaded system prompt template from flow-generation.md");
            }
        } catch (IOException e) {
            log.warn("Failed to load system prompt template: {}", e.getMessage());
        }
    }

    /**
     * Load few-shot examples from resource
     */
    private void loadFewShotExamples() {
        fewShotExamples = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource("ai/prompts/few-shot-examples.json");
            if (resource.exists()) {
                List<Map<String, Object>> examples;
                try (var is = resource.getInputStream()) {
                    examples = objectMapper.readValue(is, new TypeReference<>() {});
                }

                for (Map<String, Object> example : examples) {
                    fewShotExamples.add(new FewShotExample(
                            (String) example.get("title"),
                            (String) example.get("userRequest"),
                            (String) example.get("solution"),
                            example.get("keywords") != null ?
                                    (List<String>) example.get("keywords") : List.of()
                    ));
                }
                log.info("Loaded {} few-shot examples", fewShotExamples.size());
            }
        } catch (IOException e) {
            log.warn("Failed to load few-shot examples: {}", e.getMessage());
        }

        // Add default examples if none loaded
        if (fewShotExamples.isEmpty()) {
            addDefaultExamples();
        }
    }

    /**
     * Add default few-shot examples
     */
    private void addDefaultExamples() {
        fewShotExamples.add(new FewShotExample(
                "Daily Weather Notification",
                "Every day at 8 AM, fetch weather forecast and send to Slack",
                """
                {
                  "understanding": "Create a scheduled task that runs daily at 8 AM, calls a weather API to get forecast data, then sends it to a Slack channel",
                  "nodes": [
                    {"id": "1", "type": "scheduleTrigger", "label": "Daily at 8 AM", "config": {"cron": "0 8 * * *"}},
                    {"id": "2", "type": "httpRequest", "label": "Fetch Weather Forecast", "config": {"method": "GET", "url": "https://api.weather.gov/forecast"}},
                    {"id": "3", "type": "slack", "label": "Send Weather Notification", "config": {"channel": "#general"}}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}],
                  "requiredNodes": ["scheduleTrigger", "httpRequest", "slack"],
                  "missingNodes": []
                }
                """,
                List.of("schedule", "weather", "notification", "slack", "排程", "天氣", "通知", "スケジュール", "天気", "通知")
        ));

        fewShotExamples.add(new FewShotExample(
                "API Monitoring & Alerting",
                "Every 5 minutes check if website is healthy; if response time exceeds 3s, send alert email",
                """
                {
                  "understanding": "Create a monitoring task that runs every 5 minutes, calls the target API and checks response time; if it exceeds the threshold, sends an alert email",
                  "nodes": [
                    {"id": "1", "type": "scheduleTrigger", "label": "Every 5 Minutes", "config": {"interval": "5m"}},
                    {"id": "2", "type": "httpRequest", "label": "Check Website Health", "config": {"method": "GET", "url": "https://example.com/health"}},
                    {"id": "3", "type": "condition", "label": "Response Time Check", "config": {"rules": [{"field": "responseTime", "operator": "gt", "value": 3000}]}},
                    {"id": "4", "type": "sendEmail", "label": "Send Alert Email", "config": {"to": "admin@example.com", "subject": "Slow Response Alert"}}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}, {"source": "3", "target": "4", "sourceHandle": "true"}],
                  "requiredNodes": ["scheduleTrigger", "httpRequest", "condition", "sendEmail"],
                  "missingNodes": []
                }
                """,
                List.of("monitor", "API", "alert", "email", "condition", "監控", "告警", "郵件", "監視", "アラート")
        ));

        fewShotExamples.add(new FewShotExample(
                "Data Synchronization",
                "Read data from Google Sheets and write to database",
                """
                {
                  "understanding": "Read data from a Google Sheets spreadsheet, then batch-insert into the database",
                  "nodes": [
                    {"id": "1", "type": "trigger", "label": "Manual Trigger", "config": {}},
                    {"id": "2", "type": "googleSheets", "label": "Read Spreadsheet", "config": {"operation": "read", "sheetId": ""}},
                    {"id": "3", "type": "loop", "label": "Process Each Row", "config": {}},
                    {"id": "4", "type": "database", "label": "Write to Database", "config": {"operation": "insert"}}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}, {"source": "3", "target": "4"}],
                  "requiredNodes": ["trigger", "googleSheets", "loop", "database"],
                  "missingNodes": []
                }
                """,
                List.of("data", "sync", "sheets", "database", "batch", "資料", "同步", "資料庫", "データ", "同期")
        ));
    }

    /**
     * Find relevant few-shot examples for a query
     */
    private List<FewShotExample> findRelevantExamples(String query, int limit) {
        if (query == null || query.isBlank() || fewShotExamples.isEmpty()) {
            return fewShotExamples.stream().limit(limit).collect(Collectors.toList());
        }

        String lowerQuery = query.toLowerCase();
        return fewShotExamples.stream()
                .sorted((a, b) -> {
                    int scoreA = calculateExampleRelevance(a, lowerQuery);
                    int scoreB = calculateExampleRelevance(b, lowerQuery);
                    return Integer.compare(scoreB, scoreA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int calculateExampleRelevance(FewShotExample example, String query) {
        int score = 0;

        if (example.title.toLowerCase().contains(query)) {
            score += 3;
        }

        if (example.userRequest.toLowerCase().contains(query)) {
            score += 2;
        }

        for (String keyword : example.keywords) {
            if (query.contains(keyword.toLowerCase())) {
                score += 2;
            }
            if (keyword.toLowerCase().contains(query)) {
                score += 1;
            }
        }

        return score;
    }

    private String getCategoryDisplayName(String category) {
        return switch (category) {
            case "trigger" -> "Triggers";
            case "ai" -> "AI & ML";
            case "data" -> "Data Processing";
            case "messaging" -> "Messaging";
            case "database" -> "Database";
            case "cloud" -> "Cloud Services";
            case "integration" -> "Integration";
            case "utility" -> "Utilities";
            case "flow" -> "Flow Control";
            default -> "Other";
        };
    }

    /**
     * Few-shot example record
     */
    private record FewShotExample(
            String title,
            String userRequest,
            String solution,
            List<String> keywords
    ) {}
}
