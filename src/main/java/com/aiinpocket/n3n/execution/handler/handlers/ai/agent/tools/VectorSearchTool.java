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
 * Vector semantic search tool
 *
 * Allows AI Agent to perform semantic search in the knowledge base.
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
        return "Search for documents semantically related to the query in the knowledge base. " +
               "Can be used to find relevant information or answer document-based questions. " +
               "Parameters: query (query text), top_k (number of results, default 5), store_name (knowledge base name, optional)";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "Query text to search for"
                ),
                "top_k", Map.of(
                    "type", "integer",
                    "description", "Number of results to return",
                    "default", 5
                ),
                "store_name", Map.of(
                    "type", "string",
                    "description", "Knowledge base name (optional, uses default if not specified)"
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
                    return ToolResult.failure("Query text cannot be empty");
                }

                int topK = parameters.containsKey("top_k")
                        ? ((Number) parameters.get("top_k")).intValue()
                        : 5;

                // Security limit: prevent resource exhaustion attacks
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
                    return ToolResult.success("No documents found matching the query.");
                }

                // Format results
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Found %d relevant documents:\n\n", results.size()));

                for (int i = 0; i < results.size(); i++) {
                    Document doc = results.get(i);
                    sb.append(String.format("--- Document %d (similarity: %.2f) ---\n",
                            i + 1, doc.getScore() != null ? doc.getScore() : 0));

                    if (doc.getSource() != null) {
                        sb.append("Source: ").append(doc.getSource()).append("\n");
                    }

                    String content = doc.getContent();
                    if (content.length() > 500) {
                        content = content.substring(0, 500) + "...";
                    }
                    sb.append(content).append("\n\n");
                }

                // Return results and structured data
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
                return ToolResult.failure("Vector search failed");
            }
        });
    }

    @Override
    public String getCategory() {
        return "search";
    }
}
