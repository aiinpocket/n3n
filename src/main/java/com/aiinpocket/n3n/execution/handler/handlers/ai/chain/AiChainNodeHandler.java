package com.aiinpocket.n3n.execution.handler.handlers.ai.chain;

import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.AbstractAiNodeHandler;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.StreamChunk;
import com.aiinpocket.n3n.execution.handler.handlers.ai.vector.VectorStore;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI Chain 節點處理器
 *
 * 功能：
 * - RAG（檢索增強生成）處理鏈
 * - Map-Reduce 文檔處理
 * - 摘要生成鏈
 * - 自定義處理流程
 *
 * 類似 LangChain 的鏈式處理概念
 */
@Component
@Slf4j
public class AiChainNodeHandler extends AbstractAiNodeHandler {

    private final VectorStore vectorStore;

    public AiChainNodeHandler(
        AiProviderFactory providerFactory,
        VectorStore vectorStore
    ) {
        super(providerFactory);
        this.vectorStore = vectorStore;
    }

    @Override
    public String getType() {
        return "aiChain";
    }

    @Override
    public String getDisplayName() {
        return "AI Chain";
    }

    @Override
    public String getDescription() {
        return "Build AI processing chains like RAG, Map-Reduce, and summarization pipelines.";
    }

    @Override
    public String getIcon() {
        return "link";
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("chain", ResourceDef.of("chain", "Chain", "AI processing chains"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("chain", List.of(
            // RAG Chain
            OperationDef.create("rag", "RAG Chain")
                .description("Retrieval-Augmented Generation: search relevant context then generate response")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "claude", "gemini"))
                        .withDefault("openai")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo",
                            "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307",
                            "gemini-1.5-pro", "gemini-1.5-flash"
                        ))
                        .withDefault("gpt-4o")
                        .required(),
                    FieldDef.string("namespace", "Vector Namespace")
                        .withDefault("default")
                        .withDescription("Namespace to search in"),
                    FieldDef.textarea("query", "Query")
                        .withDescription("User's question")
                        .required(),
                    FieldDef.integer("topK", "Context Count")
                        .withDefault(3)
                        .withRange(1, 10)
                        .withDescription("Number of context documents to retrieve"),
                    FieldDef.select("embeddingModel", "Embedding Model", List.of(
                            "text-embedding-3-small", "text-embedding-3-large"
                        ))
                        .withDefault("text-embedding-3-small"),
                    FieldDef.textarea("systemPrompt", "System Prompt")
                        .withDescription("Optional system instructions"),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(0.3)
                        .withRange(0.0, 1.0)
                ))
                .outputDescription("Returns AI response with retrieved context sources")
                .build(),

            // Map-Reduce Chain
            OperationDef.create("mapReduce", "Map-Reduce Chain")
                .description("Process documents in parallel (map), then combine results (reduce)")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "claude", "gemini"))
                        .withDefault("openai")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            "gpt-4o", "gpt-4o-mini",
                            "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307",
                            "gemini-1.5-pro", "gemini-1.5-flash"
                        ))
                        .withDefault("gpt-4o-mini")
                        .required(),
                    FieldDef.json("documents", "Documents")
                        .withDescription("Array of text documents to process")
                        .required(),
                    FieldDef.textarea("mapPrompt", "Map Prompt")
                        .withDescription("Prompt for processing each document")
                        .withPlaceholder("Summarize the following document:\\n\\n{document}")
                        .required(),
                    FieldDef.textarea("reducePrompt", "Reduce Prompt")
                        .withDescription("Prompt for combining results")
                        .withPlaceholder("Combine these summaries into a final summary:\\n\\n{summaries}")
                        .required(),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(0.3)
                ))
                .outputDescription("Returns combined result from all documents")
                .build(),

            // Summarize Chain
            OperationDef.create("summarize", "Summarize Chain")
                .description("Summarize long text using chunking strategy")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "claude", "gemini"))
                        .withDefault("openai")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            "gpt-4o", "gpt-4o-mini",
                            "claude-3-5-sonnet-20241022",
                            "gemini-1.5-pro"
                        ))
                        .withDefault("gpt-4o-mini")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Long text to summarize")
                        .required(),
                    FieldDef.integer("chunkSize", "Chunk Size")
                        .withDefault(4000)
                        .withRange(500, 16000)
                        .withDescription("Characters per chunk"),
                    FieldDef.select("style", "Summary Style", List.of(
                            "concise", "detailed", "bullet-points", "executive"
                        ))
                        .withDefault("concise"),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(0.3)
                ))
                .outputDescription("Returns summary of the text")
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
        if (!"chain".equals(resource)) {
            return NodeExecutionResult.failure("Unknown resource: " + resource);
        }

        try {
            return switch (operation) {
                case "rag" -> executeRagChain(params, credential);
                case "mapReduce" -> executeMapReduceChain(params, credential);
                case "summarize" -> executeSummarizeChain(params, credential);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            log.error("Chain execution failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Chain execution failed: " + e.getMessage());
        }
    }

    private NodeExecutionResult executeRagChain(
        Map<String, Object> params,
        Map<String, Object> credential
    ) throws Exception {
        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");
        String namespace = getParam(params, "namespace", "default");
        String query = getRequiredParam(params, "query");
        int topK = getIntParam(params, "topK", 3);
        String embeddingModel = getParam(params, "embeddingModel", "text-embedding-3-small");
        String systemPrompt = getParam(params, "systemPrompt", "");
        double temperature = getDoubleParam(params, "temperature", 0.3);

        AiProvider provider = resolveProvider(providerId);
        AiProviderSettings settings = buildProviderSettings(credential, providerId);

        // Step 1: 生成查詢嵌入
        AiEmbeddingRequest embeddingRequest = AiEmbeddingRequest.builder()
            .model(embeddingModel)
            .input(query)
            .build();

        AiEmbeddingResponse embeddingResponse = provider.embed(embeddingRequest, settings).get();
        List<Float> queryVector = embeddingResponse.getEmbeddings().get(0);

        // Step 2: 檢索相關文檔
        List<VectorStore.SearchResult> searchResults =
            vectorStore.search(namespace, queryVector, topK, Map.of()).get();

        // Step 3: 建構 RAG prompt
        StringBuilder contextBuilder = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();

        for (int i = 0; i < searchResults.size(); i++) {
            VectorStore.SearchResult result = searchResults.get(i);
            contextBuilder.append("Document ").append(i + 1).append(":\n");
            contextBuilder.append(result.content()).append("\n\n");

            sources.add(Map.of(
                "id", result.id(),
                "score", result.score(),
                "preview", truncate(result.content(), 200)
            ));
        }

        String ragPrompt = String.format("""
            Based on the following context, answer the user's question.
            If the context doesn't contain relevant information, say so.

            Context:
            %s

            Question: %s
            """, contextBuilder.toString(), query);

        // Step 4: 生成回應
        String finalSystemPrompt = systemPrompt.isEmpty()
            ? "You are a helpful assistant that answers questions based on provided context."
            : systemPrompt;

        AiChatRequest chatRequest = AiChatRequest.builder()
            .model(model)
            .messages(List.of(AiMessage.user(ragPrompt)))
            .systemPrompt(finalSystemPrompt)
            .temperature(temperature)
            .build();

        AiResponse response = provider.chat(chatRequest, settings).get();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("response", response.getContent());
        output.put("sources", sources);
        output.put("sourceCount", sources.size());
        output.put("query", query);

        if (response.getUsage() != null) {
            output.put("usage", Map.of(
                "inputTokens", response.getUsage().getInputTokens(),
                "outputTokens", response.getUsage().getOutputTokens()
            ));
        }

        log.info("RAG chain completed - {} sources retrieved", sources.size());

        return NodeExecutionResult.success(output);
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeMapReduceChain(
        Map<String, Object> params,
        Map<String, Object> credential
    ) throws Exception {
        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");
        List<String> documents = (List<String>) params.get("documents");
        String mapPrompt = getRequiredParam(params, "mapPrompt");
        String reducePrompt = getRequiredParam(params, "reducePrompt");
        double temperature = getDoubleParam(params, "temperature", 0.3);

        if (documents == null || documents.isEmpty()) {
            return NodeExecutionResult.failure("No documents provided");
        }

        AiProvider provider = resolveProvider(providerId);
        AiProviderSettings settings = buildProviderSettings(credential, providerId);

        // Map phase: 並行處理每個文檔
        List<CompletableFuture<String>> mapFutures = new ArrayList<>();

        for (String document : documents) {
            String prompt = mapPrompt.replace("{document}", document);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    AiChatRequest request = AiChatRequest.builder()
                        .model(model)
                        .messages(List.of(AiMessage.user(prompt)))
                        .temperature(temperature)
                        .build();

                    AiResponse response = provider.chat(request, settings).get();
                    return response.getContent();
                } catch (Exception e) {
                    log.error("Map phase error: {}", e.getMessage());
                    return "Error processing document: " + e.getMessage();
                }
            });

            mapFutures.add(future);
        }

        // 等待所有 map 任務完成
        List<String> mapResults = new ArrayList<>();
        for (CompletableFuture<String> future : mapFutures) {
            mapResults.add(future.get());
        }

        // Reduce phase: 合併結果
        String summaries = mapResults.stream()
            .map(s -> "- " + s)
            .collect(Collectors.joining("\n\n"));

        String finalPrompt = reducePrompt.replace("{summaries}", summaries);

        AiChatRequest reduceRequest = AiChatRequest.builder()
            .model(model)
            .messages(List.of(AiMessage.user(finalPrompt)))
            .temperature(temperature)
            .build();

        AiResponse reduceResponse = provider.chat(reduceRequest, settings).get();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", reduceResponse.getContent());
        output.put("mapResults", mapResults);
        output.put("documentCount", documents.size());

        log.info("Map-Reduce chain completed - {} documents processed", documents.size());

        return NodeExecutionResult.success(output);
    }

    private NodeExecutionResult executeSummarizeChain(
        Map<String, Object> params,
        Map<String, Object> credential
    ) throws Exception {
        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");
        String text = getRequiredParam(params, "text");
        int chunkSize = getIntParam(params, "chunkSize", 4000);
        String style = getParam(params, "style", "concise");
        double temperature = getDoubleParam(params, "temperature", 0.3);

        AiProvider provider = resolveProvider(providerId);
        AiProviderSettings settings = buildProviderSettings(credential, providerId);

        // 分割文本為 chunks
        List<String> chunks = splitIntoChunks(text, chunkSize);

        if (chunks.size() == 1) {
            // 單一 chunk，直接摘要
            String summary = summarizeText(provider, settings, model, text, style, temperature);
            return NodeExecutionResult.success(Map.of(
                "summary", summary,
                "chunkCount", 1,
                "originalLength", text.length()
            ));
        }

        // 多個 chunks，使用 map-reduce 策略
        List<String> chunkSummaries = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            log.debug("Summarizing chunk {}/{}", i + 1, chunks.size());
            String chunkSummary = summarizeText(provider, settings, model, chunks.get(i), style, temperature);
            chunkSummaries.add(chunkSummary);
        }

        // 合併摘要
        String combinedSummaries = String.join("\n\n", chunkSummaries);
        String finalSummary = summarizeText(provider, settings, model, combinedSummaries, style, temperature);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summary", finalSummary);
        output.put("chunkCount", chunks.size());
        output.put("originalLength", text.length());
        output.put("chunkSummaries", chunkSummaries);

        log.info("Summarize chain completed - {} chunks processed", chunks.size());

        return NodeExecutionResult.success(output);
    }

    private String summarizeText(
        AiProvider provider,
        AiProviderSettings settings,
        String model,
        String text,
        String style,
        double temperature
    ) throws Exception {
        String styleInstruction = switch (style) {
            case "detailed" -> "Provide a detailed summary covering all main points.";
            case "bullet-points" -> "Summarize as bullet points.";
            case "executive" -> "Write an executive summary focusing on key insights and decisions.";
            default -> "Write a concise summary.";
        };

        String prompt = String.format("""
            %s

            Text to summarize:
            %s
            """, styleInstruction, text);

        AiChatRequest request = AiChatRequest.builder()
            .model(model)
            .messages(List.of(AiMessage.user(prompt)))
            .temperature(temperature)
            .build();

        AiResponse response = provider.chat(request, settings).get();
        return response.getContent();
    }

    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // 嘗試在句子邊界切割
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf(". ", end);
                int lastNewline = text.lastIndexOf("\n", end);
                int boundary = Math.max(lastPeriod, lastNewline);

                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end;
        }

        return chunks;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        Sinks.Many<StreamChunk> sink = Sinks.many().multicast().onBackpressureBuffer();

        CompletableFuture.runAsync(() -> {
            try {
                String operation = getStringConfig(context, "operation", "rag");

                sink.tryEmitNext(StreamChunk.progress(0, "Starting " + operation + " chain..."));

                // 簡化版串流實作：執行後返回結果
                Map<String, Object> credential = resolveCredential(context);
                Map<String, Object> params = context.getNodeConfig();

                NodeExecutionResult result = executeOperation(context, "chain", operation, credential, params);

                if (result.isSuccess()) {
                    String response = (String) result.getOutput().getOrDefault("response",
                        result.getOutput().getOrDefault("result",
                            result.getOutput().getOrDefault("summary", "")));

                    sink.tryEmitNext(StreamChunk.text(response));
                    sink.tryEmitNext(StreamChunk.done(result.getOutput()));
                } else {
                    sink.tryEmitNext(StreamChunk.error(result.getErrorMessage()));
                }

            } catch (Exception e) {
                log.error("Chain stream error: {}", e.getMessage());
                sink.tryEmitNext(StreamChunk.error(e.getMessage()));
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "query", "type", "string", "required", false,
                       "description", "Query for RAG chain"),
                Map.of("name", "documents", "type", "array", "required", false,
                       "description", "Documents for Map-Reduce"),
                Map.of("name", "text", "type", "string", "required", false,
                       "description", "Text for summarization")
            ),
            "outputs", List.of(
                Map.of("name", "response", "type", "string",
                       "description", "Generated response"),
                Map.of("name", "result", "type", "string",
                       "description", "Processing result"),
                Map.of("name", "summary", "type", "string",
                       "description", "Generated summary"),
                Map.of("name", "sources", "type", "array",
                       "description", "Retrieved sources for RAG")
            )
        );
    }
}
