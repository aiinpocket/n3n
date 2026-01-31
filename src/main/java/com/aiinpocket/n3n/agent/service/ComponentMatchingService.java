package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.context.ComponentContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 元件匹配服務
 *
 * 分析使用者需求，從已註冊元件中找出最適合的匹配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentMatchingService {

    private final ComponentContextBuilder contextBuilder;

    // 常用關鍵字對應（用於簡單的語意匹配）
    private static final Map<String, List<String>> KEYWORD_MAPPINGS = Map.ofEntries(
        Map.entry("http", List.of("http", "api", "request", "rest", "fetch", "呼叫", "請求")),
        Map.entry("database", List.of("database", "db", "sql", "query", "資料庫", "查詢", "postgres", "mysql")),
        Map.entry("transform", List.of("transform", "convert", "map", "轉換", "映射", "格式")),
        Map.entry("condition", List.of("if", "condition", "判斷", "條件", "分支")),
        Map.entry("loop", List.of("loop", "foreach", "iterate", "迴圈", "遍歷", "批次")),
        Map.entry("notification", List.of("email", "notify", "send", "通知", "郵件", "訊息")),
        Map.entry("file", List.of("file", "read", "write", "upload", "檔案", "讀取", "寫入")),
        Map.entry("schedule", List.of("schedule", "cron", "timer", "排程", "定時")),
        Map.entry("webhook", List.of("webhook", "trigger", "hook", "觸發")),
        Map.entry("javascript", List.of("script", "js", "javascript", "code", "腳本", "程式碼"))
    );

    /**
     * 分析需求並匹配元件
     *
     * @param requirement 使用者需求描述
     * @return 匹配結果
     */
    public MatchResult analyzeRequirement(String requirement) {
        Map<String, Object> context = contextBuilder.buildContext();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> registeredComponents =
            (List<Map<String, Object>>) context.get("registeredComponents");

        if (registeredComponents == null || registeredComponents.isEmpty()) {
            return new MatchResult(List.of(), true, "系統中沒有已註冊的元件");
        }

        List<ComponentMatch> matches = new ArrayList<>();
        String lowerReq = requirement.toLowerCase();

        for (Map<String, Object> component : registeredComponents) {
            double score = calculateMatchScore(lowerReq, component);
            if (score > 0.2) {
                String reason = buildMatchReason(lowerReq, component, score);
                matches.add(new ComponentMatch(
                    (String) component.get("id"),
                    (String) component.get("name"),
                    (String) component.get("displayName"),
                    (String) component.get("description"),
                    (String) component.get("category"),
                    score,
                    reason
                ));
            }
        }

        // 按分數排序
        matches.sort(Comparator.comparing(ComponentMatch::score).reversed());

        // 判斷是否需要新元件
        boolean needsNewComponent = matches.stream().noneMatch(m -> m.score() > 0.6);
        String suggestion = needsNewComponent
            ? "現有元件可能無法完全滿足需求，建議考慮新增專用元件"
            : "找到適合的現有元件";

        return new MatchResult(matches, needsNewComponent, suggestion);
    }

    /**
     * 計算元件與需求的匹配分數
     */
    private double calculateMatchScore(String requirement, Map<String, Object> component) {
        double score = 0.0;

        String name = ((String) component.get("name")).toLowerCase();
        String displayName = component.get("displayName") != null
            ? ((String) component.get("displayName")).toLowerCase() : "";
        String description = component.get("description") != null
            ? ((String) component.get("description")).toLowerCase() : "";
        String category = component.get("category") != null
            ? ((String) component.get("category")).toLowerCase() : "";

        // 名稱完全匹配
        if (requirement.contains(name)) {
            score += 0.5;
        }

        // 類別匹配
        if (requirement.contains(category)) {
            score += 0.2;
        }

        // 關鍵字匹配
        for (Map.Entry<String, List<String>> entry : KEYWORD_MAPPINGS.entrySet()) {
            boolean reqHasKeyword = entry.getValue().stream()
                .anyMatch(requirement::contains);
            boolean compHasKeyword = entry.getValue().stream()
                .anyMatch(kw -> name.contains(kw) || displayName.contains(kw) ||
                               description.contains(kw) || category.contains(kw));

            if (reqHasKeyword && compHasKeyword) {
                score += 0.3;
                break; // 只計一次
            }
        }

        // 描述中的關鍵字匹配
        String[] reqWords = requirement.split("\\s+");
        int matchCount = 0;
        for (String word : reqWords) {
            if (word.length() < 2) continue;
            if (name.contains(word) || displayName.contains(word) || description.contains(word)) {
                matchCount++;
            }
        }
        score += Math.min(0.3, matchCount * 0.1);

        return Math.min(1.0, score);
    }

    /**
     * 建構匹配原因說明
     */
    private String buildMatchReason(String requirement, Map<String, Object> component, double score) {
        String name = (String) component.get("name");
        String description = (String) component.get("description");

        if (score > 0.7) {
            return String.format("「%s」高度匹配您的需求", name);
        } else if (score > 0.4) {
            return String.format("「%s」可能適用：%s",
                name, description != null ? description : "可處理相關功能");
        } else {
            return String.format("「%s」或許可參考", name);
        }
    }

    /**
     * 根據需求推薦元件組合
     *
     * @param requirement 使用者需求
     * @return 推薦的元件組合
     */
    public List<ComponentSuggestion> suggestComponentCombination(String requirement) {
        MatchResult result = analyzeRequirement(requirement);
        List<ComponentSuggestion> suggestions = new ArrayList<>();

        // 取前 5 個最相關的元件
        List<ComponentMatch> topMatches = result.matches().stream()
            .limit(5)
            .toList();

        for (int i = 0; i < topMatches.size(); i++) {
            ComponentMatch match = topMatches.get(i);
            suggestions.add(new ComponentSuggestion(
                match.name(),
                match.displayName(),
                i + 1,
                match.score(),
                match.reason(),
                inferPurpose(requirement, match)
            ));
        }

        return suggestions;
    }

    /**
     * 推斷元件在流程中的用途
     */
    private String inferPurpose(String requirement, ComponentMatch match) {
        String category = match.category();
        if (category == null) {
            return "處理資料";
        }

        return switch (category.toLowerCase()) {
            case "trigger" -> "作為流程觸發點";
            case "action" -> "執行操作";
            case "integration" -> "與外部系統整合";
            case "database" -> "資料庫操作";
            case "transform" -> "資料轉換處理";
            case "condition" -> "條件判斷分支";
            case "notification" -> "發送通知";
            default -> "處理相關邏輯";
        };
    }

    /**
     * 匹配結果
     */
    public record MatchResult(
        List<ComponentMatch> matches,
        boolean needsNewComponent,
        String suggestion
    ) {
        public List<ComponentMatch> getTopMatches(int limit) {
            return matches.stream().limit(limit).toList();
        }

        public boolean hasHighConfidenceMatch() {
            return matches.stream().anyMatch(m -> m.score() > 0.7);
        }
    }

    /**
     * 元件匹配
     */
    public record ComponentMatch(
        String id,
        String name,
        String displayName,
        String description,
        String category,
        double score,
        String reason
    ) {}

    /**
     * 元件建議
     */
    public record ComponentSuggestion(
        String name,
        String displayName,
        int order,
        double confidence,
        String reason,
        String suggestedPurpose
    ) {}
}
