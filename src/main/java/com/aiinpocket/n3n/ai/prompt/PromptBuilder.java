package com.aiinpocket.n3n.ai.prompt;

import com.aiinpocket.n3n.ai.codex.NodeCodex;
import com.aiinpocket.n3n.ai.codex.NodeKnowledgeBase;
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
            sb.append("# 相關節點\n\n");
            sb.append("以下是與使用者需求最相關的節點：\n\n");
            for (NodeCodex node : relevantNodes) {
                sb.append(node.toPromptDescription()).append("\n");
            }
        }

        // 3. Category summary for reference
        sb.append("# 所有節點分類\n\n");
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
            sb.append("\n# 範例\n\n");
            for (FewShotExample example : relevantExamples) {
                sb.append("## 範例: ").append(example.title).append("\n");
                sb.append("**需求**: ").append(example.userRequest).append("\n");
                sb.append("**解答**:\n```json\n").append(example.solution).append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Build user prompt for flow generation
     */
    public String buildFlowGenerationPrompt(String userInput, Set<String> installedNodeTypes) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 使用者需求\n\n");
        sb.append(userInput).append("\n\n");

        sb.append("# 可用節點\n\n");

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

        sb.append("# 輸出格式\n\n");
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

        sb.append("# 當前流程上下文\n\n");
        if (currentFlow != null && currentFlow.containsKey("nodes")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) currentFlow.get("nodes");
            sb.append("目前流程包含 ").append(nodes.size()).append(" 個節點：\n");
            for (Map<String, Object> node : nodes) {
                sb.append("- ").append(node.get("type")).append("\n");
            }
        } else {
            sb.append("目前沒有任何節點。\n");
        }

        sb.append("\n# 使用者搜尋\n\n");
        sb.append(searchQuery != null ? searchQuery : "(無特定搜尋)").append("\n\n");

        sb.append("# 可推薦的節點\n\n");
        List<NodeCodex> candidates = nodeKnowledgeBase.searchNodes(searchQuery, 15);
        for (NodeCodex node : candidates) {
            if (!installedNodeTypes.contains(node.getType())) {
                sb.append("- **").append(node.getType()).append("** (").append(node.getCategory()).append("): ");
                sb.append(node.getDescription());
                if (node.getKeywords() != null && !node.getKeywords().isEmpty()) {
                    sb.append(" [關鍵字: ").append(String.join(", ", node.getKeywords())).append("]");
                }
                sb.append("\n");
            }
        }

        sb.append("\n請根據當前流程和搜尋需求，推薦 3-5 個最適合的節點。\n");
        sb.append("對於每個推薦，說明為什麼這個節點適合，以及使用時的注意事項。\n");

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
            # 角色定義

            你是 N3N 工作流程設計專家，專門協助用戶建立自動化工作流程。
            你精通各種整合服務（API、資料庫、訊息通知等）的使用方式。

            # 核心能力

            1. **理解需求**: 準確理解用戶的口語化描述，識別關鍵的觸發條件、處理步驟和輸出目標
            2. **選擇節點**: 根據需求選擇最適合的節點類型，考慮效能和維護性
            3. **設計流程**: 建立合理的節點連接和資料流向
            4. **錯誤處理**: 考慮可能的錯誤情況並建議適當的處理方式

            # 設計原則

            1. **簡潔優先**: 使用最少的節點完成任務
            2. **可讀性**: 節點標籤使用清晰的繁體中文描述
            3. **可維護性**: 複雜邏輯拆分成多個節點
            4. **錯誤處理**: 對外部服務調用考慮錯誤處理

            # 輸出語言

            - 所有顯示名稱、標籤、描述使用繁體中文
            - 技術配置值（URL、變數名等）使用英文

            # 回應格式

            請嚴格使用 JSON 格式回應，不要包含其他文字。
            """;
    }

    /**
     * Get output format instructions
     */
    private String getOutputFormatInstructions() {
        return """
            請嚴格按照以下 JSON 格式回應：

            ```json
            {
              "understanding": "對使用者需求的理解摘要（繁體中文）",
              "nodes": [
                {
                  "id": "node_1",
                  "type": "節點類型（如 trigger、httpRequest、condition）",
                  "label": "節點顯示名稱（繁體中文）",
                  "config": {
                    "配置項": "配置值"
                  }
                }
              ],
              "edges": [
                {"source": "node_1", "target": "node_2"}
              ],
              "requiredNodes": ["使用到的節點類型列表"],
              "missingNodes": ["不在可用清單中的節點類型"]
            }
            ```

            注意事項：
            1. 每個節點必須有唯一的 id
            2. 第一個節點通常是觸發器（trigger 類型）
            3. edges 定義節點之間的連接關係
            4. 如果需要的節點不在可用清單中，將其列入 missingNodes
            """;
    }

    /**
     * Load system prompt template from resource
     */
    private void loadSystemPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("ai/prompts/flow-generation.md");
            if (resource.exists()) {
                systemPromptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
                List<Map<String, Object>> examples = objectMapper.readValue(
                        resource.getInputStream(), new TypeReference<>() {});

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
                "每日天氣通知",
                "每天早上 8 點查詢天氣預報並發送到 Slack",
                """
                {
                  "understanding": "建立一個每天早上 8 點執行的排程任務，呼叫天氣 API 取得預報資訊，然後發送到 Slack 頻道",
                  "nodes": [
                    {"id": "1", "type": "scheduleTrigger", "label": "每日早上 8 點", "config": {"cron": "0 8 * * *"}},
                    {"id": "2", "type": "httpRequest", "label": "取得天氣預報", "config": {"method": "GET", "url": "https://api.weather.gov/forecast"}},
                    {"id": "3", "type": "slack", "label": "發送天氣通知", "config": {"channel": "#general"}}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}],
                  "requiredNodes": ["scheduleTrigger", "httpRequest", "slack"],
                  "missingNodes": []
                }
                """,
                List.of("排程", "天氣", "通知", "slack")
        ));

        fewShotExamples.add(new FewShotExample(
                "API 監控與告警",
                "每 5 分鐘檢查網站是否正常，如果回應時間超過 3 秒就發送告警郵件",
                """
                {
                  "understanding": "建立一個每 5 分鐘執行的監控任務，呼叫目標 API 並檢查回應時間，如果超過閾值則發送告警郵件",
                  "nodes": [
                    {"id": "1", "type": "scheduleTrigger", "label": "每 5 分鐘執行", "config": {"interval": "5m"}},
                    {"id": "2", "type": "httpRequest", "label": "檢查網站", "config": {"method": "GET", "url": "https://example.com/health"}},
                    {"id": "3", "type": "condition", "label": "回應時間檢查", "config": {"rules": [{"field": "responseTime", "operator": "gt", "value": 3000}]}},
                    {"id": "4", "type": "sendEmail", "label": "發送告警郵件", "config": {"to": "admin@example.com", "subject": "網站回應緩慢告警"}}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}, {"source": "3", "target": "4", "sourceHandle": "true"}],
                  "requiredNodes": ["scheduleTrigger", "httpRequest", "condition", "sendEmail"],
                  "missingNodes": []
                }
                """,
                List.of("監控", "API", "告警", "郵件", "條件")
        ));

        fewShotExamples.add(new FewShotExample(
                "資料同步",
                "從 Google Sheets 讀取資料並寫入資料庫",
                """
                {
                  "understanding": "從 Google Sheets 試算表讀取資料，然後批次寫入資料庫",
                  "nodes": [
                    {"id": "1", "type": "trigger", "label": "手動觸發", "config": {}},
                    {"id": "2", "type": "googleSheets", "label": "讀取試算表", "config": {"operation": "read", "sheetId": ""}},
                    {"id": "3", "type": "loop", "label": "逐筆處理", "config": {}},
                    {"id": "4", "type": "database", "label": "寫入資料庫", "config": {"operation": "insert"}}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}, {"source": "3", "target": "4"}],
                  "requiredNodes": ["trigger", "googleSheets", "loop", "database"],
                  "missingNodes": []
                }
                """,
                List.of("資料", "同步", "sheets", "資料庫", "批次")
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
