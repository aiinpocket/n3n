package com.aiinpocket.n3n.execution.handler.handlers.ai.media;

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
 * AI 視覺理解節點：以多模態模型分析圖片（描述、OCR、結構化擷取等）。
 *
 * 透過 OpenAI 相容的多模態訊息格式送出圖片，
 * 支援 OpenAI 與 OpenRouter（可用其上的 Claude / Gemini 視覺模型）。
 */
@Component
@Slf4j
public class AiVisionNodeHandler extends AbstractAiNodeHandler {

    public AiVisionNodeHandler(AiProviderFactory providerFactory) {
        super(providerFactory);
    }

    @Override
    public String getType() {
        return "aiVision";
    }

    @Override
    public String getDisplayName() {
        return "AI Vision";
    }

    @Override
    public String getDescription() {
        return "Analyze images with multimodal AI models: describe, extract text (OCR), or answer questions about an image.";
    }

    @Override
    public String getIcon() {
        return "eye";
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("vision", ResourceDef.of("vision", "Vision", "Image understanding"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("vision", List.of(
            OperationDef.create("analyzeImage", "Analyze Image")
                .description("Send an image with a prompt to a multimodal model")
                .fields(List.of(
                    FieldDef.credential("credentialId", "Credential")
                        .withDescription("API credential for the selected provider"),
                    FieldDef.select("provider", "Provider", List.of("openai", "openrouter"))
                        .withDefault("openai")
                        .withDescription("Provider with OpenAI-compatible multimodal message format")
                        .required(),
                    FieldDef.string("model", "Model")
                        .withDefault("gpt-4o")
                        .withPlaceholder("gpt-4o / anthropic/claude-sonnet-4.5 / google/gemini-2.5-flash")
                        .withDescription("Vision-capable model ID")
                        .required(),
                    FieldDef.string("imageUrl", "Image URL")
                        .withFormat("uri")
                        .withPlaceholder("https://... or data URL from a previous node")
                        .withDescription("Image to analyze")
                        .required(),
                    FieldDef.textarea("prompt", "Prompt")
                        .withDefault("Describe this image in detail.")
                        .withDescription("What to ask about the image")
                        .required(),
                    FieldDef.integer("maxTokens", "Max Tokens")
                        .withDefault(2048)
                        .withRange(1, 100000)
                ))
                .outputDescription("Returns analysis text in 'content' and token 'usage'")
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
            Map<String, Object> params) {

        String providerId = getParam(params, "provider", "openai");
        String model = getRequiredParam(params, "model");
        String imageUrl = getRequiredParam(params, "imageUrl");
        String prompt = getRequiredParam(params, "prompt");
        int maxTokens = getIntParam(params, "maxTokens", 2048);

        try {
            AiProvider provider = resolveProvider(providerId);
            AiProviderSettings settings = buildProviderSettings(credential, providerId);

            AiMessage message = AiMessage.builder()
                    .role("user")
                    .multiContent(List.of(
                            AiContent.text(prompt),
                            AiContent.imageUrl(imageUrl)))
                    .build();

            AiChatRequest request = AiChatRequest.builder()
                    .model(model)
                    .messages(List.of(message))
                    .maxTokens(maxTokens)
                    .build();

            AiResponse response = provider.chat(request, settings).get();

            if (response.getUsage() != null) {
                recordTokenUsage(
                        context.getUserId(), providerId, model,
                        response.getUsage().getInputTokens(),
                        response.getUsage().getOutputTokens(),
                        context.getExecutionId(), context.getNodeId());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("content", response.getContent());
            output.put("model", response.getModel());
            if (response.getUsage() != null) {
                output.put("usage", Map.of(
                        "inputTokens", response.getUsage().getInputTokens(),
                        "outputTokens", response.getUsage().getOutputTokens(),
                        "totalTokens", response.getUsage().getTotalTokens()));
            }
            return NodeExecutionResult.success(output);

        } catch (Exception e) {
            log.error("AI Vision error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("AI Vision analysis failed");
        }
    }

    @Override
    public Flux<StreamChunk> executeStream(NodeExecutionContext context) {
        return Flux.just(StreamChunk.error("AI Vision does not support streaming"));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "imageUrl", "type", "string", "required", false,
                       "description", "Image URL to analyze"),
                Map.of("name", "prompt", "type", "string", "required", false,
                       "description", "Question about the image")
            ),
            "outputs", List.of(
                Map.of("name", "content", "type", "string",
                       "description", "Analysis result"),
                Map.of("name", "usage", "type", "object",
                       "description", "Token usage statistics")
            )
        );
    }
}
