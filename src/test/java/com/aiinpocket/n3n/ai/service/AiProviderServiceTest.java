package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.response.AiProviderConfigResponse;
import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.provider.AiProviderFactory;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.service.CredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiProviderServiceTest extends BaseServiceTest {

    @Mock
    private AiProviderConfigRepository configRepository;

    @Mock
    private AiProviderFactory providerFactory;

    @Mock
    private CredentialService credentialService;

    @InjectMocks
    private AiProviderService aiProviderService;

    private UUID userId;
    private UUID configId;
    private UUID credentialId;
    private AiProviderConfig openaiConfig;
    private AiProviderConfig anthropicConfig;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        configId = UUID.randomUUID();
        credentialId = UUID.randomUUID();

        openaiConfig = AiProviderConfig.builder()
                .id(configId)
                .ownerId(userId)
                .provider("openai")
                .name("My OpenAI")
                .description("OpenAI provider config")
                .credentialId(credentialId)
                .baseUrl("https://api.openai.com/v1")
                .defaultModel("gpt-4")
                .settings(Map.of("temperature", 0.7))
                .isActive(true)
                .isDefault(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        anthropicConfig = AiProviderConfig.builder()
                .id(UUID.randomUUID())
                .ownerId(userId)
                .provider("anthropic")
                .name("My Anthropic")
                .description("Anthropic provider config")
                .credentialId(UUID.randomUUID())
                .baseUrl("https://api.anthropic.com/v1")
                .defaultModel("claude-3-opus")
                .settings(Map.of())
                .isActive(true)
                .isDefault(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("listUserConfigs")
    class ListUserConfigs {

        @Test
        @DisplayName("should return list of active configs for user")
        void listUserConfigs_returnsList() {
            when(configRepository.findByOwnerIdAndIsActiveTrue(userId))
                    .thenReturn(List.of(openaiConfig, anthropicConfig));

            List<AiProviderConfigResponse> result = aiProviderService.listUserConfigs(userId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getProvider()).isEqualTo("openai");
            assertThat(result.get(0).getName()).isEqualTo("My OpenAI");
            assertThat(result.get(0).getIsDefault()).isTrue();
            assertThat(result.get(0).getHasApiKey()).isTrue();
            assertThat(result.get(1).getProvider()).isEqualTo("anthropic");
            assertThat(result.get(1).getName()).isEqualTo("My Anthropic");
            assertThat(result.get(1).getIsDefault()).isFalse();
            verify(configRepository).findByOwnerIdAndIsActiveTrue(userId);
        }

        @Test
        @DisplayName("should return empty list when user has no configs")
        void listUserConfigs_empty_returnsEmptyList() {
            when(configRepository.findByOwnerIdAndIsActiveTrue(userId))
                    .thenReturn(List.of());

            List<AiProviderConfigResponse> result = aiProviderService.listUserConfigs(userId);

            assertThat(result).isEmpty();
            verify(configRepository).findByOwnerIdAndIsActiveTrue(userId);
        }

        @Test
        @DisplayName("should indicate hasApiKey based on credentialId presence")
        void listUserConfigs_hasApiKeyReflectsCredentialId() {
            AiProviderConfig noKeyConfig = AiProviderConfig.builder()
                    .id(UUID.randomUUID())
                    .ownerId(userId)
                    .provider("ollama")
                    .name("Local Ollama")
                    .credentialId(null)
                    .isActive(true)
                    .isDefault(false)
                    .settings(Map.of())
                    .build();

            when(configRepository.findByOwnerIdAndIsActiveTrue(userId))
                    .thenReturn(List.of(openaiConfig, noKeyConfig));

            List<AiProviderConfigResponse> result = aiProviderService.listUserConfigs(userId);

            assertThat(result.get(0).getHasApiKey()).isTrue();
            assertThat(result.get(1).getHasApiKey()).isFalse();
        }
    }

    @Nested
    @DisplayName("getDefaultConfig")
    class GetDefaultConfig {

        @Test
        @DisplayName("should return default config when exists")
        void getDefaultConfig_returnsConfig() {
            when(configRepository.findByOwnerIdAndIsDefaultTrue(userId))
                    .thenReturn(Optional.of(openaiConfig));

            AiProviderConfigResponse result = aiProviderService.getDefaultConfig(userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(configId);
            assertThat(result.getProvider()).isEqualTo("openai");
            assertThat(result.getIsDefault()).isTrue();
            verify(configRepository).findByOwnerIdAndIsDefaultTrue(userId);
        }

        @Test
        @DisplayName("should return null when no default config exists")
        void getDefaultConfig_noDefault_returnsNull() {
            when(configRepository.findByOwnerIdAndIsDefaultTrue(userId))
                    .thenReturn(Optional.empty());

            AiProviderConfigResponse result = aiProviderService.getDefaultConfig(userId);

            assertThat(result).isNull();
            verify(configRepository).findByOwnerIdAndIsDefaultTrue(userId);
        }
    }

    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Test
        @DisplayName("should return config when found")
        void getConfig_success() {
            when(configRepository.findByIdAndOwnerId(configId, userId))
                    .thenReturn(Optional.of(openaiConfig));

            AiProviderConfigResponse result = aiProviderService.getConfig(configId, userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(configId);
            assertThat(result.getProvider()).isEqualTo("openai");
            assertThat(result.getName()).isEqualTo("My OpenAI");
            assertThat(result.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
            assertThat(result.getDefaultModel()).isEqualTo("gpt-4");
            assertThat(result.getSettings()).containsEntry("temperature", 0.7);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found")
        void getConfig_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(configRepository.findByIdAndOwnerId(nonExistingId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.getConfig(nonExistingId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AI Provider config not found");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config belongs to different user")
        void getConfig_differentUser_throwsException() {
            UUID otherUserId = UUID.randomUUID();
            when(configRepository.findByIdAndOwnerId(configId, otherUserId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.getConfig(configId, otherUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AI Provider config not found");
        }
    }

    @Nested
    @DisplayName("deleteConfig")
    class DeleteConfig {

        @Test
        @DisplayName("should delete config and associated credential")
        void deleteConfig_withCredential_success() {
            when(configRepository.findByIdAndOwnerId(configId, userId))
                    .thenReturn(Optional.of(openaiConfig));

            aiProviderService.deleteConfig(configId, userId);

            verify(credentialService).deleteCredential(credentialId, userId);
            verify(configRepository).delete(openaiConfig);
        }

        @Test
        @DisplayName("should delete config without credential")
        void deleteConfig_withoutCredential_success() {
            AiProviderConfig noCredConfig = AiProviderConfig.builder()
                    .id(configId)
                    .ownerId(userId)
                    .provider("ollama")
                    .name("Local Ollama")
                    .credentialId(null)
                    .isActive(true)
                    .isDefault(false)
                    .settings(Map.of())
                    .build();

            when(configRepository.findByIdAndOwnerId(configId, userId))
                    .thenReturn(Optional.of(noCredConfig));

            aiProviderService.deleteConfig(configId, userId);

            verify(credentialService, never()).deleteCredential(any(), any());
            verify(configRepository).delete(noCredConfig);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found")
        void deleteConfig_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(configRepository.findByIdAndOwnerId(nonExistingId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.deleteConfig(nonExistingId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AI Provider config not found");

            verify(configRepository, never()).delete(any());
            verify(credentialService, never()).deleteCredential(any(), any());
        }

        @Test
        @DisplayName("should still delete config when credential deletion fails")
        void deleteConfig_credentialDeletionFails_stillDeletesConfig() {
            when(configRepository.findByIdAndOwnerId(configId, userId))
                    .thenReturn(Optional.of(openaiConfig));
            doThrow(new RuntimeException("Credential deletion failed"))
                    .when(credentialService).deleteCredential(credentialId, userId);

            aiProviderService.deleteConfig(configId, userId);

            verify(credentialService).deleteCredential(credentialId, userId);
            verify(configRepository).delete(openaiConfig);
        }
    }

    @Nested
    @DisplayName("setAsDefault")
    class SetAsDefault {

        @Test
        @DisplayName("should clear others and set config as default")
        void setAsDefault_success() {
            AiProviderConfig nonDefaultConfig = AiProviderConfig.builder()
                    .id(configId)
                    .ownerId(userId)
                    .provider("openai")
                    .name("My OpenAI")
                    .credentialId(credentialId)
                    .isActive(true)
                    .isDefault(false)
                    .settings(Map.of())
                    .build();

            when(configRepository.findByIdAndOwnerId(configId, userId))
                    .thenReturn(Optional.of(nonDefaultConfig));

            aiProviderService.setAsDefault(configId, userId);

            verify(configRepository).clearDefaultForUser(userId);
            assertThat(nonDefaultConfig.getIsDefault()).isTrue();
            verify(configRepository).save(nonDefaultConfig);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found")
        void setAsDefault_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(configRepository.findByIdAndOwnerId(nonExistingId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.setAsDefault(nonExistingId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AI Provider config not found");

            verify(configRepository, never()).clearDefaultForUser(any());
            verify(configRepository, never()).save(any());
        }

        @Test
        @DisplayName("should clear default even if config is already default")
        void setAsDefault_alreadyDefault_stillClearsAndSets() {
            when(configRepository.findByIdAndOwnerId(configId, userId))
                    .thenReturn(Optional.of(openaiConfig));

            aiProviderService.setAsDefault(configId, userId);

            verify(configRepository).clearDefaultForUser(userId);
            assertThat(openaiConfig.getIsDefault()).isTrue();
            verify(configRepository).save(openaiConfig);
        }
    }
}
