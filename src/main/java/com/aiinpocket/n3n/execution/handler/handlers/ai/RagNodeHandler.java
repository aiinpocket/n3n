package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.rag.RagService;
import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeCategory;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG Node Handler
 *
 * Handles RAG-related node operations including Q&A, search, and indexing.
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
        return "Retrieval-Augmented Generation (RAG) document Q&A system";
    }

    @Override
    public String getCategory() {
        return NodeCategory.AI;
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
                    .errorMessage("RAG operation failed")
                    .build();
        }
    }

    /**
     * Execute RAG Q&A
     */
    private NodeExecutionResult executeRagQA(NodeExecutionContext context) {
        Map<String, Object> input = context.getInputData();

        Object questionObj = input.get("question");
        String question = questionObj != null ? questionObj.toString() : null;
        if (question == null) {
            Object inputObj = input.get("input");
            question = inputObj != null ? inputObj.toString() : null;
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
     * Execute vector search
     */
    private NodeExecutionResult executeVectorSearch(NodeExecutionContext context) {
        Map<String, Object> input = context.getInputData();

        Object queryObj = input.get("query");
        String query = queryObj != null ? queryObj.toString() : null;
        if (query == null) {
            Object inputObj = input.get("input");
            query = inputObj != null ? inputObj.toString() : null;
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
     * Execute document indexing
     */
    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeIndex(NodeExecutionContext context) {
        Map<String, Object> input = context.getInputData();
        String storeName = getStringConfig(context, "storeName", null);

        // Try to get document list
        Object docsObj = input.get("documents");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> docsInput = docsObj instanceof List<?> ? (List<Map<String, Object>>) docsObj : null;

        if (docsInput == null || docsInput.isEmpty()) {
            // If no document list, try single content
            Object contentObj = input.get("content");
            String content = contentObj != null ? contentObj.toString() : null;
            if (content != null && !content.isBlank()) {
                Map<String, Object> metadata = new HashMap<>();
                Object sourceObj = input.get("source");
                String source = sourceObj != null ? sourceObj.toString() : null;
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

        // Batch index multiple documents
        List<String> allIds = new ArrayList<>();
        for (Map<String, Object> docMap : docsInput) {
            Object contentObj2 = docMap.get("content");
            String content = contentObj2 != null ? contentObj2.toString() : null;
            if (content != null && !content.isBlank()) {
                Map<String, Object> metadata = new HashMap<>();
                Object sourceObj2 = docMap.get("source");
                String source = sourceObj2 != null ? sourceObj2.toString() : null;
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
     * Clear vector store
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
                                "description", "RAG operation type"
                        ),
                        "storeName", Map.of(
                                "type", "string",
                                "description", "Vector store name (optional)"
                        ),
                        "topK", Map.of(
                                "type", "integer",
                                "default", 5,
                                "description", "Maximum number of results to return"
                        ),
                        "minScore", Map.of(
                                "type", "number",
                                "default", 0.0,
                                "description", "Minimum similarity score"
                        )
                )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "question", "type", "string",
                                "description", "Question (for QA operation)"),
                        Map.of("name", "query", "type", "string",
                                "description", "Search query (for search operation)"),
                        Map.of("name", "content", "type", "string",
                                "description", "Document content (for index operation)"),
                        Map.of("name", "documents", "type", "array",
                                "description", "List of documents to index")
                ),
                "outputs", List.of(
                        Map.of("name", "answer", "type", "string",
                                "description", "AI generated answer"),
                        Map.of("name", "documents", "type", "array",
                                "description", "Search result documents"),
                        Map.of("name", "count", "type", "integer",
                                "description", "Number of results"),
                        Map.of("name", "indexed", "type", "integer",
                                "description", "Number of indexed document chunks")
                )
        );
    }
}
