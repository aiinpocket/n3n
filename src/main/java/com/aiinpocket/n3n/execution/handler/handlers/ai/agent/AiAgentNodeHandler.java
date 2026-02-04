package com.aiinpocket.n3n.execution.handler.handlers.ai.agent;

import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.AbstractAiNodeHandler;
import com.aiinpocket.n3n.execution.handler.handlers.ai.base.StreamChunk;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 節點處理器
 *
 * 功能：
 * - ReAct 模式的工具調用迭代
 * - 支援多輪對話和工具調用
 * - 自動工具選擇和執行
 * - 思考過程可視化
 *
 * ReAct 模式流程：
 * 1. AI 分析任務，決定是否需要工具
 * 2. 如需工具，AI 選擇工具並提供參數
 * 3. 系統執行工具，返回結果
 * 4. AI 分析結果，決定下一步
 * 5. 重複直到完成或達到迭代上限
 */
@Component
@Slf4j
public class AiAgentNodeHandler extends AbstractAiNodeHandler {

    private final AgentNodeToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    public AiAgentNodeHandler(
        AiProviderFactory providerFactory,
        AgentNodeToolRegistry toolRegistry,
        ObjectMapper objectMapper
    ) {
        super(providerFactory);
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return "aiAgent";
    }

    @Override
    public String getDisplayName() {
        return "AI Agent";
    }

    @Override
    public String getDescription() {
        return "Intelligent AI agent that can use tools to accomplish tasks. " +
               "Supports HTTP requests, code execution, web search, and custom tools.";
    }

    @Override
    public String getIcon() {
        return "robot";
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("agent", ResourceDef.of("agent", "Agent", "AI Agent with tool use"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("agent", List.of(
            OperationDef.create("execute", "Execute Task")
                .description("Execute a task using AI agent with tool calling capabilities")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "claude", "gemini"))
                        .withDefault("openai")
                        .withDescription("AI provider to use (must support function calling)")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            // OpenAI (function calling)
                            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo",
                            // Claude (tool use)
                            "claude-3-5-sonnet-20241022", "claude-3-opus-20240229",
                            // Gemini (function calling)
                            "gemini-1.5-pro", "gemini-1.5-flash"
                        ))
                        .withDefault("gpt-4o")
                        .withDescription("Model to use (must support tool use)")
                        .required(),
                    FieldDef.textarea("systemPrompt", "System Prompt")
                        .withDescription("System instructions for the agent")
                        .withPlaceholder("You are a helpful AI assistant with access to tools..."),
                    FieldDef.textarea("task", "Task")
                        .withDescription("The task for the agent to accomplish")
                        .withPlaceholder("Describe what you want the agent to do...")
                        .required(),
                    FieldDef.multiSelect("tools", "Enabled Tools",
                            List.of("http_request", "code_execution", "web_search"))
                        .withDefault(List.of("http_request", "web_search"))
                        .withDescription("Tools available to the agent"),
                    FieldDef.integer("maxIterations", "Max Iterations")
                        .withDefault(DEFAULT_MAX_ITERATIONS)
                        .withRange(1, 50)
                        .withDescription("Maximum tool call iterations"),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(0.3)
                        .withRange(0.0, 1.0)
                        .withDescription("Lower values for more focused execution")
                ))
                .outputDescription("Returns final response, tool call history, and execution trace")
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
        if (!"agent".equals(resource) || !"execute".equals(operation)) {
            return NodeExecutionResult.failure("Unknown operation: " + resource + "." + operation);
        }

        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");
        String systemPrompt = getParam(params, "systemPrompt", getDefaultSystemPrompt());
        String task = getRequiredParam(params, "task");
        List<String> enabledTools = getListParam(params, "tools", List.of("http_request", "web_search"));
        int maxIterations = getIntParam(params, "maxIterations", DEFAULT_MAX_ITERATIONS);
        double temperature = getDoubleParam(params, "temperature", 0.3);

        try {
            AiProvider provider = resolveProvider(providerId);
            AiProviderSettings settings = buildProviderSettings(credential, providerId);

            // 執行 Agent 迴圈
            AgentExecutionResult result = executeAgentLoop(
                provider, settings, model, systemPrompt, task,
                enabledTools, maxIterations, temperature, context
            );

            return NodeExecutionResult.success(result.toOutputMap());

        } catch (Exception e) {
            log.error("AI Agent error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("AI Agent error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getListParam(Map<String, Object> params, String key, List<String> defaultValue) {
        Object value = params.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        if (value instanceof String) {
            return List.of(((String) value).split(","));
        }
        return defaultValue;
    }

    private AgentExecutionResult executeAgentLoop(
        AiProvider provider,
        AiProviderSettings settings,
        String model,
        String systemPrompt,
        String task,
        List<String> enabledTools,
        int maxIterations,
        double temperature,
        NodeExecutionContext context
    ) throws Exception {

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.user(task));

        List<Map<String, Object>> toolCallHistory = new ArrayList<>();
        List<String> thinkingTrace = new ArrayList<>();
        int totalInputTokens = 0;
        int totalOutputTokens = 0;

        // 取得工具定義
        List<Map<String, Object>> tools = toolRegistry.toOpenAITools(enabledTools);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            log.debug("Agent iteration {}/{}", iteration + 1, maxIterations);
            thinkingTrace.add("=== Iteration " + (iteration + 1) + " ===");

            // 建立請求
            AiChatRequest request = AiChatRequest.builder()
                .model(model)
                .messages(messages)
                .systemPrompt(systemPrompt)
                .temperature(temperature)
                .tools(tools)
                .build();

            // 呼叫 AI
            AiResponse response = provider.chat(request, settings).get();

            // 更新 token 計數
            if (response.getUsage() != null) {
                totalInputTokens += response.getUsage().getInputTokens();
                totalOutputTokens += response.getUsage().getOutputTokens();
            }

            // 檢查是否有工具調用
            List<AiToolCall> toolCalls = response.getToolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                // 沒有工具調用，Agent 完成任務
                thinkingTrace.add("Agent completed task");
                thinkingTrace.add("Final response: " + truncate(response.getContent(), 200));

                return new AgentExecutionResult(
                    response.getContent(),
                    toolCallHistory,
                    thinkingTrace,
                    iteration + 1,
                    totalInputTokens,
                    totalOutputTokens,
                    true,
                    null
                );
            }

            // 處理工具調用
            messages.add(AiMessage.assistant(response.getContent(), toolCalls));
            thinkingTrace.add("AI thinking: " + (response.getContent() != null ? response.getContent() : "(tool call)"));

            for (AiToolCall toolCall : toolCalls) {
                thinkingTrace.add("Tool call: " + toolCall.getName() + "(" + truncate(toolCall.getArguments(), 100) + ")");

                // 執行工具
                AgentNodeTool.ToolResult toolResult = executeToolCall(toolCall, context);

                // 記錄工具調用
                Map<String, Object> callRecord = new LinkedHashMap<>();
                callRecord.put("iteration", iteration + 1);
                callRecord.put("toolId", toolCall.getId());
                callRecord.put("toolName", toolCall.getName());
                callRecord.put("arguments", toolCall.getArguments());
                callRecord.put("success", toolResult.success());
                callRecord.put("output", truncate(toolResult.output(), 500));
                if (toolResult.error() != null) {
                    callRecord.put("error", toolResult.error());
                }
                toolCallHistory.add(callRecord);

                // 加入工具結果到訊息
                String resultContent = toolResult.success()
                    ? toolResult.output()
                    : "Error: " + toolResult.error();
                messages.add(AiMessage.toolResult(toolCall.getId(), resultContent));

                thinkingTrace.add("Tool result: " + truncate(resultContent, 200));
            }
        }

        // 達到最大迭代次數
        log.warn("Agent reached max iterations: {}", maxIterations);
        thinkingTrace.add("Reached maximum iterations (" + maxIterations + ")");

        return new AgentExecutionResult(
            "Agent reached maximum iterations without completing the task.",
            toolCallHistory,
            thinkingTrace,
            maxIterations,
            totalInputTokens,
            totalOutputTokens,
            false,
            "Max iterations reached"
        );
    }

    private AgentNodeTool.ToolResult executeToolCall(AiToolCall toolCall, NodeExecutionContext context) {
        try {
            Optional<AgentNodeTool> toolOpt = toolRegistry.getTool(toolCall.getName());
            if (toolOpt.isEmpty()) {
                return AgentNodeTool.ToolResult.failure("Unknown tool: " + toolCall.getName());
            }

            AgentNodeTool tool = toolOpt.get();

            // 解析參數
            Map<String, Object> arguments;
            try {
                arguments = objectMapper.readValue(
                    toolCall.getArguments(),
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                return AgentNodeTool.ToolResult.failure("Invalid tool arguments: " + e.getMessage());
            }

            // 執行工具
            AgentNodeTool.ToolExecutionContext toolContext = new AgentNodeTool.ToolExecutionContext(
                context.getUserId() != null ? context.getUserId().toString() : null,
                context.getFlowId() != null ? context.getFlowId().toString() : null,
                context.getExecutionId() != null ? context.getExecutionId().toString() : null,
                context.getGlobalContext() != null ? context.getGlobalContext() : Map.of()
            );

            CompletableFuture<AgentNodeTool.ToolResult> future = tool.execute(arguments, toolContext);
            return future.get();  // 同步等待

        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage());
            return AgentNodeTool.ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String getDefaultSystemPrompt() {
        return """
            You are an intelligent AI assistant with access to tools.

            When given a task:
            1. Analyze what needs to be done
            2. Use available tools when necessary
            3. Process tool results and continue reasoning
            4. Provide a clear, helpful final response

            Be efficient with tool usage - only call tools when necessary.
            If you can answer directly from your knowledge, do so.
            """;
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        Sinks.Many<StreamChunk> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 在背景執行 Agent 迴圈
        CompletableFuture.runAsync(() -> {
            try {
                String providerId = getStringConfig(context, "provider", "openai");
                String model = getStringConfig(context, "model", "gpt-4o");
                String systemPrompt = getStringConfig(context, "systemPrompt", getDefaultSystemPrompt());
                String task = getStringConfig(context, "task", "");
                @SuppressWarnings("unchecked")
                List<String> enabledTools = (List<String>) context.getConfig("tools", List.of("http_request", "web_search"));
                int maxIterations = getIntConfig(context, "maxIterations", DEFAULT_MAX_ITERATIONS);
                double temperature = getDoubleConfig(context, "temperature", 0.3);

                AiProvider provider = resolveProvider(providerId);
                Map<String, Object> credential = resolveCredential(context);
                AiProviderSettings settings = buildProviderSettings(credential, providerId);

                // 執行串流版本的 Agent 迴圈
                executeAgentLoopStreaming(
                    sink, provider, settings, model, systemPrompt, task,
                    enabledTools, maxIterations, temperature, context
                );

            } catch (Exception e) {
                log.error("AI Agent stream error: {}", e.getMessage());
                sink.tryEmitNext(StreamChunk.error(e.getMessage()));
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    private void executeAgentLoopStreaming(
        Sinks.Many<StreamChunk> sink,
        AiProvider provider,
        AiProviderSettings settings,
        String model,
        String systemPrompt,
        String task,
        List<String> enabledTools,
        int maxIterations,
        double temperature,
        NodeExecutionContext context
    ) throws Exception {

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.user(task));
        List<Map<String, Object>> tools = toolRegistry.toOpenAITools(enabledTools);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            sink.tryEmitNext(StreamChunk.progress((iteration * 100) / maxIterations,
                "Iteration " + (iteration + 1) + "/" + maxIterations));

            AiChatRequest request = AiChatRequest.builder()
                .model(model)
                .messages(messages)
                .systemPrompt(systemPrompt)
                .temperature(temperature)
                .tools(tools)
                .build();

            // 使用串流呼叫 AI
            StringBuilder responseContent = new StringBuilder();
            List<AiToolCall> toolCalls = new ArrayList<>();

            provider.chatStream(request, settings)
                .doOnNext(chunk -> {
                    if (chunk.getDelta() != null && !chunk.getDelta().isEmpty()) {
                        responseContent.append(chunk.getDelta());
                        sink.tryEmitNext(StreamChunk.text(chunk.getDelta()));
                    }
                    if (chunk.getToolCalls() != null) {
                        toolCalls.addAll(chunk.getToolCalls());
                    }
                })
                .blockLast();

            if (toolCalls.isEmpty()) {
                // 完成
                sink.tryEmitNext(StreamChunk.done(Map.of(
                    "iterations", iteration + 1,
                    "completed", true
                )));
                return;
            }

            // 處理工具調用
            messages.add(AiMessage.assistant(responseContent.toString(), toolCalls));

            for (AiToolCall toolCall : toolCalls) {
                sink.tryEmitNext(StreamChunk.toolCall(toolCall.getId(), toolCall.getName(), toolCall.getArguments()));

                AgentNodeTool.ToolResult toolResult = executeToolCall(toolCall, context);
                String resultContent = toolResult.success() ? toolResult.output() : "Error: " + toolResult.error();

                sink.tryEmitNext(StreamChunk.toolResult(toolCall.getId(), truncate(resultContent, 500)));
                messages.add(AiMessage.toolResult(toolCall.getId(), resultContent));
            }
        }

        sink.tryEmitNext(StreamChunk.error("Max iterations reached"));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "task", "type", "string", "required", true,
                       "description", "The task for the agent to accomplish")
            ),
            "outputs", List.of(
                Map.of("name", "response", "type", "string",
                       "description", "Agent's final response"),
                Map.of("name", "toolCalls", "type", "array",
                       "description", "History of tool calls made"),
                Map.of("name", "iterations", "type", "number",
                       "description", "Number of iterations taken"),
                Map.of("name", "completed", "type", "boolean",
                       "description", "Whether the task was completed")
            )
        );
    }

    /**
     * Agent 執行結果
     */
    private record AgentExecutionResult(
        String response,
        List<Map<String, Object>> toolCallHistory,
        List<String> thinkingTrace,
        int iterations,
        int inputTokens,
        int outputTokens,
        boolean completed,
        String error
    ) {
        Map<String, Object> toOutputMap() {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("response", response);
            output.put("toolCalls", toolCallHistory);
            output.put("trace", thinkingTrace);
            output.put("iterations", iterations);
            output.put("completed", completed);
            output.put("usage", Map.of(
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "totalTokens", inputTokens + outputTokens
            ));
            if (error != null) {
                output.put("error", error);
            }
            return output;
        }
    }
}
