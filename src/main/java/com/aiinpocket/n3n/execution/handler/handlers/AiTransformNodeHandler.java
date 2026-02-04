package com.aiinpocket.n3n.execution.handler.handlers;

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
 * AI Transform 節點處理器
 *
 * 讓使用者用自然語言描述資料轉換需求，
 * AI 自動生成 JavaScript 程式碼並執行。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiTransformNodeHandler extends AbstractNodeHandler {

    private final SimpleAIProviderRegistry aiProviderRegistry;
    private final JavaScriptEngine javaScriptEngine;
    private final ObjectMapper objectMapper;

    /**
     * 程式碼快取（避免重複生成相同的轉換程式碼）
     * Key: transformDescription + inputSchemaHash
     */
    private final Map<String, String> codeCache = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
        你是一個專業的資料轉換程式碼生成器。根據使用者的自然語言描述，生成 JavaScript 程式碼來轉換資料。

        規則：
        1. 只輸出純 JavaScript 程式碼，不要有任何解釋或 markdown
        2. 使用 $input 變數存取輸入資料
        3. 直接 return 轉換後的結果
        4. 處理可能的 null 或 undefined
        5. 程式碼需要簡潔、高效

        範例輸入描述：「將所有價格乘以 1.1」
        範例輸出：
        const result = $input.map(item => ({
          ...item,
          price: item.price * 1.1
        }));
        return result;

        現在請根據描述生成程式碼：
        """;

    @Override
    public String getType() {
        return "aiTransform";
    }

    @Override
    public String getDisplayName() {
        return "AI 資料轉換";
    }

    @Override
    public String getDescription() {
        return "使用自然語言描述資料轉換邏輯，AI 自動生成並執行程式碼";
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
            return NodeExecutionResult.failure("請輸入轉換描述");
        }

        // 取得輸入資料
        Map<String, Object> inputData = context.getInputData();
        if (inputData == null) {
            inputData = new HashMap<>();
        }

        try {
            // 1. 生成或取得快取的程式碼
            String generatedCode = generateOrGetCachedCode(
                transformDescription,
                inputData,
                cacheCode,
                context.getUserId()
            );

            if (generatedCode == null || generatedCode.isBlank()) {
                return NodeExecutionResult.failure("AI 無法生成轉換程式碼");
            }

            log.debug("AI Transform executing code:\n{}", generatedCode);

            // 2. 準備腳本輸入
            Map<String, Object> scriptInput = new HashMap<>(inputData);
            scriptInput.put("$executionId", context.getExecutionId().toString());
            scriptInput.put("$nodeId", context.getNodeId());

            // 3. 執行生成的程式碼
            ScriptResult result = javaScriptEngine.execute(generatedCode, scriptInput, timeout);

            if (!result.isSuccess()) {
                log.warn("AI Transform execution failed: {}", result.getErrorMessage());
                return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("轉換執行失敗: " + result.getErrorMessage())
                    .metadata(Map.of(
                        "generatedCode", generatedCode,
                        "errorType", result.getErrorType() != null ? result.getErrorType() : "UNKNOWN"
                    ))
                    .build();
            }

            // 4. 建構輸出
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
            return NodeExecutionResult.failure("AI 轉換發生錯誤: " + e.getMessage());
        }
    }

    private String generateOrGetCachedCode(
            String description,
            Map<String, Object> inputData,
            boolean useCache,
            java.util.UUID userId) {

        String cacheKey = getCacheKey(description, inputData);

        // 檢查快取
        if (useCache && codeCache.containsKey(cacheKey)) {
            log.debug("Using cached code for description: {}", description);
            return codeCache.get(cacheKey);
        }

        // 生成新程式碼
        String code = generateTransformCode(description, inputData, userId);

        // 儲存到快取
        if (useCache && code != null && !code.isBlank()) {
            // 限制快取大小
            if (codeCache.size() > 1000) {
                // 簡單清除策略：清空快取
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
            // 建構提示詞
            StringBuilder prompt = new StringBuilder();
            prompt.append("轉換描述：").append(description).append("\n\n");

            // 加入輸入資料範例（幫助 AI 理解資料結構）
            if (!inputData.isEmpty()) {
                try {
                    String sampleJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(truncateForSample(inputData));
                    prompt.append("輸入資料範例：\n").append(sampleJson).append("\n\n");
                } catch (Exception e) {
                    log.debug("Could not serialize input sample: {}", e.getMessage());
                }
            }

            prompt.append("請生成 JavaScript 程式碼：");

            // 呼叫 AI
            String response = aiProviderRegistry.chatWithFailover(
                prompt.toString(),
                SYSTEM_PROMPT,
                1500, // maxTokens
                0.2,  // temperature (低一點以獲得穩定輸出)
                userId
            );

            // 清理回應（移除可能的 markdown）
            return cleanCodeResponse(response);

        } catch (Exception e) {
            log.error("Failed to generate transform code", e);
            return null;
        }
    }

    private String getCacheKey(String description, Map<String, Object> inputData) {
        // 使用描述 + 輸入結構的 hash
        int inputHash = inputData.keySet().hashCode();
        return description.hashCode() + "_" + inputHash;
    }

    private Map<String, Object> truncateForSample(Map<String, Object> data) {
        // 限制範例資料大小
        Map<String, Object> sample = new HashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (count >= 5) break; // 最多 5 個欄位
            Object value = entry.getValue();
            if (value instanceof List<?> list && list.size() > 3) {
                // 截斷長列表
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

        // 移除 markdown 程式碼區塊
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
            return ValidationResult.invalid("transformDescription", "轉換描述不可為空");
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
                    "title", "轉換描述",
                    "description", "用自然語言描述您想要的資料轉換邏輯，例如：「過濾出價格大於 100 的商品」",
                    "format", "textarea"
                ),
                "cacheCode", Map.of(
                    "type", "boolean",
                    "title", "快取生成的程式碼",
                    "description", "相同的描述會重複使用已生成的程式碼，提高效能",
                    "default", true
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "title", "執行超時（毫秒）",
                    "description", "程式碼執行的最大時間",
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
