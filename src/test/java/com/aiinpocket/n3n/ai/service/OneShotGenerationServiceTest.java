package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.ChatStreamChunk;
import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.service.NodeProbeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OneShotGenerationServiceTest {

    @Mock
    private AssistantAiClient aiClient;

    @Mock
    private NodeProbeService nodeProbeService;

    @Mock
    private AiProviderService aiProviderService;

    @Mock
    private ArtifactService artifactService;

    private OneShotGenerationService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OneShotGenerationService(aiClient, nodeProbeService,
            aiProviderService, artifactService, new ObjectMapper());
    }

    @Test
    void detectReturnsNullWithoutMediaHints() {
        assertThat(service.detect("幫我看一下昨天的執行紀錄", userId)).isNull();
    }

    @Test
    void detectReturnsNullWhenFlowHintsPresent() {
        // 有「每天」等流程字眼即使提到圖片也不走一次性生成
        assertThat(service.detect("每天生成一張圖片寄給我", userId)).isNull();
    }

    @Test
    void detectUsesAiConfirmationForMediaRequests() {
        when(aiClient.chat(anyString(), anyString(), anyInt(), anyDouble(), any(), any()))
            .thenReturn("{\"oneShot\": true, \"kind\": \"image\", \"prompt\": \"a beautiful landscape\"}");

        OneShotGenerationService.OneShotRequest request =
            service.detect("幫我生成一張風景圖片", userId);

        assertThat(request).isNotNull();
        assertThat(request.kind()).isEqualTo("image");
        assertThat(request.prompt()).isEqualTo("a beautiful landscape");
    }

    @Test
    void detectReturnsNullWhenAiSaysNotOneShot() {
        when(aiClient.chat(anyString(), anyString(), anyInt(), anyDouble(), any(), any()))
            .thenReturn("{\"oneShot\": false, \"kind\": \"none\", \"prompt\": \"\"}");

        assertThat(service.detect("圖片節點要怎麼設定？", userId)).isNull();
    }

    @Test
    void generateStreamSavesArtifactsAndEmitsStructuredChunk() {
        when(aiProviderService.resolveSharedApiKey("fal")).thenReturn(Optional.of("key"));
        UUID artifactId = UUID.randomUUID();
        when(nodeProbeService.probe(eq(userId), eq("falAi"), anyString(), any(), any(), anyLong()))
            .thenReturn(new NodeProbeService.ProbeResult(true,
                Map.of("artifactIds", List.of(artifactId.toString())), null, 100, UUID.randomUUID()));
        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setFilename("image-1.png");
        artifact.setMimeType("image/png");
        when(artifactService.claimForAssistant(userId, artifactId)).thenReturn(artifact);

        List<ChatStreamChunk> chunks = service.generateStream(userId,
                new OneShotGenerationService.OneShotRequest("image", "a cat"), text -> {})
            .collectList().block(java.time.Duration.ofSeconds(10));

        assertThat(chunks).isNotNull();
        assertThat(chunks.stream().anyMatch(c -> "structured".equals(c.getType())
            && "artifact_generated".equals(c.getStructuredData().get("action")))).isTrue();
        assertThat(chunks.stream().anyMatch(c -> "done".equals(c.getType()))).isTrue();
    }

    @Test
    void generateStreamExplainsMissingKeyInPlainLanguage() {
        when(aiProviderService.resolveSharedApiKey("fal")).thenReturn(Optional.empty());

        List<ChatStreamChunk> chunks = service.generateStream(userId,
                new OneShotGenerationService.OneShotRequest("image", "a cat"), text -> {})
            .collectList().block(java.time.Duration.ofSeconds(10));

        assertThat(chunks).isNotNull();
        assertThat(chunks.stream().anyMatch(c -> "text".equals(c.getType())
            && c.getText().contains("金鑰"))).isTrue();
    }
}
