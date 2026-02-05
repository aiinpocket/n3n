package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.rag.RagService;
import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 節點處理器
 *
 * 處理 RAG 相關節點類型的問答、搜尋、索引等操作
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RagNodeHandler extends AbstractNodeHandler {

    private final RagService ragService;

    @Override
    public String getType() {
        return "aiRag";
    }

    @Override
    public String getDisplayName() {
        return "RAG Q&A";
    }

    @Override
    public String getDescription() {
        return "基於檢索增強生成的文檔問答系統";
    }

    @Override
    public String getCategory() {
        return "ai";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "qa");
        log.debug("Executing RAG node: operation={}, nodeId={}", operation, context.getNodeId());

        try {
            return switch (operation.toLowerCase()) {
                case "qa" -> executeRagQA(context);
                case "search" -> executeVectorSearch(context);
                case "index" -> executeIndex(context);
                case "clear" -> executeClear(context);
                default -> NodeExecutionResult.builder()
                        .success(false)
                        .errorMessage("Unknown RAG operation: " + operation)
                        .build();
            };
        } catch (Exception e) {
            log.error("RAG node execution failed", e);
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("RAG operation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 執行 RAG 問答
     */
    private NodeExecutionResult executeRagQA(NodeExecutionContext context) {
        Map<String, Object> input = context.getInputData();

        String question = (String) input.get("question");
        if (question == null) {
            question = (String) input.get("input");
        }
        if (question == null || question.isBlank()) {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Question is required for RAG Q&A")
                    .build();
        }

        String storeName = getStringConfig(context, "storeName", null);

        String answer = ragService.ask(question, storeName);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("answer", answer);
        outputs.put("question", question);

        return NodeExecutionResult.builder()
                .success(true)
                .output(outputs)
                .build();
    }

    /**
     * 執行向量搜尋
     */
    private NodeExecutionResult executeVectorSearch(NodeExecutionContext context) {
        Map<String, Object> input = context.getInputData();

        String query = (String) input.get("query");
        if (query == null) {
            query = (String) input.get("input");
        }
        if (query == null || query.isBlank()) {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Query is required for vector search")
                    .build();
        }

        String storeName = getStringConfig(context, "storeName", null);
        int topK = getIntConfig(context, "topK", 5);
        double minScore = getDoubleConfig(context, "minScore", 0.0);

        List<Document> results = ragService.search(query, topK, storeName);

        if (minScore > 0) {
            results = results.stream()
                    .filter(doc -> doc.getScore() != null && doc.getScore() >= minScore)
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> documents = results.stream()
                .map(doc -> {
                    Map<String, Object> docMap = new HashMap<>();
                    docMap.put("content", doc.getContent());
                    docMap.put("score", doc.getScore());
                    docMap.put("source", doc.getSource());
                    docMap.put("metadata", doc.getMetadata());
                    return docMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("documents", documents);
        outputs.put("count", documents.size());
        outputs.put("query", query);

        return NodeExecutionResult.builder()
                .success(true)
                .output(outputs)
                .build();
    }

    /**
     * 執行文檔索引
     */
    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeIndex(NodeExecutionContext context) {
        Map<String, Object> input = context.getInputData();
        String storeName = getStringConfig(context, "storeName", null);

        // 嘗試取得文檔列表
        List<Map<String, Object>> docsInput = (List<Map<String, Object>>) input.get("documents");

        if (docsInput == null || docsInput.isEmpty()) {
            // 如果沒有文檔列表，嘗試單一內容
            String content = (String) input.get("content");
            if (content != null && !content.isBlank()) {
                Map<String, Object> metadata = new HashMap<>();
                String source = (String) input.get("source");
                if (source != null) {
                    metadata.put("source", source);
                }

                List<String> ids = ragService.indexDocument(content, metadata, storeName);

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("indexed", ids.size());
                outputs.put("ids", ids);
                outputs.put("message", "Successfully indexed document into " + ids.size() + " chunks");
                return NodeExecutionResult.builder()
                        .success(true)
                        .output(outputs)
                        .build();
            }
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("No documents to index")
                    .build();
        }

        // 批次索引多個文檔
        List<String> allIds = new ArrayList<>();
        for (Map<String, Object> docMap : docsInput) {
            String content = (String) docMap.get("content");
            if (content != null && !content.isBlank()) {
                Map<String, Object> metadata = new HashMap<>();
                String source = (String) docMap.get("source");
                if (source != null) {
                    metadata.put("source", source);
                }
                List<String> ids = ragService.indexDocument(content, metadata, storeName);
                allIds.addAll(ids);
            }
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("indexed", allIds.size());
        outputs.put("ids", allIds);
        outputs.put("message", "Successfully indexed " + docsInput.size() + " documents into " + allIds.size() + " chunks");

        return NodeExecutionResult.builder()
                .success(true)
                .output(outputs)
                .build();
    }

    /**
     * 清除向量存儲
     */
    private NodeExecutionResult executeClear(NodeExecutionContext context) {
        String storeName = getStringConfig(context, "storeName", null);

        ragService.clearStore(storeName);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("cleared", true);
        outputs.put("storeName", storeName != null ? storeName : "default");
        outputs.put("message", "Successfully cleared vector store: " + (storeName != null ? storeName : "default"));

        return NodeExecutionResult.builder()
                .success(true)
                .output(outputs)
                .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("qa", "search", "index", "clear"),
                                "default", "qa",
                                "description", "RAG 操作類型"
                        ),
                        "storeName", Map.of(
                                "type", "string",
                                "description", "向量存儲名稱（可選）"
                        ),
                        "topK", Map.of(
                                "type", "integer",
                                "default", 5,
                                "description", "返回的最大結果數量"
                        ),
                        "minScore", Map.of(
                                "type", "number",
                                "default", 0.0,
                                "description", "最低相似度分數"
                        )
                )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "question", "type", "string",
                                "description", "問題（用於 QA 操作）"),
                        Map.of("name", "query", "type", "string",
                                "description", "搜尋查詢（用於 search 操作）"),
                        Map.of("name", "content", "type", "string",
                                "description", "文檔內容（用於 index 操作）"),
                        Map.of("name", "documents", "type", "array",
                                "description", "要索引的文檔列表")
                ),
                "outputs", List.of(
                        Map.of("name", "answer", "type", "string",
                                "description", "AI 生成的答案"),
                        Map.of("name", "documents", "type", "array",
                                "description", "搜尋結果文檔"),
                        Map.of("name", "count", "type", "integer",
                                "description", "結果數量"),
                        Map.of("name", "indexed", "type", "integer",
                                "description", "已索引的文檔片段數")
                )
        );
    }
}
