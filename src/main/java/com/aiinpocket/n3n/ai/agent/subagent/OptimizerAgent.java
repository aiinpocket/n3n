package com.aiinpocket.n3n.ai.agent.subagent;

import com.aiinpocket.n3n.ai.agent.*;
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
 * Optimizer Agent - 流程優化代理
 *
 * 職責：
 * 1. 分析流程結構，找出優化機會
 * 2. 檢測效能瓶頸
 * 3. 建議最佳實踐
 * 4. 自動套用高優先級優化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptimizerAgent implements Agent {

    private final AgentRegistry agentRegistry;
    private final SimpleAIProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        agentRegistry.register(this);
    }

    @Override
    public String getId() {
        return "optimizer";
    }

    @Override
    public String getName() {
        return "Optimizer Agent";
    }

    @Override
    public String getDescription() {
        return "Optimizer agent for analyzing and optimizing flow structure and performance";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("analyze_flow", "optimize_flow", "suggest_improvements",
            "detect_bottlenecks", "validate_best_practices");
    }

    @Override
    public List<AgentTool> getTools() {
        return List.of(); // 優化器主要使用 AI 分析，不需要特定工具
    }

    @Override
    public AgentResult execute(AgentContext context) {
        log.info("Optimizer Agent executing");

        try {
            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null || !draft.hasContent()) {
                return AgentResult.error("No flow available to optimize");
            }

            // 收集優化分析
            OptimizationReport report = analyzeFlow(draft, context);

            // 自動套用高優先級優化
            List<String> appliedOptimizations = applyHighPriorityOptimizations(report, context);

            // 建立回應
            return buildOptimizationResponse(report, appliedOptimizations, context);

        } catch (Exception e) {
            log.error("Optimizer Agent execution failed", e);
            return AgentResult.error("Optimization analysis failed");
        }
    }

    @Override
    public Flux<AgentStreamChunk> executeStream(AgentContext context) {
        return Flux.create(sink -> {
            try {
                sink.next(AgentStreamChunk.thinking("Analyzing flow..."));
                sink.next(AgentStreamChunk.progress(10, "checking_structure"));

                WorkingFlowDraft draft = context.getFlowDraft();
                if (draft == null || !draft.hasContent()) {
                    sink.next(AgentStreamChunk.error("No flow available to optimize"));
                    sink.complete();
                    return;
                }

                sink.next(AgentStreamChunk.progress(30, "identifying_optimizations"));
                OptimizationReport report = analyzeFlow(draft, context);

                sink.next(AgentStreamChunk.progress(60, "evaluating_suggestions"));
                List<String> applied = applyHighPriorityOptimizations(report, context);

                sink.next(AgentStreamChunk.progress(90, "generating_report"));
                AgentResult result = buildOptimizationResponse(report, applied, context);

                sink.next(AgentStreamChunk.text(result.getContent()));

                if (!report.suggestions.isEmpty()) {
                    sink.next(AgentStreamChunk.structured(Map.of(
                        "action", "optimization_suggestions",
                        "suggestions", report.suggestions
                    )));
                }

                sink.next(AgentStreamChunk.progress(100, "done"));
                sink.next(AgentStreamChunk.done());
                sink.complete();

            } catch (Exception e) {
                log.error("Optimizer stream failed", e);
                sink.next(AgentStreamChunk.error("Optimization analysis failed"));
                sink.complete();
            }
        });
    }

    /**
     * 分析流程並生成優化報告
     */
    private OptimizationReport analyzeFlow(WorkingFlowDraft draft, AgentContext context) {
        OptimizationReport report = new OptimizationReport();

        // 1. 結構分析
        analyzeStructure(draft, report);

        // 2. 效能分析
        analyzePerformance(draft, report);

        // 3. 最佳實踐檢查
        checkBestPractices(draft, report);

        // 4. 安全性檢查
        checkSecurity(draft, report);

        // 5. 使用 AI 進行深度分析
        if (providerRegistry != null) {
            performAIAnalysis(draft, report, context);
        }

        return report;
    }

    /**
     * 結構分析
     */
    private void analyzeStructure(WorkingFlowDraft draft, OptimizationReport report) {
        int nodeCount = draft.getNodeCount();
        int edgeCount = draft.getEdgeCount();

        if (nodeCount > 20) {
            report.addSuggestion(
                OptimizationSuggestion.high(
                    "Flow too complex",
                    "Flow contains " + nodeCount + " nodes. Consider splitting into sub-workflows",
                    "split_flow"
                )
            );
        } else if (nodeCount > 10) {
            report.addSuggestion(
                OptimizationSuggestion.medium(
                    "Many nodes",
                    "Consider simplifying or merging some nodes",
                    "simplify"
                )
            );
        }

        // 檢查重複節點
        Map<String, Integer> typeCounts = new HashMap<>();
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            typeCounts.merge(node.type(), 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            if (entry.getValue() >= 3) {
                report.addSuggestion(
                    OptimizationSuggestion.medium(
                        "Duplicate node types",
                        "Found " + entry.getValue() + " " + entry.getKey() + " nodes. Consider using loops or sub-workflows",
                        "deduplicate"
                    )
                );
            }
        }

        // 檢查孤立節點
        Set<String> connectedNodes = new HashSet<>();
        for (WorkingFlowDraft.Edge edge : draft.getEdges()) {
            connectedNodes.add(edge.source());
            connectedNodes.add(edge.target());
        }

        int orphanCount = 0;
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            if (!connectedNodes.contains(node.id()) && draft.getNodeCount() > 1) {
                orphanCount++;
            }
        }

        if (orphanCount > 0) {
            report.addSuggestion(
                OptimizationSuggestion.high(
                    "Orphan nodes detected",
                    "Found " + orphanCount + " unconnected node(s). The flow may not execute correctly",
                    "connect_orphans"
                )
            );
        }

        // 記錄統計
        report.setMetric("nodeCount", nodeCount);
        report.setMetric("edgeCount", edgeCount);
        report.setMetric("orphanCount", orphanCount);
    }

    /**
     * 效能分析
     */
    private void analyzePerformance(WorkingFlowDraft draft, OptimizationReport report) {
        // 檢查是否有可並行執行的節點
        Map<String, Set<String>> dependencies = buildDependencyGraph(draft);
        int parallelOpportunities = findParallelOpportunities(dependencies, draft);

        if (parallelOpportunities > 0) {
            report.addSuggestion(
                OptimizationSuggestion.medium(
                    "Parallelization opportunity",
                    "Found " + parallelOpportunities + " node group(s) that can run in parallel for better performance",
                    "parallelize"
                )
            );
        }

        // 檢查 HTTP 請求節點
        int httpRequestCount = 0;
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            if ("httpRequest".equals(node.type()) || "http".equals(node.type())) {
                httpRequestCount++;
            }
        }

        if (httpRequestCount > 5) {
            report.addSuggestion(
                OptimizationSuggestion.medium(
                    "Many HTTP requests",
                    "Flow contains " + httpRequestCount + " HTTP requests. Consider batch requests or caching",
                    "batch_http"
                )
            );
        }

        report.setMetric("parallelOpportunities", parallelOpportunities);
        report.setMetric("httpRequestCount", httpRequestCount);
    }

    /**
     * 最佳實踐檢查
     */
    private void checkBestPractices(WorkingFlowDraft draft, OptimizationReport report) {
        boolean hasTrigger = false;
        boolean hasErrorHandler = false;
        boolean hasLogging = false;

        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            String type = node.type().toLowerCase();
            if (type.contains("trigger") || type.contains("webhook") || type.contains("schedule")) {
                hasTrigger = true;
            }
            if (type.contains("error") || type.contains("catch") || type.contains("exception")) {
                hasErrorHandler = true;
            }
            if (type.contains("log") || type.contains("debug")) {
                hasLogging = true;
            }
        }

        if (!hasTrigger && draft.getNodeCount() > 0) {
            report.addSuggestion(
                OptimizationSuggestion.high(
                    "Missing trigger",
                    "Flow has no trigger node and cannot start automatically",
                    "add_trigger"
                )
            );
        }

        if (!hasErrorHandler && draft.getNodeCount() > 3) {
            report.addSuggestion(
                OptimizationSuggestion.low(
                    "No error handling",
                    "Flow has no error handler node. Consider adding one for robustness",
                    "add_error_handler"
                )
            );
        }

        report.setMetric("hasTrigger", hasTrigger);
        report.setMetric("hasErrorHandler", hasErrorHandler);
        report.setMetric("hasLogging", hasLogging);
    }

    /**
     * 安全性檢查
     */
    private void checkSecurity(WorkingFlowDraft draft, OptimizationReport report) {
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            Map<String, Object> config = node.config();
            if (config == null) continue;

            // 檢查是否有硬編碼的敏感資料
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String key = entry.getKey().toLowerCase();
                Object value = entry.getValue();

                if (value instanceof String strValue) {
                    // 檢查可能的敏感欄位
                    if ((key.contains("password") || key.contains("secret") ||
                         key.contains("token") || key.contains("key") ||
                         key.contains("api_key") || key.contains("apikey")) &&
                        !strValue.startsWith("{{") && !strValue.startsWith("${") &&
                        strValue.length() > 5) {
                        report.addSuggestion(
                            OptimizationSuggestion.high(
                                "Potential sensitive data exposure",
                                "Node \"" + node.label() + "\" field " + key + " may contain hardcoded sensitive data. Use credential management instead",
                                "use_credentials"
                            )
                        );
                    }
                }
            }
        }
    }

    /**
     * AI 深度分析
     */
    private void performAIAnalysis(WorkingFlowDraft draft, OptimizationReport report, AgentContext context) {
        try {
            SimpleAIProvider provider = providerRegistry.getProviderForFeature("optimizer", context.getUserId());
            if (!provider.isAvailable()) {
                return;
            }

            String flowJson = objectMapper.writeValueAsString(draft.toDefinition());
            String prompt = String.format("""
                Analyze the following workflow and provide optimization suggestions (JSON format):

                %s

                Respond in JSON format:
                {
                  "suggestions": [
                    {"priority": "high/medium/low", "title": "title", "description": "description", "action": "action_type"}
                  ],
                  "summary": "overall assessment"
                }
                """, flowJson);

            String response = provider.chat(prompt, OPTIMIZER_SYSTEM_PROMPT, 1000, 0.3);
            parseAISuggestions(response, report);

        } catch (Exception e) {
            log.warn("AI analysis failed: {}", e.getMessage());
        }
    }

    private void parseAISuggestions(String response, OptimizationReport report) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            if (root.has("suggestions") && root.get("suggestions").isArray()) {
                for (JsonNode suggestion : root.get("suggestions")) {
                    String priority = suggestion.has("priority") ? suggestion.get("priority").asText() : "medium";
                    String title = suggestion.has("title") ? suggestion.get("title").asText() : "";
                    String description = suggestion.has("description") ? suggestion.get("description").asText() : "";
                    String action = suggestion.has("action") ? suggestion.get("action").asText() : "";

                    if (!title.isEmpty()) {
                        report.addSuggestion(new OptimizationSuggestion(
                            priority, title, description, action
                        ));
                    }
                }
            }

            if (root.has("summary")) {
                report.setSummary(root.get("summary").asText());
            }

        } catch (Exception e) {
            log.warn("Failed to parse AI suggestions: {}", e.getMessage());
        }
    }

    /**
     * 套用高優先級優化
     */
    private List<String> applyHighPriorityOptimizations(OptimizationReport report, AgentContext context) {
        List<String> applied = new ArrayList<>();

        for (OptimizationSuggestion suggestion : report.suggestions) {
            if (!"high".equals(suggestion.priority)) {
                continue;
            }

            // 自動套用的優化類型
            switch (suggestion.action) {
                case "connect_orphans" -> {
                    // 不自動套用，需要使用者確認
                }
                case "add_trigger" -> {
                    // 不自動套用，需要使用者選擇觸發類型
                }
                default -> {
                    // 其他高優先級建議不自動套用
                }
            }
        }

        return applied;
    }

    /**
     * 建立優化回應
     */
    private AgentResult buildOptimizationResponse(OptimizationReport report,
            List<String> appliedOptimizations, AgentContext context) {

        StringBuilder sb = new StringBuilder();
        sb.append("## Flow Optimization Report\n\n");

        if (report.summary != null && !report.summary.isEmpty()) {
            sb.append("### Overall Assessment\n");
            sb.append(report.summary).append("\n\n");
        }

        sb.append("### Flow Statistics\n");
        sb.append("- Nodes: ").append(report.metrics.getOrDefault("nodeCount", 0)).append("\n");
        sb.append("- Connections: ").append(report.metrics.getOrDefault("edgeCount", 0)).append("\n");
        if ((int) report.metrics.getOrDefault("orphanCount", 0) > 0) {
            sb.append("- ⚠️ Orphan nodes: ").append(report.metrics.get("orphanCount")).append("\n");
        }
        sb.append("\n");

        if (!appliedOptimizations.isEmpty()) {
            sb.append("### ✅ Auto-applied\n");
            for (String opt : appliedOptimizations) {
                sb.append("- ").append(opt).append("\n");
            }
            sb.append("\n");
        }

        if (!report.suggestions.isEmpty()) {
            sb.append("### Optimization Suggestions\n\n");

            // 按優先級分組
            List<OptimizationSuggestion> high = new ArrayList<>();
            List<OptimizationSuggestion> medium = new ArrayList<>();
            List<OptimizationSuggestion> low = new ArrayList<>();

            for (OptimizationSuggestion s : report.suggestions) {
                switch (s.priority) {
                    case "high" -> high.add(s);
                    case "medium" -> medium.add(s);
                    default -> low.add(s);
                }
            }

            if (!high.isEmpty()) {
                sb.append("**🔴 High Priority**\n");
                for (OptimizationSuggestion s : high) {
                    sb.append("- **").append(s.title).append("**: ").append(s.description).append("\n");
                }
                sb.append("\n");
            }

            if (!medium.isEmpty()) {
                sb.append("**🟡 Medium Priority**\n");
                for (OptimizationSuggestion s : medium) {
                    sb.append("- **").append(s.title).append("**: ").append(s.description).append("\n");
                }
                sb.append("\n");
            }

            if (!low.isEmpty()) {
                sb.append("**🟢 Suggestions**\n");
                for (OptimizationSuggestion s : low) {
                    sb.append("- **").append(s.title).append("**: ").append(s.description).append("\n");
                }
            }
        } else {
            sb.append("✅ Flow structure looks good. No optimization issues found.\n");
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .recommendations(report.suggestions.stream()
                .map(s -> Map.<String, Object>of(
                    "priority", s.priority,
                    "title", s.title,
                    "description", s.description,
                    "action", s.action
                ))
                .toList())
            .build();
    }

    private Map<String, Set<String>> buildDependencyGraph(WorkingFlowDraft draft) {
        Map<String, Set<String>> deps = new HashMap<>();
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            deps.put(node.id(), new HashSet<>());
        }
        for (WorkingFlowDraft.Edge edge : draft.getEdges()) {
            deps.computeIfAbsent(edge.target(), k -> new HashSet<>()).add(edge.source());
        }
        return deps;
    }

    private int findParallelOpportunities(Map<String, Set<String>> dependencies, WorkingFlowDraft draft) {
        // 找出可以並行執行的節點（共享相同前置節點的節點）
        Map<String, Set<String>> reverseGraph = new HashMap<>();
        for (WorkingFlowDraft.Edge edge : draft.getEdges()) {
            reverseGraph.computeIfAbsent(edge.source(), k -> new HashSet<>()).add(edge.target());
        }

        int opportunities = 0;
        for (Set<String> children : reverseGraph.values()) {
            if (children.size() > 1) {
                opportunities++;
            }
        }
        return opportunities;
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

    // 內部類別
    private static class OptimizationReport {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();
        String summary;

        void addSuggestion(OptimizationSuggestion suggestion) {
            suggestions.add(suggestion);
        }

        void setMetric(String key, Object value) {
            metrics.put(key, value);
        }

        void setSummary(String summary) {
            this.summary = summary;
        }
    }

    private record OptimizationSuggestion(String priority, String title, String description, String action) {
        static OptimizationSuggestion high(String title, String description, String action) {
            return new OptimizationSuggestion("high", title, description, action);
        }

        static OptimizationSuggestion medium(String title, String description, String action) {
            return new OptimizationSuggestion("medium", title, description, action);
        }

        static OptimizationSuggestion low(String title, String description, String action) {
            return new OptimizationSuggestion("low", title, description, action);
        }
    }

    private static final String OPTIMIZER_SYSTEM_PROMPT = """
        You are a workflow optimization expert. Analyze workflows and provide optimization suggestions.
        Respond in the same language the user used (Chinese, English, or Japanese).

        Focus on:
        1. Whether the flow structure is reasonable
        2. Whether there are redundant or duplicate operations
        3. Whether nodes can run in parallel for better performance
        4. Whether the flow follows best practices
        5. Whether there are security or performance risks

        Provide specific, actionable optimization suggestions.
        """;
}
