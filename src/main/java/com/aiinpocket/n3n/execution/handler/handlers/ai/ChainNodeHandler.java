package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.chain.Chain;
import com.aiinpocket.n3n.ai.chain.ChainResult;
import com.aiinpocket.n3n.ai.chain.executor.ChainExecutor;
import com.aiinpocket.n3n.ai.chain.impl.ConversationChain;
import com.aiinpocket.n3n.ai.chain.impl.LLMChain;
import com.aiinpocket.n3n.ai.chain.impl.RouterChain;
import com.aiinpocket.n3n.ai.chain.impl.SequentialChain;
import com.aiinpocket.n3n.ai.memory.MemoryManager;
import com.aiinpocket.n3n.ai.service.AiService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeCategory;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI Chain 節點處理器
 *
 * 處理各種 AI Chain 節點類型：
 * - aiChain: 基礎 LLM Chain
 * - aiConversation: 對話 Chain（帶記憶）
 * - aiSequence: 順序執行多個 Chain
 * - aiRouter: 條件路由 Chain
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChainNodeHandler extends AbstractNodeHandler {

    private final AiService aiService;
    private final MemoryManager memoryManager;
    private final ChainExecutor chainExecutor;

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
        return "AI 處理鏈節點，支援 LLM Chain、對話 Chain、順序 Chain 和路由 Chain";
    }

    @Override
    public String getCategory() {
        return NodeCategory.AI;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String chainType = getStringConfig(context, "chainType", "llm");
        log.debug("Executing Chain node: chainType={}, nodeId={}", chainType, context.getNodeId());

        try {
            return switch (chainType.toLowerCase()) {
                case "llm" -> executeLLMChain(context);
                case "conversation" -> executeConversationChain(context);
                case "sequential" -> executeSequentialChain(context);
                case "router" -> executeRouterChain(context);
                default -> NodeExecutionResult.builder()
                        .success(false)
                        .errorMessage("Unknown chain type: " + chainType)
                        .build();
            };
        } catch (Exception e) {
            log.error("Chain execution failed", e);
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Chain execution failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 執行基礎 LLM Chain
     */
    private NodeExecutionResult executeLLMChain(NodeExecutionContext context) {
        String promptTemplate = getStringConfig(context, "promptTemplate", "{input}");
        String model = getStringConfig(context, "model", null);
        String systemPrompt = getStringConfig(context, "systemPrompt", null);
        int timeout = getIntConfig(context, "timeout", 120);

        LLMChain chain = LLMChain.builder()
                .name("llm_chain_" + context.getNodeId())
                .aiService(aiService)
                .promptTemplate(promptTemplate)
                .model(model)
                .build();

        Map<String, Object> inputs = new HashMap<>(context.getInputData());
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            inputs.put("system_prompt", systemPrompt);
        }

        ChainResult result = chainExecutor.execute(chain, inputs, timeout);

        if (result.isSuccess()) {
            return NodeExecutionResult.builder()
                    .success(true)
                    .output(result.getOutputs())
                    .build();
        } else {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage(result.getError())
                    .build();
        }
    }

    /**
     * 執行對話 Chain（帶記憶）
     */
    private NodeExecutionResult executeConversationChain(NodeExecutionContext context) {
        String conversationId = getStringConfig(context, "conversationId", null);
        if (conversationId == null) {
            conversationId = context.getExecutionId() + "_" + context.getNodeId();
        }

        String systemPrompt = getStringConfig(context, "systemPrompt", null);
        String model = getStringConfig(context, "model", null);
        int timeout = getIntConfig(context, "timeout", 120);

        ConversationChain chain = ConversationChain.builder()
                .name("conversation_" + context.getNodeId())
                .aiService(aiService)
                .memoryManager(memoryManager)
                .systemPrompt(systemPrompt)
                .model(model)
                .build();

        Map<String, Object> inputs = new HashMap<>(context.getInputData());
        inputs.put("conversation_id", conversationId);

        ChainResult result = chainExecutor.execute(chain, inputs, timeout);

        if (result.isSuccess()) {
            Map<String, Object> outputs = new HashMap<>(result.getOutputs());
            outputs.put("conversation_id", conversationId);
            return NodeExecutionResult.builder()
                    .success(true)
                    .output(outputs)
                    .build();
        } else {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage(result.getError())
                    .build();
        }
    }

    /**
     * 執行順序 Chain
     */
    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeSequentialChain(NodeExecutionContext context) {
        List<Map<String, Object>> stepsConfig = (List<Map<String, Object>>)
                context.getNodeConfig().get("steps");

        if (stepsConfig == null || stepsConfig.isEmpty()) {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Sequential chain requires steps configuration")
                    .build();
        }

        boolean returnIntermediates = getBooleanConfig(context, "returnIntermediates", false);
        int timeout = getIntConfig(context, "timeout", 300);

        List<Chain> steps = new ArrayList<>();
        for (int i = 0; i < stepsConfig.size(); i++) {
            Map<String, Object> stepConfig = stepsConfig.get(i);
            String stepName = (String) stepConfig.getOrDefault("name", "step_" + i);
            String promptTemplate = (String) stepConfig.getOrDefault("promptTemplate", "{input}");
            String model = (String) stepConfig.get("model");

            Chain stepChain = LLMChain.builder()
                    .name(stepName)
                    .aiService(aiService)
                    .promptTemplate(promptTemplate)
                    .model(model)
                    .build();

            steps.add(stepChain);
        }

        SequentialChain chain = SequentialChain.builder()
                .name("sequential_" + context.getNodeId())
                .chains(steps)
                .returnIntermediates(returnIntermediates)
                .build();

        ChainResult result = chainExecutor.execute(chain, context.getInputData(), timeout);

        if (result.isSuccess()) {
            return NodeExecutionResult.builder()
                    .success(true)
                    .output(result.getOutputs())
                    .build();
        } else {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage(result.getError())
                    .build();
        }
    }

    /**
     * 執行路由 Chain
     */
    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeRouterChain(NodeExecutionContext context) {
        Map<String, Map<String, Object>> routesConfig = (Map<String, Map<String, Object>>)
                context.getNodeConfig().get("routes");

        if (routesConfig == null || routesConfig.isEmpty()) {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Router chain requires routes configuration")
                    .build();
        }

        String routingPrompt = getStringConfig(context, "routingPrompt", null);
        String defaultRoute = getStringConfig(context, "defaultRoute", null);
        int timeout = getIntConfig(context, "timeout", 120);

        Map<String, Chain> routes = new HashMap<>();
        Chain defaultChain = null;

        for (Map.Entry<String, Map<String, Object>> entry : routesConfig.entrySet()) {
            String routeKey = entry.getKey();
            Map<String, Object> routeConfig = entry.getValue();

            String promptTemplate = (String) routeConfig.getOrDefault("promptTemplate", "{input}");
            String model = (String) routeConfig.get("model");

            Chain routeChain = LLMChain.builder()
                    .name("route_" + routeKey)
                    .aiService(aiService)
                    .promptTemplate(promptTemplate)
                    .model(model)
                    .build();

            routes.put(routeKey, routeChain);

            if (routeKey.equals(defaultRoute)) {
                defaultChain = routeChain;
            }
        }

        RouterChain chain = RouterChain.builder()
                .name("router_" + context.getNodeId())
                .routes(routes)
                .defaultRoute(defaultChain)
                .aiService(aiService)
                .routingPrompt(routingPrompt)
                .build();

        ChainResult result = chainExecutor.execute(chain, context.getInputData(), timeout);

        if (result.isSuccess()) {
            return NodeExecutionResult.builder()
                    .success(true)
                    .output(result.getOutputs())
                    .build();
        } else {
            return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage(result.getError())
                    .build();
        }
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
                                "description", "Chain 類型"
                        ),
                        "promptTemplate", Map.of(
                                "type", "string",
                                "description", "Prompt 模板，使用 {variable} 語法"
                        ),
                        "systemPrompt", Map.of(
                                "type", "string",
                                "description", "系統提示詞"
                        ),
                        "model", Map.of(
                                "type", "string",
                                "description", "使用的 AI 模型"
                        ),
                        "timeout", Map.of(
                                "type", "integer",
                                "default", 120,
                                "description", "執行超時（秒）"
                        )
                )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "input", "type", "string", "required", true,
                                "description", "輸入文字")
                ),
                "outputs", List.of(
                        Map.of("name", "output", "type", "string", "description", "AI 回應"),
                        Map.of("name", "conversation_id", "type", "string",
                                "description", "對話 ID（僅對話 Chain）")
                )
        );
    }
}
