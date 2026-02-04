package com.aiinpocket.n3n.execution.handler.handlers.ai.vector;

import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.AbstractAiNodeHandler;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.StreamChunk;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * AI Vector Search 節點處理器
 *
 * 功能：
 * - 向量存儲和檢索
 * - 語意相似度搜尋
 * - RAG（檢索增強生成）支援
 */
@Component
@Slf4j
public class AiVectorSearchNodeHandler extends AbstractAiNodeHandler {

    private final VectorStore vectorStore;

    public AiVectorSearchNodeHandler(
        AiProviderFactory providerFactory,
        VectorStore vectorStore
    ) {
        super(providerFactory);
        this.vectorStore = vectorStore;
    }

    @Override
    public String getType() {
        return "aiVectorSearch";
    }

    @Override
    public String getDisplayName() {
        return "Vector Search";
    }

    @Override
    public String getDescription() {
        return "Store and search vector embeddings. Use for semantic search, RAG, and similarity matching.";
    }

    @Override
    public String getIcon() {
        return "search";
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("vector", ResourceDef.of("vector", "Vector Store", "Vector storage operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("vector", List.of(
            // 插入向量
            OperationDef.create("upsert", "Upsert Document")
                .description("Insert or update a document with its embedding")
                .fields(List.of(
                    FieldDef.string("namespace", "Namespace")
                        .withDescription("Collection/namespace name")
                        .withDefault("default")
                        .required(),
                    FieldDef.string("id", "Document ID")
                        .withDescription("Unique document identifier")
                        .required(),
                    FieldDef.json("vector", "Vector")
                        .withDescription("Embedding vector (array of floats)")
                        .required(),
                    FieldDef.textarea("content", "Content")
                        .withDescription("Original text content"),
                    FieldDef.json("metadata", "Metadata")
                        .withDescription("Additional metadata (JSON object)")
                ))
                .outputDescription("Confirmation of upsert")
                .build(),

            // 語意搜尋（帶自動嵌入）
            OperationDef.create("search", "Semantic Search")
                .description("Search for similar documents by text query (auto-embeds)")
                .fields(List.of(
                    FieldDef.string("namespace", "Namespace")
                        .withDefault("default")
                        .required(),
                    FieldDef.textarea("query", "Query")
                        .withDescription("Text query to search")
                        .required(),
                    FieldDef.select("embeddingProvider", "Embedding Provider",
                            List.of("openai", "gemini", "ollama"))
                        .withDefault("openai"),
                    FieldDef.select("embeddingModel", "Embedding Model", List.of(
                            "text-embedding-3-small", "text-embedding-3-large",
                            "text-embedding-004", "nomic-embed-text"
                        ))
                        .withDefault("text-embedding-3-small"),
                    FieldDef.integer("topK", "Top K")
                        .withDefault(5)
                        .withRange(1, 100)
                        .withDescription("Number of results to return"),
                    FieldDef.json("filter", "Filter")
                        .withDescription("Metadata filter (JSON object)")
                ))
                .outputDescription("Returns matching documents with scores")
                .build(),

            // 向量搜尋（直接用向量）
            OperationDef.create("searchByVector", "Search by Vector")
                .description("Search using a pre-computed vector")
                .fields(List.of(
                    FieldDef.string("namespace", "Namespace")
                        .withDefault("default")
                        .required(),
                    FieldDef.json("vector", "Query Vector")
                        .withDescription("Embedding vector to search with")
                        .required(),
                    FieldDef.integer("topK", "Top K")
                        .withDefault(5)
                        .withRange(1, 100),
                    FieldDef.json("filter", "Filter")
                ))
                .outputDescription("Returns matching documents with scores")
                .build(),

            // 刪除
            OperationDef.create("delete", "Delete Document")
                .description("Delete a document by ID")
                .fields(List.of(
                    FieldDef.string("namespace", "Namespace")
                        .withDefault("default")
                        .required(),
                    FieldDef.string("id", "Document ID")
                        .required()
                ))
                .outputDescription("Confirmation of deletion")
                .build(),

            // 統計
            OperationDef.create("count", "Count Documents")
                .description("Count documents in namespace")
                .fields(List.of(
                    FieldDef.string("namespace", "Namespace")
                        .withDefault("default")
                        .required()
                ))
                .outputDescription("Returns document count")
                .build()
        ));

        return operations;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        if (!"vector".equals(resource)) {
            return NodeExecutionResult.failure("Unknown resource: " + resource);
        }

        try {
            return switch (operation) {
                case "upsert" -> executeUpsert(params);
                case "search" -> executeSearch(params, credential);
                case "searchByVector" -> executeSearchByVector(params);
                case "delete" -> executeDelete(params);
                case "count" -> executeCount(params);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            log.error("Vector operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Vector operation failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeUpsert(Map<String, Object> params) throws Exception {
        String namespace = getParam(params, "namespace", "default");
        String id = getRequiredParam(params, "id");
        Object vectorObj = params.get("vector");
        String content = getParam(params, "content", "");
        Map<String, Object> metadata = (Map<String, Object>) params.getOrDefault("metadata", Map.of());

        List<Float> vector;
        if (vectorObj instanceof List) {
            vector = ((List<?>) vectorObj).stream()
                .map(v -> ((Number) v).floatValue())
                .toList();
        } else {
            return NodeExecutionResult.failure("Invalid vector format");
        }

        VectorStore.VectorDocument doc = new VectorStore.VectorDocument(id, vector, content, metadata);
        vectorStore.upsert(namespace, doc).get();

        return NodeExecutionResult.success(Map.of(
            "upserted", true,
            "id", id,
            "namespace", namespace,
            "dimensions", vector.size()
        ));
    }

    private NodeExecutionResult executeSearch(
        Map<String, Object> params,
        Map<String, Object> credential
    ) throws Exception {
        String namespace = getParam(params, "namespace", "default");
        String query = getRequiredParam(params, "query");
        String embeddingProvider = getParam(params, "embeddingProvider", "openai");
        String embeddingModel = getParam(params, "embeddingModel", "text-embedding-3-small");
        int topK = getIntParam(params, "topK", 5);
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) params.getOrDefault("filter", Map.of());

        // 生成查詢向量
        AiProvider provider = resolveProvider(embeddingProvider);
        AiProviderSettings settings = buildProviderSettings(credential, embeddingProvider);

        AiEmbeddingRequest embeddingRequest = AiEmbeddingRequest.builder()
            .model(embeddingModel)
            .input(query)
            .build();

        AiEmbeddingResponse embeddingResponse = provider.embed(embeddingRequest, settings).get();

        if (embeddingResponse.getEmbeddings() == null || embeddingResponse.getEmbeddings().isEmpty()) {
            return NodeExecutionResult.failure("Failed to generate query embedding");
        }

        List<Float> queryVector = embeddingResponse.getEmbeddings().get(0);

        // 執行搜尋
        List<VectorStore.SearchResult> results = vectorStore.search(namespace, queryVector, topK, filter).get();

        List<Map<String, Object>> resultMaps = results.stream()
            .map(r -> Map.<String, Object>of(
                "id", r.id(),
                "score", r.score(),
                "content", r.content(),
                "metadata", r.metadata()
            ))
            .toList();

        return NodeExecutionResult.success(Map.of(
            "results", resultMaps,
            "count", results.size(),
            "query", query,
            "namespace", namespace
        ));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeSearchByVector(Map<String, Object> params) throws Exception {
        String namespace = getParam(params, "namespace", "default");
        Object vectorObj = params.get("vector");
        int topK = getIntParam(params, "topK", 5);
        Map<String, Object> filter = (Map<String, Object>) params.getOrDefault("filter", Map.of());

        List<Float> queryVector;
        if (vectorObj instanceof List) {
            queryVector = ((List<?>) vectorObj).stream()
                .map(v -> ((Number) v).floatValue())
                .toList();
        } else {
            return NodeExecutionResult.failure("Invalid vector format");
        }

        List<VectorStore.SearchResult> results = vectorStore.search(namespace, queryVector, topK, filter).get();

        List<Map<String, Object>> resultMaps = results.stream()
            .map(r -> Map.<String, Object>of(
                "id", r.id(),
                "score", r.score(),
                "content", r.content(),
                "metadata", r.metadata()
            ))
            .toList();

        return NodeExecutionResult.success(Map.of(
            "results", resultMaps,
            "count", results.size(),
            "namespace", namespace
        ));
    }

    private NodeExecutionResult executeDelete(Map<String, Object> params) throws Exception {
        String namespace = getParam(params, "namespace", "default");
        String id = getRequiredParam(params, "id");

        vectorStore.delete(namespace, id).get();

        return NodeExecutionResult.success(Map.of(
            "deleted", true,
            "id", id,
            "namespace", namespace
        ));
    }

    private NodeExecutionResult executeCount(Map<String, Object> params) throws Exception {
        String namespace = getParam(params, "namespace", "default");

        long count = vectorStore.count(namespace).get();

        return NodeExecutionResult.success(Map.of(
            "count", count,
            "namespace", namespace
        ));
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        return Flux.just(StreamChunk.error("Vector search node does not support streaming"));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "query", "type", "string", "required", false,
                       "description", "Text query for semantic search"),
                Map.of("name", "vector", "type", "array", "required", false,
                       "description", "Vector for direct search")
            ),
            "outputs", List.of(
                Map.of("name", "results", "type", "array",
                       "description", "Matching documents with scores"),
                Map.of("name", "count", "type", "number",
                       "description", "Number of results")
            )
        );
    }
}
