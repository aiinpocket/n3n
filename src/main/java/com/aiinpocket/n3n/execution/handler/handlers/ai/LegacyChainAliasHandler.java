package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.service.AiService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeCategory;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.memory.MemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 舊版 aiChain 節點的相容別名處理器（deprecated，請改用 aiPipeline）。
 *
 * 取代原本基於 ai/chain 與 ai/memory 舊堆疊的 ChainNodeHandler，
 * 以平台共用的 AI Provider（AiService → AssistantAiClient）重新實作
 * 同樣的四種 chainType 語意，讓既有流程不需修改即可繼續執行：
 *
 * - llm：提示詞模板（{variable} 語法）單次呼叫
 * - conversation：多輪對話；記憶改存於節點側 MemoryStore（Redis），
 *   視窗大小由 memoryWindow 設定（預設 10 筆）。與舊版差異：舊堆疊的
 *   per-conversation Redis 記憶（含摘要 / 向量檢索模式）已簡化為滑動視窗。
 * - sequential：多步驟依序執行，前一步輸出（output）併入下一步輸入
 * - router：AI 或 route 鍵路由至不同提示詞模板
 *
 * 輸出鍵（output / conversation_id / intermediates）與舊版一致。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyChainAliasHandler extends AbstractNodeHandler {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)\\}");
    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful AI assistant.
            Respond in the same language the user used.
            Provide coherent and helpful answers based on conversation history.
            """;
    private static final int DEFAULT_MEMORY_WINDOW = 10;

    private final AiService aiService;
    private final MemoryStore memoryStore;

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
        return "(相容舊流程，請改用 aiPipeline) AI processing chain node supporting "
                + "LLM Chain, Conversation Chain, Sequential Chain, and Router Chain";
    }

    @Override
    public String getCategory() {
        return NodeCategory.AI;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String chainType = getStringConfig(context, "chainType", "llm");
        int timeout = getIntConfig(context, "timeout",
                "sequential".equalsIgnoreCase(chainType) ? 300 : 120);
        log.debug("Executing legacy aiChain node: chainType={}, nodeId={}",
                chainType, context.getNodeId());

        return runWithTimeout(timeout, () -> {
            try {
                return switch (chainType.toLowerCase()) {
                    case "llm" -> executeLlm(context);
                    case "conversation" -> executeConversation(context);
                    case "sequential" -> executeSequential(context);
                    case "router" -> executeRouter(context);
                    default -> NodeExecutionResult.failure("Unknown chain type: " + chainType);
                };
            } catch (Exception e) {
                log.error("Legacy chain execution failed", e);
                return NodeExecutionResult.failure("Chain execution failed");
            }
        });
    }

    private NodeExecutionResult executeLlm(NodeExecutionContext context) {
        String promptTemplate = getStringConfig(context, "promptTemplate", "{input}");
        String model = getStringConfig(context, "model", null);
        String systemPrompt = getStringConfig(context, "systemPrompt", null);

        Map<String, Object> inputs = new HashMap<>(context.getInputData());
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            inputs.put("system_prompt", systemPrompt);
        }

        String prompt = formatPrompt(promptTemplate, inputs);
        String response = generate(prompt, model);

        return NodeExecutionResult.success(mapOf("output", response));
    }

    private NodeExecutionResult executeConversation(NodeExecutionContext context) throws Exception {
        String conversationId = getStringConfig(context, "conversationId", null);
        if (conversationId == null) {
            conversationId = context.getExecutionId() + "_" + context.getNodeId();
        }

        Object rawInput = context.getInputData().get("input");
        String userInput = rawInput != null ? rawInput.toString() : null;
        if (userInput == null || userInput.isBlank()) {
            return NodeExecutionResult.failure("No input provided");
        }

        String systemPrompt = getStringConfig(context, "systemPrompt", null);
        String model = getStringConfig(context, "model", null);
        int memoryWindow = getIntConfig(context, "memoryWindow", DEFAULT_MEMORY_WINDOW);

        // 租戶隔離：以 userId（缺失時退回唯一的 executionId）為記憶鍵加前綴，
        // 避免 config 提供的 conversationId 在不同使用者間碰撞而洩漏對話記憶。
        String userScope = context.getUserId() != null
                ? context.getUserId().toString()
                : context.getExecutionId().toString();
        String sessionId = userScope + ":legacy-chain:" + conversationId;
        List<MemoryStore.MemoryEntry> history = memoryStore.getHistory(sessionId, memoryWindow).get();

        StringBuilder prompt = new StringBuilder();
        prompt.append("System: ")
                .append(systemPrompt != null && !systemPrompt.isBlank() ? systemPrompt : DEFAULT_SYSTEM_PROMPT)
                .append("\n\n");
        if (!history.isEmpty()) {
            prompt.append("Conversation History:\n");
            for (MemoryStore.MemoryEntry entry : history) {
                String role = "assistant".equals(entry.role()) ? "Assistant" : "User";
                prompt.append(role).append(": ").append(entry.content()).append("\n");
            }
            prompt.append("\n");
        }
        prompt.append("User: ").append(userInput).append("\n\nAssistant:");

        String response = generate(prompt.toString(), model);

        memoryStore.store(sessionId, MemoryStore.MemoryEntry.user(userInput)).get();
        memoryStore.store(sessionId, MemoryStore.MemoryEntry.assistant(response)).get();

        Map<String, Object> outputs = mapOf("output", response);
        outputs.put("conversation_id", conversationId);
        return NodeExecutionResult.success(outputs);
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeSequential(NodeExecutionContext context) {
        List<Map<String, Object>> steps = (List<Map<String, Object>>)
                context.getNodeConfig().get("steps");
        if (steps == null || steps.isEmpty()) {
            return NodeExecutionResult.failure("Sequential chain requires steps configuration");
        }

        boolean returnIntermediates = getBooleanConfig(context, "returnIntermediates", false);
        Map<String, Object> intermediates = new HashMap<>(context.getInputData());
        String lastOutput = null;

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String template = (String) step.getOrDefault("promptTemplate", "{input}");
            String model = (String) step.get("model");

            String prompt = formatPrompt(template, intermediates);
            lastOutput = generate(prompt, model);
            intermediates.put("output", lastOutput);
        }

        Map<String, Object> outputs = mapOf("output", lastOutput);
        if (returnIntermediates) {
            outputs.put("intermediates", intermediates);
        }
        return NodeExecutionResult.success(outputs);
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeRouter(NodeExecutionContext context) {
        Map<String, Map<String, Object>> routes = (Map<String, Map<String, Object>>)
                context.getNodeConfig().get("routes");
        if (routes == null || routes.isEmpty()) {
            return NodeExecutionResult.failure("Router chain requires routes configuration");
        }

        String routingPrompt = getStringConfig(context, "routingPrompt", null);
        String defaultRoute = getStringConfig(context, "defaultRoute", null);
        Map<String, Object> inputs = context.getInputData();

        String routeKey = determineRoute(inputs, routes.keySet(), routingPrompt);

        Map<String, Object> routeConfig = routes.get(routeKey);
        if (routeConfig == null && defaultRoute != null) {
            routeConfig = routes.get(defaultRoute);
        }
        if (routeConfig == null) {
            return NodeExecutionResult.failure("No route found for key: " + routeKey);
        }

        String template = (String) routeConfig.getOrDefault("promptTemplate", "{input}");
        String model = (String) routeConfig.get("model");

        String prompt = formatPrompt(template, inputs);
        String response = generate(prompt, model);

        return NodeExecutionResult.success(mapOf("output", response));
    }

    private String determineRoute(Map<String, Object> inputs, Set<String> routeKeys,
                                  String routingPrompt) {
        if (routingPrompt != null && !routingPrompt.isBlank()) {
            Object input = inputs.get("input");
            String prompt = routingPrompt
                    .replace("{input}", input != null ? input.toString() : "")
                    .replace("{routes}", String.join(", ", routeKeys));
            String response = generate(prompt, null);
            String lower = response.toLowerCase(Locale.ROOT).trim();
            for (String key : routeKeys) {
                if (lower.contains(key.toLowerCase(Locale.ROOT))) {
                    return key;
                }
            }
            return "default";
        }
        Object routeValue = inputs.get("route");
        return routeValue != null ? routeValue.toString() : "default";
    }

    private String generate(String prompt, String model) {
        return model != null && !model.isBlank()
                ? aiService.generateText(prompt, model)
                : aiService.generateText(prompt);
    }

    private String formatPrompt(String template, Map<String, Object> inputs) {
        if (template == null) {
            Object input = inputs.get("input");
            return input != null ? input.toString() : "";
        }
        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = inputs.get(varName);
            if (value != null) {
                result = result.replace("{" + varName + "}", value.toString());
            }
        }
        return result;
    }

    private NodeExecutionResult runWithTimeout(int timeoutSeconds,
                                               java.util.function.Supplier<NodeExecutionResult> task) {
        CompletableFuture<NodeExecutionResult> future =
                CompletableFuture.supplyAsync(task, command -> Thread.ofVirtual().start(command));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return NodeExecutionResult.failure(
                    "Execution timed out after " + timeoutSeconds + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeExecutionResult.failure("Execution interrupted");
        } catch (Exception e) {
            log.error("Legacy chain execution failed", e);
            return NodeExecutionResult.failure("Chain execution failed");
        }
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "chainType", Map.of(
                                "type", "string",
                                "enum", List.of("llm", "conversation", "sequential", "router"),
                                "default", "llm",
                                "description", "Chain type"
                        ),
                        "promptTemplate", Map.of(
                                "type", "string",
                                "description", "Prompt template using {variable} syntax"
                        ),
                        "systemPrompt", Map.of(
                                "type", "string",
                                "description", "System prompt"
                        ),
                        "model", Map.of(
                                "type", "string",
                                "description", "AI model to use"
                        ),
                        "memoryWindow", Map.of(
                                "type", "integer",
                                "default", DEFAULT_MEMORY_WINDOW,
                                "description", "Conversation memory window (conversation chain only)"
                        ),
                        "timeout", Map.of(
                                "type", "integer",
                                "default", 120,
                                "description", "Execution timeout (seconds)"
                        )
                )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "input", "type", "string", "required", true,
                                "description", "Input text")
                ),
                "outputs", List.of(
                        Map.of("name", "output", "type", "string", "description", "AI response"),
                        Map.of("name", "conversation_id", "type", "string",
                                "description", "Conversation ID (conversation chain only)")
                )
        );
    }
}
