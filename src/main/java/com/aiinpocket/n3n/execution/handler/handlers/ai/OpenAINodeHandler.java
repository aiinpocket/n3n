package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * OpenAI multi-operation node handler.
 *
 * Supports:
 * - Chat completions (GPT-4, GPT-4o, etc.)
 * - Image generation (DALL-E)
 * - Text embeddings
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAINodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String API_BASE_URL = "https://api.openai.com/v1";

    @Override
    public String getType() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI";
    }

    @Override
    public String getDescription() {
        return "Interact with OpenAI's AI models including GPT-4, DALL-E, and text embeddings.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "openai";
    }

    @Override
    public String getCredentialType() {
        return "openai";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("chat", ResourceDef.of("chat", "Chat", "Chat completions with GPT models"));
        resources.put("image", ResourceDef.of("image", "Image", "Image generation with DALL-E"));
        resources.put("embedding", ResourceDef.of("embedding", "Embedding", "Text embeddings for semantic search"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Chat operations
        operations.put("chat", List.of(
            OperationDef.create("createCompletion", "Create Completion")
                .description("Generate a chat completion using GPT models")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo"
                        ))
                        .withDefault("gpt-4o")
                        .withDescription("The model to use for completion")
                        .required(),
                    FieldDef.textarea("prompt", "Prompt")
                        .withDescription("The user message to send")
                        .withPlaceholder("Enter your prompt here...")
                        .required(),
                    FieldDef.textarea("systemPrompt", "System Prompt")
                        .withDescription("Optional system message to set context")
                        .withPlaceholder("You are a helpful assistant..."),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(0.7)
                        .withRange(0.0, 2.0)
                        .withDescription("Sampling temperature (0-2)"),
                    FieldDef.integer("maxTokens", "Max Tokens")
                        .withDefault(1024)
                        .withRange(1, 128000)
                        .withDescription("Maximum tokens in the response")
                ))
                .outputDescription("Returns the generated text in 'content' field")
                .build()
        ));

        // Image operations
        operations.put("image", List.of(
            OperationDef.create("createImage", "Create Image")
                .description("Generate an image using DALL-E")
                .fields(List.of(
                    FieldDef.textarea("prompt", "Prompt")
                        .withDescription("Description of the image to generate")
                        .withPlaceholder("A cute cat sitting on a rainbow...")
                        .required(),
                    FieldDef.select("model", "Model", List.of("dall-e-3", "dall-e-2"))
                        .withDefault("dall-e-3")
                        .withDescription("The DALL-E model to use"),
                    FieldDef.select("size", "Size", List.of(
                            "1024x1024", "1792x1024", "1024x1792", "512x512", "256x256"
                        ))
                        .withDefault("1024x1024")
                        .withDescription("Image size"),
                    FieldDef.select("quality", "Quality", List.of("standard", "hd"))
                        .withDefault("standard")
                        .withDescription("Image quality (DALL-E 3 only)"),
                    FieldDef.integer("n", "Number of Images")
                        .withDefault(1)
                        .withRange(1, 10)
                        .withDescription("Number of images to generate")
                ))
                .outputDescription("Returns image URL(s) in 'images' array")
                .build()
        ));

        // Embedding operations
        operations.put("embedding", List.of(
            OperationDef.create("createEmbedding", "Create Embedding")
                .description("Generate text embeddings for semantic operations")
                .fields(List.of(
                    FieldDef.textarea("input", "Input Text")
                        .withDescription("Text to generate embeddings for")
                        .withPlaceholder("Enter text to embed...")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            "text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002"
                        ))
                        .withDefault("text-embedding-3-small")
                        .withDescription("The embedding model to use"),
                    FieldDef.integer("dimensions", "Dimensions")
                        .withDescription("Output dimensions (for text-embedding-3-* models)")
                ))
                .outputDescription("Returns embedding vector in 'embedding' array")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        String apiKey = getCredentialValue(credential, "apiKey");
        if (apiKey == null || apiKey.isEmpty()) {
            // Fallback to environment variable
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return NodeExecutionResult.failure("OpenAI API key is required");
        }

        try {
            return switch (resource) {
                case "chat" -> switch (operation) {
                    case "createCompletion" -> createChatCompletion(apiKey, params);
                    default -> NodeExecutionResult.failure("Unknown chat operation: " + operation);
                };
                case "image" -> switch (operation) {
                    case "createImage" -> createImage(apiKey, params);
                    default -> NodeExecutionResult.failure("Unknown image operation: " + operation);
                };
                case "embedding" -> switch (operation) {
                    case "createEmbedding" -> createEmbedding(apiKey, params);
                    default -> NodeExecutionResult.failure("Unknown embedding operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("OpenAI API error: {}", e.getMessage());
            return NodeExecutionResult.failure("OpenAI API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult createChatCompletion(String apiKey, Map<String, Object> params) throws IOException {
        String model = getParam(params, "model", "gpt-4o");
        String prompt = getRequiredParam(params, "prompt");
        String systemPrompt = getParam(params, "systemPrompt", "");
        double temperature = getDoubleParam(params, "temperature", 0.7);
        int maxTokens = getIntParam(params, "maxTokens", 1024);

        // Build messages array
        List<Map<String, String>> messages = new ArrayList<>();
        if (!systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(API_BASE_URL + "/chat/completions")
            .post(body)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("OpenAI API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            String content = json.path("choices").get(0).path("message").path("content").asText();

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("content", content);
            output.put("model", json.path("model").asText());
            output.put("usage", Map.of(
                "promptTokens", json.path("usage").path("prompt_tokens").asInt(),
                "completionTokens", json.path("usage").path("completion_tokens").asInt(),
                "totalTokens", json.path("usage").path("total_tokens").asInt()
            ));
            output.put("finishReason", json.path("choices").get(0).path("finish_reason").asText());

            log.info("OpenAI chat completion successful, tokens used: {}",
                json.path("usage").path("total_tokens").asInt());
            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult createImage(String apiKey, Map<String, Object> params) throws IOException {
        String prompt = getRequiredParam(params, "prompt");
        String model = getParam(params, "model", "dall-e-3");
        String size = getParam(params, "size", "1024x1024");
        String quality = getParam(params, "quality", "standard");
        int n = getIntParam(params, "n", 1);

        // DALL-E 3 only supports n=1
        if ("dall-e-3".equals(model)) {
            n = 1;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("n", n);
        payload.put("size", size);
        if ("dall-e-3".equals(model)) {
            payload.put("quality", quality);
        }

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(API_BASE_URL + "/images/generations")
            .post(body)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("OpenAI API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            List<String> images = new ArrayList<>();
            for (JsonNode imageData : json.path("data")) {
                images.add(imageData.path("url").asText());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("images", images);
            output.put("count", images.size());

            log.info("OpenAI image generation successful, {} images created", images.size());
            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult createEmbedding(String apiKey, Map<String, Object> params) throws IOException {
        String input = getRequiredParam(params, "input");
        String model = getParam(params, "model", "text-embedding-3-small");
        Integer dimensions = getIntParam(params, "dimensions", 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", input);
        if (dimensions > 0 && model.startsWith("text-embedding-3")) {
            payload.put("dimensions", dimensions);
        }

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(API_BASE_URL + "/embeddings")
            .post(body)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("OpenAI API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode embeddingNode = json.path("data").get(0).path("embedding");

            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add(value.asDouble());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("embedding", embedding);
            output.put("dimensions", embedding.size());
            output.put("model", json.path("model").asText());
            output.put("usage", Map.of(
                "promptTokens", json.path("usage").path("prompt_tokens").asInt(),
                "totalTokens", json.path("usage").path("total_tokens").asInt()
            ));

            log.info("OpenAI embedding created, dimensions: {}", embedding.size());
            return NodeExecutionResult.success(output);
        }
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
