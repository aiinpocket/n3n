package com.aiinpocket.n3n.ai.provider;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.ai.service.AiProviderService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AssistantAiClientTest extends BaseServiceTest {

    @Mock
    private AiProviderService aiProviderService;

    @Mock
    private AiProviderFactory providerFactory;

    @Mock
    private AiProviderConfigRepository configRepository;

    @Mock
    private AiProvider openAiProvider;

    @Mock
    private AiProvider claudeProvider;

    @InjectMocks
    private AssistantAiClient client;

    private UUID userId;
    private AiProviderConfig primaryConfig;
    private AiProviderConfig fallbackConfig;
    private AiProviderSettings settings;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        primaryConfig = AiProviderConfig.builder()
            .id(UUID.randomUUID())
            .ownerId(UUID.randomUUID())
            .provider("openai")
            .name("Platform OpenAI")
            .defaultModel("gpt-4o")
            .isShared(true)
            .isActive(true)
            .build();
        fallbackConfig = AiProviderConfig.builder()
            .id(UUID.randomUUID())
            .ownerId(UUID.randomUUID())
            .provider("claude")
            .name("Platform Claude")
            .defaultModel("claude-sonnet-4-5")
            .isShared(true)
            .isActive(true)
            .build();
        settings = AiProviderSettings.builder().apiKey("sk-test").build();
    }

    private AiResponse response(String content) {
        return AiResponse.builder().content(content).build();
    }

    // ==================== isAvailable / resolveActiveModel ====================

    @Test
    @DisplayName("isAvailable is true when a config can be resolved")
    void isAvailable_configured_true() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));

        assertThat(client.isAvailable(userId)).isTrue();
    }

    @Test
    @DisplayName("isAvailable is false when no config is resolved")
    void isAvailable_notConfigured_false() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.empty());

        assertThat(client.isAvailable(userId)).isFalse();
    }

    @Test
    @DisplayName("isAvailable is false when resolution throws")
    void isAvailable_resolutionFails_false() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenThrow(new RuntimeException("db down"));

        assertThat(client.isAvailable(userId)).isFalse();
    }

    @Test
    @DisplayName("resolveActiveModel returns the resolved config's default model")
    void resolveActiveModel_returnsDefaultModel() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));

        assertThat(client.resolveActiveModel(userId)).contains("gpt-4o");
    }

    @Test
    @DisplayName("resolveActiveModel is empty when nothing is configured")
    void resolveActiveModel_notConfigured_empty() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.empty());

        assertThat(client.resolveActiveModel(userId)).isEmpty();
    }

    // ==================== chat ====================

    @Test
    @DisplayName("chat throws a clear error when no provider is configured")
    void chat_notConfigured_throws() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.chat("hi", null, userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No AI provider configured");
    }

    @Test
    @DisplayName("chat sends model, prompts and parameters from the resolved config")
    void chat_happyPath_buildsRequestFromConfig() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));
        when(aiProviderService.buildSettingsFor(primaryConfig)).thenReturn(settings);
        when(providerFactory.getProvider("openai")).thenReturn(openAiProvider);
        when(openAiProvider.chat(any(), eq(settings)))
            .thenReturn(CompletableFuture.completedFuture(response("hello back")));

        String result = client.chat("hi there", "you are helpful", 1234, 0.4, userId);

        assertThat(result).isEqualTo("hello back");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(openAiProvider).chat(captor.capture(), eq(settings));
        AiChatRequest request = captor.getValue();
        assertThat(request.getModel()).isEqualTo("gpt-4o");
        assertThat(request.getSystemPrompt()).isEqualTo("you are helpful");
        assertThat(request.getMaxTokens()).isEqualTo(1234);
        assertThat(request.getTemperature()).isEqualTo(0.4);
        assertThat(request.getMessages()).hasSize(1);
    }

    @Test
    @DisplayName("when the primary provider fails, another shared active config is tried once")
    void chat_primaryFails_failsOverOnce() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));
        when(aiProviderService.buildSettingsFor(primaryConfig)).thenReturn(settings);
        when(aiProviderService.buildSettingsFor(fallbackConfig)).thenReturn(settings);
        when(providerFactory.getProvider("openai")).thenReturn(openAiProvider);
        when(providerFactory.getProvider("claude")).thenReturn(claudeProvider);
        when(openAiProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("rate limited")));
        when(configRepository.findByIsSharedTrueAndIsActiveTrue())
            .thenReturn(List.of(primaryConfig, fallbackConfig));
        when(claudeProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(response("fallback answer")));

        String result = client.chat("hi", null, userId);

        assertThat(result).isEqualTo("fallback answer");
        verify(claudeProvider).chat(any(), any());
    }

    @Test
    @DisplayName("when primary fails and no other shared config exists, the failure surfaces")
    void chat_primaryFails_noFallback_throws() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));
        when(aiProviderService.buildSettingsFor(primaryConfig)).thenReturn(settings);
        when(providerFactory.getProvider("openai")).thenReturn(openAiProvider);
        when(openAiProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        when(configRepository.findByIsSharedTrueAndIsActiveTrue())
            .thenReturn(List.of(primaryConfig));

        assertThatThrownBy(() -> client.chat("hi", null, userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI provider request failed");
    }

    @Test
    @DisplayName("when primary and fallback both fail, a clear all-failed error is thrown")
    void chat_bothFail_throwsAllFailed() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));
        when(aiProviderService.buildSettingsFor(any())).thenReturn(settings);
        when(providerFactory.getProvider("openai")).thenReturn(openAiProvider);
        when(providerFactory.getProvider("claude")).thenReturn(claudeProvider);
        when(openAiProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));
        when(claudeProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("also down")));
        when(configRepository.findByIsSharedTrueAndIsActiveTrue())
            .thenReturn(List.of(primaryConfig, fallbackConfig));

        assertThatThrownBy(() -> client.chat("hi", null, userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("All AI providers failed");
    }

    @Test
    @DisplayName("an empty provider response triggers failover")
    void chat_emptyResponse_failsOver() {
        when(aiProviderService.resolveConfigForExecution(userId)).thenReturn(Optional.of(primaryConfig));
        when(aiProviderService.buildSettingsFor(any())).thenReturn(settings);
        when(providerFactory.getProvider("openai")).thenReturn(openAiProvider);
        when(providerFactory.getProvider("claude")).thenReturn(claudeProvider);
        when(openAiProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(response(null)));
        when(claudeProvider.chat(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(response("recovered")));
        when(configRepository.findByIsSharedTrueAndIsActiveTrue())
            .thenReturn(List.of(primaryConfig, fallbackConfig));

        assertThat(client.chat("hi", null, userId)).isEqualTo("recovered");
    }
}
