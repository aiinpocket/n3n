package com.aiinpocket.n3n.execution.handler.handlers.action;

import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.aiinpocket.n3n.execution.handler.*;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.JavaScriptEngine;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.ScriptResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Transform Node Handler
 *
 * Allows users to describe data transformation requirements in natural language.
 * AI automatically generates JavaScript code and executes it.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiTransformNodeHandler extends AbstractNodeHandler {

    private final SimpleAIProviderRegistry aiProviderRegistry;
    private final JavaScriptEngine javaScriptEngine;
    private final ObjectMapper objectMapper;

    /**
     * Code cache (avoids regenerating identical transform code)
     * Key: transformDescription + inputSchemaHash
     */
    private final Map<String, String> codeCache = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
        You are a professional data transformation code generator. Based on the user's natural language description, generate JavaScript code to transform data.

        Rules:
        1. Output only pure JavaScript code, no explanations or markdown
        2. Use the $input variable to access input data
        3. Directly return the transformed result
        4. Handle possible null or undefined values
        5. Code should be concise and efficient

        Example input description: "Multiply all prices by 1.1"
        Example output:
        const result = $input.map(item => ({
          ...item,
          price: item.price * 1.1
        }));
        return result;

        Now generate code based on the description:
        """;

    @Override
    public String getType() {
        return "aiTransform";
    }

    @Override
    public String getDisplayName() {
        return "AI Data Transform";
    }

    @Override
    public String getDescription() {
        return "Describe data transformation logic in natural language; AI generates and executes code automatically";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "robot";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String transformDescription = getStringConfig(context, "transformDescription", "");
        boolean cacheCode = getBooleanConfig(context, "cacheCode", true);
        long timeout = getIntConfig(context, "timeout", 30000);

        if (transformDescription.isBlank()) {
            return NodeExecutionResult.failure("Transform description is required");
        }

        // Get input data
        Map<String, Object> inputData = context.getInputData();
        if (inputData == null) {
            inputData = new HashMap<>();
        }

        try {
            // 1. Generate or get cached code
            String generatedCode = generateOrGetCachedCode(
                transformDescription,
                inputData,
                cacheCode,
                context.getUserId()
            );

            if (generatedCode == null || generatedCode.isBlank()) {
                return NodeExecutionResult.failure("AI failed to generate transform code");
            }

            log.debug("AI Transform executing code:\n{}", generatedCode);

            // 2. Prepare script input
            Map<String, Object> scriptInput = new HashMap<>(inputData);
            scriptInput.put("$executionId", context.getExecutionId().toString());
            scriptInput.put("$nodeId", context.getNodeId());

            // 3. Execute the generated code
            ScriptResult result = javaScriptEngine.execute(generatedCode, scriptInput, timeout);

            if (!result.isSuccess()) {
                log.warn("AI Transform execution failed: {}", result.getErrorMessage());
                return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Transform execution failed: " + result.getErrorMessage())
                    .metadata(Map.of(
                        "generatedCode", generatedCode,
                        "errorType", result.getErrorType() != null ? result.getErrorType() : "UNKNOWN"
                    ))
                    .build();
            }

            // 4. Build output
            Map<String, Object> output = new HashMap<>();
            if (result.getData() != null) {
                output.putAll(result.getData());
            } else if (result.getOutput() != null) {
                output.put("result", result.getOutput());
            }

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .metadata(Map.of(
                    "generatedCode", generatedCode,
                    "executionTimeMs", result.getExecutionTimeMs(),
                    "cached", codeCache.containsKey(getCacheKey(transformDescription, inputData))
                ))
                .build();

        } catch (Exception e) {
            log.error("AI Transform error", e);
            return NodeExecutionResult.failure("AI transform error occurred");
        }
    }

    private String generateOrGetCachedCode(
            String description,
            Map<String, Object> inputData,
            boolean useCache,
            java.util.UUID userId) {

        String cacheKey = getCacheKey(description, inputData);

        // Check cache
        if (useCache && codeCache.containsKey(cacheKey)) {
            log.debug("Using cached code for description: {}", description);
            return codeCache.get(cacheKey);
        }

        // Generate new code
        String code = generateTransformCode(description, inputData, userId);

        // Save to cache
        if (useCache && code != null && !code.isBlank()) {
            // Limit cache size
            if (codeCache.size() > 1000) {
                // Simple eviction strategy: clear cache
                codeCache.clear();
            }
            codeCache.put(cacheKey, code);
        }

        return code;
    }

    private String generateTransformCode(
            String description,
            Map<String, Object> inputData,
            java.util.UUID userId) {

        try {
            // Build prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("Transform description: ").append(description).append("\n\n");

            // Add input data sample (helps AI understand data structure)
            if (!inputData.isEmpty()) {
                try {
                    String sampleJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(truncateForSample(inputData));
                    prompt.append("Input data sample:\n").append(sampleJson).append("\n\n");
                } catch (Exception e) {
                    log.debug("Could not serialize input sample: {}", e.getMessage());
                }
            }

            prompt.append("Please generate JavaScript code:");

            // Call AI
            String response = aiProviderRegistry.chatWithFailover(
                prompt.toString(),
                SYSTEM_PROMPT,
                1500, // maxTokens
                0.2,  // temperature (lower for more stable output)
                userId
            );

            // Clean response (remove possible markdown)
            return cleanCodeResponse(response);

        } catch (Exception e) {
            log.error("Failed to generate transform code", e);
            return null;
        }
    }

    private String getCacheKey(String description, Map<String, Object> inputData) {
        // Use description + input structure hash
        int inputHash = inputData.keySet().hashCode();
        return description.hashCode() + "_" + inputHash;
    }

    private Map<String, Object> truncateForSample(Map<String, Object> data) {
        // Limit sample data size
        Map<String, Object> sample = new HashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (count >= 5) break; // Max 5 fields
            Object value = entry.getValue();
            if (value instanceof List<?> list && list.size() > 3) {
                // Truncate long lists
                sample.put(entry.getKey(), list.subList(0, 3));
            } else {
                sample.put(entry.getKey(), value);
            }
            count++;
        }
        return sample;
    }

    private String cleanCodeResponse(String response) {
        if (response == null) return null;

        String code = response.trim();

        // Remove markdown code blocks
        if (code.startsWith("```javascript")) {
            code = code.substring("```javascript".length());
        } else if (code.startsWith("```js")) {
            code = code.substring("```js".length());
        } else if (code.startsWith("```")) {
            code = code.substring(3);
        }

        if (code.endsWith("```")) {
            code = code.substring(0, code.length() - 3);
        }

        return code.trim();
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object description = config.get("transformDescription");
        if (description == null || description.toString().trim().isEmpty()) {
            return ValidationResult.invalid("transformDescription", "Transform description cannot be empty");
        }
        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("transformDescription"),
            "properties", Map.of(
                "transformDescription", Map.of(
                    "type", "string",
                    "title", "Transform Description",
                    "description", "Describe your data transformation logic in natural language, e.g., 'Filter products with price greater than 100'",
                    "format", "textarea"
                ),
                "cacheCode", Map.of(
                    "type", "boolean",
                    "title", "Cache Generated Code",
                    "description", "Reuse previously generated code for the same description to improve performance",
                    "default", true
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "title", "Execution Timeout (ms)",
                    "description", "Maximum execution time for the generated code",
                    "default", 30000,
                    "minimum", 1000,
                    "maximum", 300000
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
