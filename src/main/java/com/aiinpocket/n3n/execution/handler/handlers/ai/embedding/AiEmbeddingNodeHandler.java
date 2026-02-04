package com.aiinpocket.n3n.execution.handler.handlers.ai.embedding;

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
 * AI Embedding 節點處理器
 *
 * 功能：
 * - 將文本轉換為向量嵌入
 * - 支援多種嵌入模型
 * - 批量處理支援
 */
@Component
@Slf4j
public class AiEmbeddingNodeHandler extends AbstractAiNodeHandler {

    public AiEmbeddingNodeHandler(AiProviderFactory providerFactory) {
        super(providerFactory);
    }

    @Override
    public String getType() {
        return "aiEmbedding";
    }

    @Override
    public String getDisplayName() {
        return "AI Embedding";
    }

    @Override
    public String getDescription() {
        return "Convert text to vector embeddings for semantic search, similarity, and RAG applications.";
    }

    @Override
    public String getIcon() {
        return "code";
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("embedding", ResourceDef.of("embedding", "Embedding", "Text embedding operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("embedding", List.of(
            // 單文本嵌入
            OperationDef.create("embed", "Embed Text")
                .description("Convert text to a vector embedding")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "gemini", "ollama"))
                        .withDefault("openai")
                        .withDescription("Embedding provider")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            // OpenAI
                            "text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002",
                            // Gemini
                            "text-embedding-004",
                            // Ollama
                            "nomic-embed-text", "mxbai-embed-large"
                        ))
                        .withDefault("text-embedding-3-small")
                        .withDescription("Embedding model")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Text to embed")
                        .required()
                ))
                .outputDescription("Returns vector array and dimensions")
                .build(),

            // 批量嵌入
            OperationDef.create("embedBatch", "Embed Batch")
                .description("Convert multiple texts to embeddings")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "gemini", "ollama"))
                        .withDefault("openai")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            "text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002",
                            "text-embedding-004",
                            "nomic-embed-text", "mxbai-embed-large"
                        ))
                        .withDefault("text-embedding-3-small")
                        .required(),
                    FieldDef.json("texts", "Texts")
                        .withDescription("Array of texts to embed")
                        .required()
                ))
                .outputDescription("Returns array of embeddings")
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
        if (!"embedding".equals(resource)) {
            return NodeExecutionResult.failure("Unknown resource: " + resource);
        }

        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");

        try {
            AiProvider provider = resolveProvider(providerId);
            AiProviderSettings settings = buildProviderSettings(credential, providerId);

            return switch (operation) {
                case "embed" -> {
                    String text = getRequiredParam(params, "text");
                    yield executeEmbed(provider, settings, model, text);
                }
                case "embedBatch" -> {
                    Object textsObj = params.get("texts");
                    List<String> texts;
                    if (textsObj instanceof List) {
                        texts = (List<String>) textsObj;
                    } else if (textsObj instanceof String) {
                        texts = List.of((String) textsObj);
                    } else {
                        yield NodeExecutionResult.failure("Invalid texts parameter");
                    }
                    yield executeEmbedBatch(provider, settings, model, texts);
                }
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };

        } catch (Exception e) {
            log.error("Embedding error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Embedding error: " + e.getMessage());
        }
    }

    private NodeExecutionResult executeEmbed(
        AiProvider provider,
        AiProviderSettings settings,
        String model,
        String text
    ) throws Exception {

        AiEmbeddingRequest request = AiEmbeddingRequest.builder()
            .model(model)
            .input(text)
            .build();

        AiEmbeddingResponse response = provider.embed(request, settings).get();

        List<Float> embedding = response.getFirstEmbedding();
        if (embedding == null || embedding.isEmpty()) {
            return NodeExecutionResult.failure("No embedding returned");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("embedding", embedding);
        output.put("dimensions", embedding.size());
        output.put("model", model);

        if (response.getUsage() != null) {
            output.put("usage", Map.of(
                "tokens", response.getUsage().getTotalTokens()
            ));
        }

        log.info("Embedding generated - model: {}, dimensions: {}", model, embedding.size());

        return NodeExecutionResult.success(output);
    }

    private NodeExecutionResult executeEmbedBatch(
        AiProvider provider,
        AiProviderSettings settings,
        String model,
        List<String> texts
    ) throws Exception {

        List<List<Float>> embeddings = new ArrayList<>();
        int totalTokens = 0;

        // 批量處理（部分 API 支援批量，這裡做通用實作）
        for (String text : texts) {
            AiEmbeddingRequest request = AiEmbeddingRequest.builder()
                .model(model)
                .input(text)
                .build();

            AiEmbeddingResponse response = provider.embed(request, settings).get();

            List<Float> embedding = response.getFirstEmbedding();
            if (embedding != null && !embedding.isEmpty()) {
                embeddings.add(embedding);
            }

            if (response.getUsage() != null) {
                totalTokens += response.getUsage().getTotalTokens();
            }
        }

        int dimensions = embeddings.isEmpty() ? 0 : embeddings.get(0).size();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("embeddings", embeddings);
        output.put("count", embeddings.size());
        output.put("dimensions", dimensions);
        output.put("model", model);
        output.put("usage", Map.of("totalTokens", totalTokens));

        log.info("Batch embedding generated - model: {}, count: {}, dimensions: {}",
            model, embeddings.size(), dimensions);

        return NodeExecutionResult.success(output);
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        return Flux.just(StreamChunk.error("Embedding node does not support streaming"));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "text", "type", "string", "required", true,
                       "description", "Text to embed"),
                Map.of("name", "texts", "type", "array", "required", false,
                       "description", "Multiple texts for batch embedding")
            ),
            "outputs", List.of(
                Map.of("name", "embedding", "type", "array",
                       "description", "Vector embedding"),
                Map.of("name", "embeddings", "type", "array",
                       "description", "Multiple embeddings for batch"),
                Map.of("name", "dimensions", "type", "number",
                       "description", "Embedding dimensions")
            )
        );
    }
}
