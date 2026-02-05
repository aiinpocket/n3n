package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.ai.rag.RagService;
import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 向量語義搜尋工具
 *
 * 允許 AI Agent 在知識庫中進行語義搜尋。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VectorSearchTool implements AgentNodeTool {

    private final RagService ragService;

    @Override
    public String getId() {
        return "vector_search";
    }

    @Override
    public String getName() {
        return "Vector Search";
    }

    @Override
    public String getDescription() {
        return "搜尋知識庫中與查詢語義相關的文檔。" +
               "可以用於查找相關資料、回答基於文檔的問題。" +
               "參數：query (查詢文字)、top_k (返回數量，預設 5)、store_name (知識庫名稱，可選)";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "要搜尋的查詢文字"
                ),
                "top_k", Map.of(
                    "type", "integer",
                    "description", "返回的結果數量",
                    "default", 5
                ),
                "store_name", Map.of(
                    "type", "string",
                    "description", "知識庫名稱（可選，不指定則使用預設知識庫）"
                )
            ),
            "required", List.of("query")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = (String) parameters.get("query");
                if (query == null || query.isBlank()) {
                    return ToolResult.failure("查詢文字不能為空");
                }

                int topK = parameters.containsKey("top_k")
                        ? ((Number) parameters.get("top_k")).intValue()
                        : 5;

                // 安全限制：防止資源耗盡攻擊
                final int MAX_TOP_K = 100;
                if (topK < 1) {
                    topK = 5;
                } else if (topK > MAX_TOP_K) {
                    log.warn("top_k ({}) exceeds maximum ({}), limiting to {}", topK, MAX_TOP_K, MAX_TOP_K);
                    topK = MAX_TOP_K;
                }

                String storeName = (String) parameters.get("store_name");

                log.debug("Vector search: query='{}', topK={}, store={}",
                        query, topK, storeName);

                List<Document> results = ragService.search(query, topK, storeName);

                if (results.isEmpty()) {
                    return ToolResult.success("未找到與查詢相關的文檔。");
                }

                // 格式化結果
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("找到 %d 個相關文檔：\n\n", results.size()));

                for (int i = 0; i < results.size(); i++) {
                    Document doc = results.get(i);
                    sb.append(String.format("--- 文檔 %d (相似度: %.2f) ---\n",
                            i + 1, doc.getScore() != null ? doc.getScore() : 0));

                    if (doc.getSource() != null) {
                        sb.append("來源: ").append(doc.getSource()).append("\n");
                    }

                    String content = doc.getContent();
                    if (content.length() > 500) {
                        content = content.substring(0, 500) + "...";
                    }
                    sb.append(content).append("\n\n");
                }

                // 返回結果和結構化資料
                List<Map<String, Object>> resultData = results.stream()
                        .map(doc -> Map.<String, Object>of(
                                "content", doc.getContent(),
                                "score", doc.getScore() != null ? doc.getScore() : 0,
                                "source", doc.getSource() != null ? doc.getSource() : "",
                                "metadata", doc.getMetadata() != null ? doc.getMetadata() : Map.of()
                        ))
                        .collect(Collectors.toList());

                return ToolResult.success(sb.toString(), Map.of(
                        "documents", resultData,
                        "count", results.size()
                ));

            } catch (Exception e) {
                log.error("Vector search failed", e);
                return ToolResult.failure("向量搜尋失敗: " + e.getMessage());
            }
        });
    }

    @Override
    public String getCategory() {
        return "search";
    }
}
