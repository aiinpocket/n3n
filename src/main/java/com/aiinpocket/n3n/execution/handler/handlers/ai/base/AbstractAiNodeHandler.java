package com.aiinpocket.n3n.execution.handler.handlers.ai.base;

import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.ai.service.AiTokenUsageService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for AI nodes.
 * Provides common functionality for AI provider selection, streaming support, and token metering.
 *
 * All AI-related nodes (Chat, Agent, Chain, Memory, etc.) should extend this class.
 */
@Slf4j
public abstract class AbstractAiNodeHandler extends MultiOperationNodeHandler
    implements StreamingNodeHandler {

    protected final AiProviderFactory providerFactory;

    @Autowired(required = false)
    protected AiTokenUsageService aiTokenUsageService;

    protected AbstractAiNodeHandler(AiProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    /**
     * Resolve AI Provider based on configuration
     */
    protected AiProvider resolveProvider(NodeExecutionContext context) {
        String providerId = getStringConfig(context, "provider", "openai");
        return providerFactory.getProvider(providerId);
    }

    /**
     * Resolve AI Provider by provider ID
     */
    protected AiProvider resolveProvider(String providerId) {
        return providerFactory.getProvider(providerId);
    }

    /**
     * Resolve API key from credential or environment variable
     *
     * @param credential credential data
     * @param envVarName environment variable name (e.g., OPENAI_API_KEY)
     * @return API key
     */
    protected String resolveApiKey(Map<String, Object> credential, String envVarName) {
        String apiKey = getCredentialValue(credential, "apiKey");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv(envVarName);
        }
        return apiKey;
    }

    /**
     * Get the corresponding environment variable name for a provider ID
     */
    protected String getEnvVarName(String providerId) {
        return switch (providerId.toLowerCase()) {
            case "openai" -> "OPENAI_API_KEY";
            case "claude", "anthropic" -> "ANTHROPIC_API_KEY";
            case "gemini", "google" -> "GOOGLE_API_KEY";
            default -> providerId.toUpperCase() + "_API_KEY";
        };
    }

    /**
     * Build AI Provider settings
     */
    protected AiProviderSettings buildProviderSettings(
        Map<String, Object> credential,
        String providerId
    ) {
        String apiKey = resolveApiKey(credential, getEnvVarName(providerId));
        String baseUrl = getCredentialValue(credential, "baseUrl");

        AiProviderSettings.AiProviderSettingsBuilder builder = AiProviderSettings.builder();

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.apiKey(apiKey);
        }
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    /**
     * Record token usage (for billing/quota management)
     */
    protected void recordTokenUsage(
        UUID userId,
        String provider,
        String model,
        int inputTokens,
        int outputTokens
    ) {
        recordTokenUsage(userId, provider, model, inputTokens, outputTokens, null, null);
    }

    /**
     * Record token usage (with execution context information)
     */
    protected void recordTokenUsage(
        UUID userId,
        String provider,
        String model,
        int inputTokens,
        int outputTokens,
        UUID executionId,
        String nodeId
    ) {
        log.debug("Token usage - user: {}, provider: {}, model: {}, input: {}, output: {}",
            userId, provider, model, inputTokens, outputTokens);
        if (aiTokenUsageService != null) {
            aiTokenUsageService.record(userId, provider, model, inputTokens, outputTokens, executionId, nodeId);
        }
    }

    /**
     * Build an AiChatRequest
     */
    protected AiChatRequest buildChatRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxTokens,
        Map<String, Object> providerOptions
    ) {
        AiChatRequest.AiChatRequestBuilder builder = AiChatRequest.builder()
            .model(model)
            .temperature(temperature)
            .maxTokens(maxTokens);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            builder.systemPrompt(systemPrompt);
        }

        if (userPrompt != null) {
            builder.messages(java.util.List.of(AiMessage.user(userPrompt)));
        }

        if (providerOptions != null && !providerOptions.isEmpty()) {
            builder.providerOptions(providerOptions);
        }

        return builder.build();
    }

    // ===== Abstract methods =====

    /**
     * Whether streaming output is supported
     */
    @Override
    public abstract boolean supportsStreaming();

    /**
     * Streaming execution (subclasses must implement)
     */
    @Override
    public abstract Flux<StreamChunk> executeStream(NodeExecutionContext context);
}
