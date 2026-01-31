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
 * Google Gemini multi-operation node handler.
 *
 * Supports:
 * - Content generation with Gemini models
 * - Embedding generation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    @Override
    public String getType() {
        return "gemini";
    }

    @Override
    public String getDisplayName() {
        return "Google Gemini";
    }

    @Override
    public String getDescription() {
        return "Interact with Google's Gemini AI models for text and multimodal generation.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "gemini";
    }

    @Override
    public String getCredentialType() {
        return "google";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("models", ResourceDef.of("models", "Models", "Generate content with Gemini models"));
        resources.put("embedding", ResourceDef.of("embedding", "Embedding", "Generate text embeddings"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Model operations
        operations.put("models", List.of(
            OperationDef.create("generateContent", "Generate Content")
                .description("Generate text content using Gemini")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "gemini-2.0-flash-exp",
                            "gemini-1.5-pro",
                            "gemini-1.5-flash",
                            "gemini-1.5-flash-8b",
                            "gemini-1.0-pro"
                        ))
                        .withDefault("gemini-2.0-flash-exp")
                        .withDescription("The Gemini model to use")
                        .required(),
                    FieldDef.textarea("prompt", "Prompt")
                        .withDescription("The prompt to send")
                        .withPlaceholder("Enter your prompt here...")
                        .required(),
                    FieldDef.textarea("systemInstruction", "System Instruction")
                        .withDescription("Optional system instruction")
                        .withPlaceholder("You are a helpful assistant..."),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(1.0)
                        .withRange(0.0, 2.0)
                        .withDescription("Sampling temperature (0-2)"),
                    FieldDef.integer("maxOutputTokens", "Max Output Tokens")
                        .withDefault(2048)
                        .withRange(1, 8192)
                        .withDescription("Maximum tokens in the response"),
                    FieldDef.number("topP", "Top P")
                        .withDefault(0.95)
                        .withRange(0.0, 1.0)
                        .withDescription("Nucleus sampling parameter"),
                    FieldDef.integer("topK", "Top K")
                        .withDefault(40)
                        .withRange(1, 100)
                        .withDescription("Top-k sampling parameter")
                ))
                .outputDescription("Returns generated text in 'content' field")
                .build(),

            OperationDef.create("chat", "Chat")
                .description("Multi-turn conversation with Gemini")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "gemini-2.0-flash-exp",
                            "gemini-1.5-pro",
                            "gemini-1.5-flash"
                        ))
                        .withDefault("gemini-2.0-flash-exp")
                        .withDescription("The Gemini model to use")
                        .required(),
                    FieldDef.textarea("message", "Message")
                        .withDescription("Your message")
                        .withPlaceholder("Enter your message...")
                        .required(),
                    FieldDef.textarea("history", "Conversation History")
                        .withDescription("Previous conversation as JSON array (optional)")
                        .withPlaceholder("[{\"role\": \"user\", \"content\": \"...\"}, ...]"),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(1.0)
                        .withRange(0.0, 2.0)
                        .withDescription("Sampling temperature"),
                    FieldDef.integer("maxOutputTokens", "Max Output Tokens")
                        .withDefault(2048)
                        .withRange(1, 8192)
                        .withDescription("Maximum tokens in the response")
                ))
                .outputDescription("Returns response in 'content' field with updated 'history'")
                .build()
        ));

        // Embedding operations
        operations.put("embedding", List.of(
            OperationDef.create("embedContent", "Embed Content")
                .description("Generate embeddings for text")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "text-embedding-004",
                            "embedding-001"
                        ))
                        .withDefault("text-embedding-004")
                        .withDescription("The embedding model to use")
                        .required(),
                    FieldDef.textarea("content", "Content")
                        .withDescription("Text to embed")
                        .withPlaceholder("Enter text to embed...")
                        .required(),
                    FieldDef.select("taskType", "Task Type", List.of(
                            "RETRIEVAL_QUERY",
                            "RETRIEVAL_DOCUMENT",
                            "SEMANTIC_SIMILARITY",
                            "CLASSIFICATION",
                            "CLUSTERING"
                        ))
                        .withDefault("RETRIEVAL_DOCUMENT")
                        .withDescription("The task type for the embedding")
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
            apiKey = System.getenv("GOOGLE_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return NodeExecutionResult.failure("Google API key is required");
        }

        try {
            return switch (resource) {
                case "models" -> switch (operation) {
                    case "generateContent" -> generateContent(apiKey, params);
                    case "chat" -> chat(apiKey, params);
                    default -> NodeExecutionResult.failure("Unknown models operation: " + operation);
                };
                case "embedding" -> switch (operation) {
                    case "embedContent" -> embedContent(apiKey, params);
                    default -> NodeExecutionResult.failure("Unknown embedding operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("Gemini API error: {}", e.getMessage());
            return NodeExecutionResult.failure("Gemini API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult generateContent(String apiKey, Map<String, Object> params) throws IOException {
        String model = getParam(params, "model", "gemini-2.0-flash-exp");
        String prompt = getRequiredParam(params, "prompt");
        String systemInstruction = getParam(params, "systemInstruction", "");
        double temperature = getDoubleParam(params, "temperature", 1.0);
        int maxOutputTokens = getIntParam(params, "maxOutputTokens", 2048);
        double topP = getDoubleParam(params, "topP", 0.95);
        int topK = getIntParam(params, "topK", 40);

        Map<String, Object> payload = new LinkedHashMap<>();

        // Contents
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", prompt))
        ));
        payload.put("contents", contents);

        // System instruction
        if (!systemInstruction.isEmpty()) {
            payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemInstruction))
            ));
        }

        // Generation config
        payload.put("generationConfig", Map.of(
            "temperature", temperature,
            "maxOutputTokens", maxOutputTokens,
            "topP", topP,
            "topK", topK
        ));

        String url = String.format("%s/models/%s:generateContent?key=%s",
            API_BASE_URL, model, apiKey);

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("Gemini API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode candidates = json.path("candidates");

            if (candidates.isEmpty()) {
                return NodeExecutionResult.failure("No response generated");
            }

            StringBuilder contentBuilder = new StringBuilder();
            for (JsonNode part : candidates.get(0).path("content").path("parts")) {
                contentBuilder.append(part.path("text").asText());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("content", contentBuilder.toString());
            output.put("finishReason", candidates.get(0).path("finishReason").asText());

            if (json.has("usageMetadata")) {
                output.put("usage", Map.of(
                    "promptTokenCount", json.path("usageMetadata").path("promptTokenCount").asInt(),
                    "candidatesTokenCount", json.path("usageMetadata").path("candidatesTokenCount").asInt(),
                    "totalTokenCount", json.path("usageMetadata").path("totalTokenCount").asInt()
                ));
            }

            log.info("Gemini content generated successfully");
            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult chat(String apiKey, Map<String, Object> params) throws IOException {
        String model = getParam(params, "model", "gemini-2.0-flash-exp");
        String message = getRequiredParam(params, "message");
        String historyJson = getParam(params, "history", "[]");
        double temperature = getDoubleParam(params, "temperature", 1.0);
        int maxOutputTokens = getIntParam(params, "maxOutputTokens", 2048);

        // Parse history
        List<Map<String, Object>> contents = new ArrayList<>();
        try {
            JsonNode historyNode = objectMapper.readTree(historyJson);
            for (JsonNode msg : historyNode) {
                String role = msg.path("role").asText("user");
                String content = msg.path("content").asText();
                contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", content))
                ));
            }
        } catch (Exception e) {
            // Ignore parse errors, start fresh
        }

        // Add current message
        contents.add(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", message))
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", contents);
        payload.put("generationConfig", Map.of(
            "temperature", temperature,
            "maxOutputTokens", maxOutputTokens
        ));

        String url = String.format("%s/models/%s:generateContent?key=%s",
            API_BASE_URL, model, apiKey);

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("Gemini API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode candidates = json.path("candidates");

            if (candidates.isEmpty()) {
                return NodeExecutionResult.failure("No response generated");
            }

            StringBuilder contentBuilder = new StringBuilder();
            for (JsonNode part : candidates.get(0).path("content").path("parts")) {
                contentBuilder.append(part.path("text").asText());
            }

            String responseContent = contentBuilder.toString();

            // Build updated history
            List<Map<String, String>> updatedHistory = new ArrayList<>();
            for (Map<String, Object> content : contents) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                updatedHistory.add(Map.of(
                    "role", content.get("role").toString(),
                    "content", parts.get(0).get("text")
                ));
            }
            updatedHistory.add(Map.of("role", "model", "content", responseContent));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("content", responseContent);
            output.put("history", updatedHistory);
            output.put("finishReason", candidates.get(0).path("finishReason").asText());

            log.info("Gemini chat response generated");
            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult embedContent(String apiKey, Map<String, Object> params) throws IOException {
        String model = getParam(params, "model", "text-embedding-004");
        String content = getRequiredParam(params, "content");
        String taskType = getParam(params, "taskType", "RETRIEVAL_DOCUMENT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "models/" + model);
        payload.put("content", Map.of(
            "parts", List.of(Map.of("text", content))
        ));
        payload.put("taskType", taskType);

        String url = String.format("%s/models/%s:embedContent?key=%s",
            API_BASE_URL, model, apiKey);

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("Gemini API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode embeddingNode = json.path("embedding").path("values");

            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add(value.asDouble());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("embedding", embedding);
            output.put("dimensions", embedding.size());

            log.info("Gemini embedding created, dimensions: {}", embedding.size());
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
