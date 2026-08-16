package com.aiinpocket.n3n.execution.handler.handlers.ai.chat;

import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.service.AiProviderService;
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
 * AI Chat Node Handler
 *
 * Features:
 * - Multi-provider support (OpenAI, Claude, Gemini, OpenRouter)
 * - Conversation history management
 * - Streaming/non-streaming output
 * - System prompt configuration
 */
@Component
@Slf4j
public class AiChatNodeHandler extends AbstractAiNodeHandler {

    private final AiProviderService aiProviderService;

    public AiChatNodeHandler(AiProviderFactory providerFactory, AiProviderService aiProviderService) {
        super(providerFactory);
        this.aiProviderService = aiProviderService;
    }

    /** aiChat 只有一種 resource/operation，AI 生成的節點缺這兩欄時直接套預設值 */
    @Override
    protected boolean applyDefaultResourceOperation() {
        return true;
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
        return "Send messages to AI models and receive responses. Supports multiple providers including OpenAI, Claude, Gemini, and OpenRouter.";
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
                            List.of("openai", "claude", "gemini", "openrouter"))
                        .withDefault("openai")
                        .withDescription("AI provider to use")
                        .required(),
                    FieldDef.select("model", "Model", List.of(
                            // OpenAI
                            "gpt-5", "gpt-5-mini", "gpt-4o", "gpt-4o-mini",
                            // Claude
                            "claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5",
                            // Gemini
                            "gemini-2.5-pro", "gemini-2.5-flash"
                        ))
                        .withDefault("gpt-4o")
                        .withDescription("Model to use (leave empty to use the platform default)"),
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
                .outputDescription("Returns AI response in 'content' (alias 'text'), with 'history' and 'usage'")
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
        String model = getParam(params, "model", "");
        String systemPrompt = getParam(params, "systemPrompt", "");
        // AI 生成的節點常把提示詞放在 userPrompt/message，一併接受（config 已完成表達式評估）
        String prompt = getParam(params, "prompt", "");
        if (prompt.isBlank()) {
            prompt = getStringConfig(context, "userPrompt", "");
        }
        if (prompt.isBlank()) {
            prompt = getStringConfig(context, "message", "");
        }
        if (prompt.isBlank()) {
            return NodeExecutionResult.failure(
                "Required parameter 'prompt' is missing｜請在節點設定填寫要給 AI 的訊息內容");
        }
        String unresolved = findUnresolvedExpression(prompt);
        if (unresolved != null) {
            // 提示詞裡還留著沒接上的表達式，代表要分析的資料根本沒進來。
            // 照樣送出去的話模型不會報錯，而是憑空生出一篇看似合理的內容，
            // 流程一路顯示成功、使用者打開產出才發現是廢話——寧可在這裡就停下來。
            return NodeExecutionResult.failure(
                "Prompt contains unresolved expression " + unresolved
                    + "｜要分析的資料沒有傳進來（" + unresolved + " 取不到值），"
                    + "請確認上一個步驟有正確輸出，或調整這個欄位要引用的來源");
        }
        double temperature = getDoubleParam(params, "temperature", 0.7);
        int maxTokens = getIntParam(params, "maxTokens", 4096);
        boolean includeHistory = getBoolParam(params, "includeHistory", false);

        try {
            AiTarget target = resolveAiTarget(providerId, model, credential, context);

            // Build message list
            List<AiMessage> messages = buildMessages(context, systemPrompt, prompt, includeHistory);

            // Build request
            AiChatRequest request = AiChatRequest.builder()
                .model(target.model())
                .messages(messages)
                .systemPrompt(systemPrompt.isEmpty() ? null : systemPrompt)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            // Execute request
            AiResponse response = target.provider().chat(request, target.settings()).get();

            // Record token usage
            if (response.getUsage() != null) {
                recordTokenUsage(
                    context.getUserId(),
                    target.providerId(),
                    target.model(),
                    response.getUsage().getInputTokens(),
                    response.getUsage().getOutputTokens()
                );
            }

            // Build output
            Map<String, Object> output = buildOutput(response, messages, prompt);

            log.info("AI Chat completed - provider: {}, model: {}, tokens: {}",
                target.providerId(), target.model(),
                response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");

            return NodeExecutionResult.success(output);

        } catch (Exception e) {
            log.error("AI Chat error: {}", e.getMessage(), e);
            String reason = e.getMessage() != null ? e.getMessage() : "未知錯誤";
            return NodeExecutionResult.failure("AI 對話失敗：" + reason);
        }
    }

    /** 解析後的執行目標：實際使用的供應商、模型與連線設定 */
    private record AiTarget(String providerId, AiProvider provider, String model, AiProviderSettings settings) {}

    /**
     * 已退役/易踩雷的舊模型名 → 現行等價模型。
     * AI 生成的流程常寫 gpt-4（8K 上下文），塞入真實資料就會 400；一律升級到現行同級模型。
     */
    private static final Map<String, String> LEGACY_MODEL_MAP = Map.ofEntries(
        Map.entry("gpt-4", "gpt-4o"),
        Map.entry("gpt-4-turbo", "gpt-4o"),
        Map.entry("gpt-4-0613", "gpt-4o"),
        Map.entry("gpt-3.5-turbo", "gpt-4o-mini"),
        Map.entry("claude-3-opus-20240229", "claude-opus-5"),
        Map.entry("claude-3-5-sonnet-20241022", "claude-sonnet-4-5"),
        Map.entry("claude-3-sonnet-20240229", "claude-sonnet-4-5"),
        Map.entry("claude-3-haiku-20240307", "claude-haiku-4-5"),
        Map.entry("gemini-1.5-pro", "gemini-2.5-pro"),
        Map.entry("gemini-1.5-flash", "gemini-2.5-flash"),
        Map.entry("gemini-pro", "gemini-2.5-pro")
    );

    private static String normalizeModel(String model) {
        String mapped = LEGACY_MODEL_MAP.get(model);
        if (mapped != null) {
            log.info("Normalizing legacy model '{}' to '{}'", model, mapped);
            return mapped;
        }
        return model;
    }

    /**
     * 決定實際要用哪個 AI 服務執行：
     * 1. 節點自己的憑證（或對應環境變數）→ 用節點指定的供應商與模型
     * 2. 平台共用 AI 設定中「同一供應商」的金鑰 → 沿用節點模型（沒填則用平台預設模型）
     * 3. 平台預設聊天供應商 → 連供應商與模型一起改用平台設定
     * 這樣 AI 生成的流程不需要使用者自備 API 金鑰就能直接執行。
     */
    private AiTarget resolveAiTarget(
        String providerId,
        String model,
        Map<String, Object> credential,
        NodeExecutionContext context
    ) {
        model = model.isBlank() ? model : normalizeModel(model);
        String apiKey = resolveApiKey(credential, getEnvVarName(providerId));
        if (apiKey != null && !apiKey.isEmpty()) {
            String effectiveModel = model.isBlank() ? "gpt-4o" : model;
            return new AiTarget(providerId, resolveProvider(providerId), effectiveModel,
                buildProviderSettings(credential, providerId));
        }

        Optional<AiProviderConfig> sameProvider = aiProviderService.resolveSharedConfigFor(providerId);
        if (sameProvider.isPresent()) {
            AiProviderConfig config = sameProvider.get();
            String effectiveModel = model.isBlank() ? config.getDefaultModel() : model;
            return new AiTarget(providerId, resolveProvider(providerId), effectiveModel,
                aiProviderService.buildSettingsFor(config));
        }

        Optional<AiProviderConfig> platformDefault =
            aiProviderService.resolveConfigForExecution(context.getUserId());
        if (platformDefault.isPresent()) {
            AiProviderConfig config = platformDefault.get();
            log.info("AI Chat falling back to platform provider '{}' (requested '{}' has no key)",
                config.getProvider(), providerId);
            return new AiTarget(config.getProvider(), resolveProvider(config.getProvider()),
                config.getDefaultModel(), aiProviderService.buildSettingsFor(config));
        }

        throw new IllegalStateException(
            "找不到可用的 AI 服務金鑰。請到「系統管理 → AI 設定」新增平台共用金鑰，或在節點掛上自己的 AI 憑證");
    }

    @SuppressWarnings("unchecked")
    private List<AiMessage> buildMessages(
        NodeExecutionContext context,
        String systemPrompt,
        String prompt,
        boolean includeHistory
    ) {
        List<AiMessage> messages = new ArrayList<>();

        // Add conversation history
        if (includeHistory) {
            Object historyInput = context.getInput("history", null);
            if (historyInput instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object roleObj = map.get("role");
                        Object contentObj = map.get("content");
                        String role = roleObj != null ? roleObj.toString() : null;
                        String content = contentObj != null ? contentObj.toString() : null;
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

        // Add user message
        messages.add(AiMessage.user(prompt));

        return messages;
    }

    private Map<String, Object> buildOutput(
        AiResponse response,
        List<AiMessage> messages,
        String prompt
    ) {
        Map<String, Object> output = new LinkedHashMap<>();

        // Main response content（text 為 n8n 風格別名，AI 生成的流程常引用 $json.text）
        output.put("content", response.getContent());
        output.put("text", response.getContent());
        output.put("model", response.getModel());
        output.put("finishReason", response.getStopReason());

        // Token usage
        if (response.getUsage() != null) {
            output.put("usage", Map.of(
                "inputTokens", response.getUsage().getInputTokens(),
                "outputTokens", response.getUsage().getOutputTokens(),
                "totalTokens", response.getUsage().getTotalTokens()
            ));
        }

        // Update conversation history
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
            Map<String, Object> credential = resolveCredential(context);
            AiTarget target = resolveAiTarget(providerId, model, credential, context);

            // Build message list
            List<AiMessage> messages = buildMessages(context, systemPrompt, prompt, includeHistory);

            // Build request
            AiChatRequest request = AiChatRequest.builder()
                .model(target.model())
                .messages(messages)
                .systemPrompt(systemPrompt.isEmpty() ? null : systemPrompt)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            // Execute streaming request
            return target.provider().chatStream(request, target.settings())
                .map(chunk -> {
                    if (chunk.isDone()) {
                        return StreamChunk.done(Map.of(
                            "model", target.model(),
                            "finishReason", chunk.getStopReason() != null ? chunk.getStopReason() : "stop"
                        ));
                    }
                    return StreamChunk.text(chunk.getDelta());
                })
                .onErrorResume(e -> {
                    log.error("AI Chat stream error: {}", e.getMessage());
                    return Flux.just(StreamChunk.error("AI 對話失敗：" + e.getMessage()));
                });

        } catch (Exception e) {
            log.error("AI Chat stream setup error: {}", e.getMessage());
            String reason = e.getMessage() != null ? e.getMessage() : "未知錯誤";
            return Flux.just(StreamChunk.error("AI 對話失敗：" + reason));
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

    /** 表達式引擎解析不出來時會把 {{ ... }} 原樣留下，這裡把第一個找出來。 */
    private static final java.util.regex.Pattern UNRESOLVED_EXPRESSION =
        java.util.regex.Pattern.compile("\\{\\{\\s*\\$[^}]*}}");

    /**
     * 找出提示詞裡沒被代換掉的表達式；沒有就回傳 null。
     *
     * <p>只認以 {@code $} 開頭的形式，避免把使用者真的想輸出的大括號文字誤判成錯誤。
     * 這個檢查放在 aiChat 而不是所有節點：到這裡時 config 的表達式已由執行引擎
     * 評估完畢，但有些節點（例如 setFields）是在自己內部才解析，統一攔會誤傷。
     */
    static String findUnresolvedExpression(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = UNRESOLVED_EXPRESSION.matcher(prompt);
        return matcher.find() ? matcher.group().trim() : null;
    }
}
