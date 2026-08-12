package com.aiinpocket.n3n.execution.handler.handlers.ai.media;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI 語音合成節點（Text-to-Speech）。
 *
 * 支援 OpenAI TTS 與 ElevenLabs 兩種供應商，輸出 base64 編碼的 MP3 音訊，
 * 可串接後續節點（如上傳、寄信、影片合成素材）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiTtsNodeHandler extends MultiOperationNodeHandler {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final ArtifactService artifactService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    @Override
    public String getType() {
        return "aiTts";
    }

    @Override
    public String getDisplayName() {
        return "AI Text to Speech";
    }

    @Override
    public String getDescription() {
        return "Convert text to natural speech audio using OpenAI TTS or ElevenLabs. "
                + "Saves the MP3 into your artifact library and outputs a download URL.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "sound";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("speech", ResourceDef.of("speech", "Speech", "Text-to-speech synthesis"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("speech", List.of(
            OperationDef.create("openaiSpeech", "OpenAI TTS")
                .description("Generate speech with OpenAI text-to-speech models")
                .fields(List.of(
                    FieldDef.credential("credentialId", "OpenAI Credential")
                        .withDescription("Credential of type OpenAI (apiKey)")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withPlaceholder("Text to convert to speech...")
                        .required(),
                    FieldDef.select("model", "Model", List.of("gpt-4o-mini-tts", "tts-1", "tts-1-hd"))
                        .withDefault("gpt-4o-mini-tts"),
                    FieldDef.select("voice", "Voice", List.of(
                            "alloy", "ash", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer"))
                        .withDefault("alloy"),
                    FieldDef.textarea("instructions", "Voice Instructions")
                        .withDescription("Optional tone/style instructions (gpt-4o-mini-tts only)"),
                    FieldDef.bool("includeBase64", "Include Base64 Audio in Output")
                        .withDefault(false)
                        .withDescription("Also include base64-encoded audio in node output (increases execution state size)")
                ))
                .outputDescription("Returns 'artifactId', 'downloadUrl', 'mimeType' and 'sizeBytes'")
                .build(),
            OperationDef.create("elevenLabsSpeech", "ElevenLabs TTS")
                .description("Generate speech with ElevenLabs voices")
                .fields(List.of(
                    FieldDef.credential("credentialId", "ElevenLabs Credential")
                        .withDescription("Credential of type ElevenLabs (apiKey)")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withPlaceholder("Text to convert to speech...")
                        .required(),
                    FieldDef.string("voiceId", "Voice ID")
                        .withDefault("21m00Tcm4TlvDq8ikWAM")
                        .withDescription("ElevenLabs voice ID (default: Rachel)")
                        .required(),
                    FieldDef.select("modelId", "Model", List.of(
                            "eleven_multilingual_v2", "eleven_turbo_v2_5", "eleven_flash_v2_5"))
                        .withDefault("eleven_multilingual_v2"),
                    FieldDef.bool("includeBase64", "Include Base64 Audio in Output")
                        .withDefault(false)
                        .withDescription("Also include base64-encoded audio in node output (increases execution state size)")
                ))
                .outputDescription("Returns 'artifactId', 'downloadUrl', 'mimeType' and 'sizeBytes'")
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

        try {
            return switch (operation) {
                case "openaiSpeech" -> openaiSpeech(context, credential, params);
                case "elevenLabsSpeech" -> elevenLabsSpeech(context, credential, params);
                default -> NodeExecutionResult.failure("Unknown operation: " + resource + "." + operation);
            };
        } catch (Exception e) {
            log.error("TTS generation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Text-to-speech generation failed");
        }
    }

    private NodeExecutionResult openaiSpeech(NodeExecutionContext context, Map<String, Object> credential, Map<String, Object> params)
            throws IOException {
        String apiKey = getCredentialValue(credential, "apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return NodeExecutionResult.failure("OpenAI API key is missing");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getParam(params, "model", "gpt-4o-mini-tts"));
        body.put("voice", getParam(params, "voice", "alloy"));
        body.put("input", getRequiredParam(params, "text"));
        body.put("response_format", "mp3");
        String instructions = getParam(params, "instructions", "");
        if (!instructions.isBlank()) {
            body.put("instructions", instructions);
        }

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        return executeAudioRequest(context, params, request, "OpenAI TTS");
    }

    private NodeExecutionResult elevenLabsSpeech(NodeExecutionContext context, Map<String, Object> credential, Map<String, Object> params)
            throws IOException {
        String apiKey = getCredentialValue(credential, "apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("ELEVENLABS_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return NodeExecutionResult.failure("ElevenLabs API key is missing");
        }

        String voiceId = getRequiredParam(params, "voiceId");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", getRequiredParam(params, "text"));
        body.put("model_id", getParam(params, "modelId", "eleven_multilingual_v2"));

        Request request = new Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId)
                .header("xi-api-key", apiKey)
                .header("Accept", "audio/mpeg")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        return executeAudioRequest(context, params, request, "ElevenLabs TTS");
    }

    private NodeExecutionResult executeAudioRequest(
            NodeExecutionContext context, Map<String, Object> params, Request request, String label)
            throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.warn("{} failed with status {}: {}", label, response.code(), errorBody);
                return NodeExecutionResult.failure(label + " failed (HTTP " + response.code() + ")");
            }

            byte[] audio = response.body().bytes();
            boolean includeBase64 = getBoolParam(params, "includeBase64", false);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("mimeType", "audio/mpeg");
            output.put("sizeBytes", audio.length);

            // 存入使用者 artifact 庫；失敗時退回輸出 base64，避免資料遺失
            try {
                ArtifactMeta meta = ArtifactMeta.builder()
                        .filename("tts-" + System.currentTimeMillis() + ".mp3")
                        .mimeType("audio/mpeg")
                        .flowId(context.getFlowId())
                        .executionId(context.getExecutionId())
                        .nodeId(context.getNodeId())
                        .sourceNodeType(getType())
                        .build();
                Artifact artifact = artifactService.save(context.getUserId(), meta, audio);
                output.put("artifactId", artifact.getId().toString());
                output.put("downloadUrl", ArtifactService.downloadUrl(artifact.getId()));
            } catch (Exception e) {
                log.warn("Failed to save TTS artifact: {}", e.getMessage());
                includeBase64 = true;
            }

            if (includeBase64) {
                output.put("audioBase64", Base64.getEncoder().encodeToString(audio));
            }
            return NodeExecutionResult.success(output);
        }
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "text", "type", "string", "required", false,
                       "description", "Text to synthesize (can also come from node config)")
            ),
            "outputs", List.of(
                Map.of("name", "artifactId", "type", "string",
                       "description", "Saved artifact ID"),
                Map.of("name", "downloadUrl", "type", "string",
                       "description", "Relative download URL of the audio artifact"),
                Map.of("name", "audioBase64", "type", "string",
                       "description", "Base64-encoded MP3 audio (only when 'includeBase64' is enabled)"),
                Map.of("name", "mimeType", "type", "string",
                       "description", "Audio MIME type"),
                Map.of("name", "sizeBytes", "type", "integer",
                       "description", "Audio size in bytes")
            )
        );
    }
}
