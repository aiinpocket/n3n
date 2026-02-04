package com.aiinpocket.n3n.ai.agent.tools;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜尋節點工具
 * 根據關鍵字搜尋可用的節點類型
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchNodeTool implements AgentTool {

    private final NodeHandlerRegistry nodeHandlerRegistry;

    @Override
    public String getName() {
        return "search_nodes";
    }

    @Override
    public String getDescription() {
        return "搜尋可用的節點類型。可依關鍵字、類別、功能進行搜尋。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "搜尋關鍵字（例如：發送郵件、HTTP 請求、資料庫）"
                ),
                "category", Map.of(
                    "type", "string",
                    "description", "節點類別（例如：trigger、action、integration、data）"
                ),
                "limit", Map.of(
                    "type", "integer",
                    "description", "回傳結果數量上限，預設 10"
                )
            ),
            "required", List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String query = (String) parameters.get("query");
        String category = (String) parameters.get("category");
        int limit = parameters.containsKey("limit") ?
            ((Number) parameters.get("limit")).intValue() : 10;

        log.debug("Searching nodes with query: {}, category: {}", query, category);

        try {
            List<NodeHandler> handlers = nodeHandlerRegistry.getAllHandlers();

            // 過濾和排序
            List<Map<String, Object>> results = handlers.stream()
                .filter(h -> matchesQuery(h, query))
                .filter(h -> category == null || category.equals(h.getCategory()))
                .sorted((a, b) -> compareRelevance(a, b, query))
                .limit(limit)
                .map(this::toNodeInfo)
                .collect(Collectors.toList());

            // 儲存搜尋結果到工作記憶
            context.setInMemory("searchResults", results);
            context.setInMemory("searchQuery", query);

            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.builder()
                .toolName(getName())
                .success(true)
                .data(Map.of(
                    "nodes", results,
                    "total", results.size(),
                    "query", query
                ))
                .durationMs(duration)
                .build();

        } catch (Exception e) {
            log.error("Failed to search nodes", e);
            return ToolResult.failure(getName(), "搜尋節點失敗: " + e.getMessage());
        }
    }

    private boolean matchesQuery(NodeHandler handler, String query) {
        if (query == null || query.isBlank()) return true;

        String lowerQuery = query.toLowerCase();
        String[] keywords = lowerQuery.split("\\s+");

        String searchableText = String.join(" ",
            handler.getType().toLowerCase(),
            handler.getDisplayName().toLowerCase(),
            handler.getDescription() != null ? handler.getDescription().toLowerCase() : "",
            handler.getCategory().toLowerCase()
        );

        // 所有關鍵字都要匹配
        for (String keyword : keywords) {
            if (!searchableText.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private int compareRelevance(NodeHandler a, NodeHandler b, String query) {
        // 計算相關度分數（越高越好）
        int scoreA = calculateRelevanceScore(a, query);
        int scoreB = calculateRelevanceScore(b, query);
        return scoreB - scoreA; // 降序
    }

    private int calculateRelevanceScore(NodeHandler handler, String query) {
        if (query == null) return 0;

        String lowerQuery = query.toLowerCase();
        int score = 0;

        // 類型名稱完全匹配
        if (handler.getType().toLowerCase().equals(lowerQuery)) {
            score += 100;
        }
        // 類型名稱包含查詢
        else if (handler.getType().toLowerCase().contains(lowerQuery)) {
            score += 50;
        }

        // 顯示名稱匹配
        if (handler.getDisplayName().toLowerCase().contains(lowerQuery)) {
            score += 30;
        }

        // 描述匹配
        if (handler.getDescription() != null &&
            handler.getDescription().toLowerCase().contains(lowerQuery)) {
            score += 10;
        }

        return score;
    }

    private Map<String, Object> toNodeInfo(NodeHandler handler) {
        Map<String, Object> info = new HashMap<>();
        info.put("type", handler.getType());
        info.put("displayName", handler.getDisplayName());
        info.put("description", handler.getDescription());
        info.put("category", handler.getCategory());
        info.put("icon", handler.getIcon());
        info.put("isTrigger", handler.isTrigger());

        // 簡化的配置 Schema
        if (handler.getConfigSchema() != null) {
            info.put("configSchema", handler.getConfigSchema());
        }

        return info;
    }
}
