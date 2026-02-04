package com.aiinpocket.n3n.execution.handler.handlers.ai.chat;

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
 * AI Chat 節點處理器
 *
 * 功能:
 * - 多 Provider 支援 (OpenAI, Claude, Gemini, Ollama)
 * - 對話歷史管理
 * - 串流/非串流輸出
 * - 系統提示詞配置
 */
@Component
@Slf4j
public class AiChatNodeHandler extends AbstractAiNodeHandler {

    public AiChatNodeHandler(AiProviderFactory providerFactory) {
        super(providerFactory);
    }

    @Override
    public String getType() {
        return "aiChat";
    }

    @Override
    public String getDisplayName() {
        return "AI Chat";
    }

    @Override
    public String getDescription() {
        return "Send messages to AI models and receive responses. Supports multiple providers including OpenAI, Claude, Gemini, and Ollama.";
    }

    @Override
    public String getIcon() {
        return "message";
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("chat", ResourceDef.of("chat", "Chat", "Chat with AI models"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("chat", List.of(
            OperationDef.create("sendMessage", "Send Message")
                .description("Send a message to an AI model and receive a response")
                .fields(List.of(
                    FieldDef.select("provider", "Provider",
                            List.of("openai", "claude", "gemini", "ollama"))
                        .withDefault("openai")
                        .withDescription("AI provider to use")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            // OpenAI
                            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo",
                            // Claude
                            "claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307",
                            // Gemini
                            "gemini-1.5-pro", "gemini-1.5-flash",
                            // Ollama
                            "llama3.1", "llama3.2", "mistral", "codellama"
                        ))
                        .withDefault("gpt-4o")
                        .withDescription("Model to use")
                        .required(),
                    FieldDef.textarea("systemPrompt", "System Prompt")
                        .withDescription("System instructions for the AI")
                        .withPlaceholder("You are a helpful assistant..."),
                    FieldDef.textarea("prompt", "Message")
                        .withDescription("The message to send to the AI")
                        .withPlaceholder("Enter your message...")
                        .required(),
                    FieldDef.bool("includeHistory", "Include Conversation History")
                        .withDefault(false)
                        .withDescription("Include previous messages from input data"),
                    FieldDef.number("temperature", "Temperature")
                        .withDefault(0.7)
                        .withRange(0.0, 2.0)
                        .withDescription("Controls randomness (0=deterministic, 2=creative)"),
                    FieldDef.integer("maxTokens", "Max Tokens")
                        .withDefault(4096)
                        .withRange(1, 200000)
                        .withDescription("Maximum tokens in the response")
                ))
                .outputDescription("Returns AI response in 'content' field, with 'history' and 'usage'")
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
        if (!"chat".equals(resource) || !"sendMessage".equals(operation)) {
            return NodeExecutionResult.failure("Unknown operation: " + resource + "." + operation);
        }

        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");
        String systemPrompt = getParam(params, "systemPrompt", "");
        String prompt = getRequiredParam(params, "prompt");
        double temperature = getDoubleParam(params, "temperature", 0.7);
        int maxTokens = getIntParam(params, "maxTokens", 4096);
        boolean includeHistory = getBoolParam(params, "includeHistory", false);

        try {
            // 取得 Provider
            AiProvider provider = resolveProvider(providerId);
            AiProviderSettings settings = buildProviderSettings(credential, providerId);

            // 建構訊息列表
            List<AiMessage> messages = buildMessages(context, systemPrompt, prompt, includeHistory);

            // 建構請求
            AiChatRequest request = AiChatRequest.builder()
                .model(model)
                .messages(messages)
                .systemPrompt(systemPrompt.isEmpty() ? null : systemPrompt)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            // 執行請求
            AiResponse response = provider.chat(request, settings).get();

            // 記錄 Token 使用量
            if (response.getUsage() != null) {
                recordTokenUsage(
                    context.getUserId(),
                    providerId,
                    model,
                    response.getUsage().getInputTokens(),
                    response.getUsage().getOutputTokens()
                );
            }

            // 建構輸出
            Map<String, Object> output = buildOutput(response, messages, prompt);

            log.info("AI Chat completed - provider: {}, model: {}, tokens: {}",
                providerId, model,
                response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");

            return NodeExecutionResult.success(output);

        } catch (Exception e) {
            log.error("AI Chat error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("AI Chat error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<AiMessage> buildMessages(
        NodeExecutionContext context,
        String systemPrompt,
        String prompt,
        boolean includeHistory
    ) {
        List<AiMessage> messages = new ArrayList<>();

        // 加入對話歷史
        if (includeHistory) {
            Object historyInput = context.getInput("history", null);
            if (historyInput instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        String role = (String) map.get("role");
                        String content = (String) map.get("content");
                        if (role != null && content != null) {
                            messages.add(AiMessage.builder()
                                .role(role)
                                .content(content)
                                .build());
                        }
                    }
                }
            }
        }

        // 加入使用者訊息
        messages.add(AiMessage.user(prompt));

        return messages;
    }

    private Map<String, Object> buildOutput(
        AiResponse response,
        List<AiMessage> messages,
        String prompt
    ) {
        Map<String, Object> output = new LinkedHashMap<>();

        // 主要回應內容
        output.put("content", response.getContent());
        output.put("model", response.getModel());
        output.put("finishReason", response.getStopReason());

        // Token 使用量
        if (response.getUsage() != null) {
            output.put("usage", Map.of(
                "inputTokens", response.getUsage().getInputTokens(),
                "outputTokens", response.getUsage().getOutputTokens(),
                "totalTokens", response.getUsage().getTotalTokens()
            ));
        }

        // 更新對話歷史
        List<Map<String, String>> updatedHistory = new ArrayList<>();
        for (AiMessage msg : messages) {
            updatedHistory.add(Map.of(
                "role", msg.getRole(),
                "content", msg.getContent()
            ));
        }
        updatedHistory.add(Map.of(
            "role", "assistant",
            "content", response.getContent()
        ));
        output.put("history", updatedHistory);

        return output;
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        String providerId = getStringConfig(context, "provider", "openai");
        String model = getStringConfig(context, "model", "gpt-4o");
        String systemPrompt = getStringConfig(context, "systemPrompt", "");
        String prompt = getStringConfig(context, "prompt", "");
        double temperature = getDoubleConfig(context, "temperature", 0.7);
        int maxTokens = getIntConfig(context, "maxTokens", 4096);
        boolean includeHistory = getBooleanConfig(context, "includeHistory", false);

        try {
            // 取得 Provider
            AiProvider provider = resolveProvider(providerId);
            Map<String, Object> credential = resolveCredential(context);
            AiProviderSettings settings = buildProviderSettings(credential, providerId);

            // 建構訊息列表
            List<AiMessage> messages = buildMessages(context, systemPrompt, prompt, includeHistory);

            // 建構請求
            AiChatRequest request = AiChatRequest.builder()
                .model(model)
                .messages(messages)
                .systemPrompt(systemPrompt.isEmpty() ? null : systemPrompt)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            // 執行串流請求
            return provider.chatStream(request, settings)
                .map(chunk -> {
                    if (chunk.isDone()) {
                        return StreamChunk.done(Map.of(
                            "model", model,
                            "finishReason", chunk.getStopReason() != null ? chunk.getStopReason() : "stop"
                        ));
                    }
                    return StreamChunk.text(chunk.getDelta());
                })
                .onErrorResume(e -> {
                    log.error("AI Chat stream error: {}", e.getMessage());
                    return Flux.just(StreamChunk.error(e.getMessage()));
                });

        } catch (Exception e) {
            log.error("AI Chat stream setup error: {}", e.getMessage());
            return Flux.just(StreamChunk.error(e.getMessage()));
        }
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "prompt", "type", "string", "required", true,
                       "description", "The message to send"),
                Map.of("name", "history", "type", "array", "required", false,
                       "description", "Previous conversation messages")
            ),
            "outputs", List.of(
                Map.of("name", "content", "type", "string",
                       "description", "AI response content"),
                Map.of("name", "history", "type", "array",
                       "description", "Updated conversation history"),
                Map.of("name", "usage", "type", "object",
                       "description", "Token usage statistics")
            )
        );
    }
}
