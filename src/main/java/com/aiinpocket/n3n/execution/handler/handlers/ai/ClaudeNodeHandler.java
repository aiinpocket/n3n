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
 * Claude (Anthropic) multi-operation node handler.
 *
 * Supports:
 * - Messages API (Claude 3.5, Claude 3, etc.)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClaudeNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String API_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";

    @Override
    public String getType() {
        return "claude";
    }

    @Override
    public String getDisplayName() {
        return "Claude";
    }

    @Override
    public String getDescription() {
        return "Interact with Anthropic's Claude AI models for text generation and analysis.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "claude";
    }

    @Override
    public String getCredentialType() {
        return "anthropic";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("messages", ResourceDef.of("messages", "Messages", "Create messages with Claude"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("messages", List.of(
            OperationDef.create("create", "Create Message")
                .description("Generate a message response from Claude")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "claude-sonnet-4-20250514",
                            "claude-opus-4-20250514",
                            "claude-3-7-sonnet-20250219",
                            "claude-3-5-sonnet-20241022",
                            "claude-3-5-haiku-20241022",
                            "claude-3-opus-20240229",
                            "claude-3-haiku-20240307"
                        ))
                        .withDefault("claude-sonnet-4-20250514")
                        .withDescription("The Claude model to use")
                        .required(),
                    FieldDef.textarea("prompt", "Prompt")
                        .withDescription("The user message to send")
                        .withPlaceholder("Enter your prompt here...")
                        .required(),
                    FieldDef.textarea("systemPrompt", "System Prompt")
                        .withDescription("Optional system message to set context")
                        .withPlaceholder("You are a helpful assistant..."),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(1.0)
                        .withRange(0.0, 1.0)
                        .withDescription("Sampling temperature (0-1)"),
                    FieldDef.integer("maxTokens", "Max Tokens")
                        .withDefault(4096)
                        .withRange(1, 200000)
                        .withDescription("Maximum tokens in the response")
                ))
                .outputDescription("Returns the generated text in 'content' field")
                .build(),

            OperationDef.create("analyze", "Analyze Content")
                .description("Analyze text or content with Claude")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "claude-sonnet-4-20250514",
                            "claude-opus-4-20250514",
                            "claude-3-7-sonnet-20250219",
                            "claude-3-5-sonnet-20241022"
                        ))
                        .withDefault("claude-sonnet-4-20250514")
                        .withDescription("The Claude model to use")
                        .required(),
                    FieldDef.textarea("content", "Content to Analyze")
                        .withDescription("The content to analyze")
                        .withPlaceholder("Paste content here...")
                        .required(),
                    FieldDef.select("analysisType", "Analysis Type", List.of(
                            "summarize", "extract_key_points", "sentiment", "translate", "custom"
                        ))
                        .withDefault("summarize")
                        .withDescription("Type of analysis to perform"),
                    FieldDef.textarea("customPrompt", "Custom Prompt")
                        .withDescription("Custom analysis instructions (for 'custom' type)")
                        .withPlaceholder("Analyze the following content and..."),
                    FieldDef.integer("maxTokens", "Max Tokens")
                        .withDefault(2048)
                        .withRange(1, 100000)
                        .withDescription("Maximum tokens in the response")
                ))
                .outputDescription("Returns analysis result in 'content' field")
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
            apiKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return NodeExecutionResult.failure("Anthropic API key is required");
        }

        try {
            return switch (resource) {
                case "messages" -> switch (operation) {
                    case "create" -> createMessage(apiKey, params);
                    case "analyze" -> analyzeContent(apiKey, params);
                    default -> NodeExecutionResult.failure("Unknown messages operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("Claude API error: {}", e.getMessage());
            return NodeExecutionResult.failure("Claude API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult createMessage(String apiKey, Map<String, Object> params) throws IOException {
        String model = getParam(params, "model", "claude-sonnet-4-20250514");
        String prompt = getRequiredParam(params, "prompt");
        String systemPrompt = getParam(params, "systemPrompt", "");
        double temperature = getDoubleParam(params, "temperature", 1.0);
        int maxTokens = getIntParam(params, "maxTokens", 4096);

        return callMessagesApi(apiKey, model, prompt, systemPrompt, temperature, maxTokens);
    }

    private NodeExecutionResult analyzeContent(String apiKey, Map<String, Object> params) throws IOException {
        String model = getParam(params, "model", "claude-sonnet-4-20250514");
        String content = getRequiredParam(params, "content");
        String analysisType = getParam(params, "analysisType", "summarize");
        String customPrompt = getParam(params, "customPrompt", "");
        int maxTokens = getIntParam(params, "maxTokens", 2048);

        // Build analysis prompt
        String prompt = switch (analysisType) {
            case "summarize" -> "Please summarize the following content concisely:\n\n" + content;
            case "extract_key_points" -> "Please extract the key points from the following content:\n\n" + content;
            case "sentiment" -> "Please analyze the sentiment of the following content:\n\n" + content;
            case "translate" -> "Please translate the following content to English:\n\n" + content;
            case "custom" -> customPrompt + "\n\n" + content;
            default -> "Please analyze the following content:\n\n" + content;
        };

        return callMessagesApi(apiKey, model, prompt, "", 1.0, maxTokens);
    }

    private NodeExecutionResult callMessagesApi(
        String apiKey,
        String model,
        String prompt,
        String systemPrompt,
        double temperature,
        int maxTokens
    ) throws IOException {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
            "role", "user",
            "content", prompt
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);

        if (!systemPrompt.isEmpty()) {
            payload.put("system", systemPrompt);
        }

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(API_BASE_URL + "/messages")
            .post(body)
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonNode error = objectMapper.readTree(responseBody);
                String errorMsg = error.path("error").path("message").asText("Unknown error");
                return NodeExecutionResult.failure("Claude API error: " + errorMsg);
            }

            JsonNode json = objectMapper.readTree(responseBody);

            // Extract content from response
            StringBuilder contentBuilder = new StringBuilder();
            for (JsonNode block : json.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    contentBuilder.append(block.path("text").asText());
                }
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("content", contentBuilder.toString());
            output.put("model", json.path("model").asText());
            output.put("stopReason", json.path("stop_reason").asText());
            output.put("usage", Map.of(
                "inputTokens", json.path("usage").path("input_tokens").asInt(),
                "outputTokens", json.path("usage").path("output_tokens").asInt()
            ));

            log.info("Claude message created successfully, tokens: input={}, output={}",
                json.path("usage").path("input_tokens").asInt(),
                json.path("usage").path("output_tokens").asInt());
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
