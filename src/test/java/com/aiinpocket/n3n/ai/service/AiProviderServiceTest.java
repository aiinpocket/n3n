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
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AI Provider 設定為平台共用（管理員統一管理）：
 * 管理操作以 isShared=true 的設定為範圍，userId 僅為操作者。
 */
class AiProviderServiceTest extends BaseServiceTest {

    @Mock
    private AiProviderConfigRepository configRepository;

    @Mock
    private AiProviderFactory providerFactory;

    @Mock
    private CredentialService credentialService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AiProviderService aiProviderService;

    private UUID adminId;
    private UUID configId;
    private UUID credentialId;
    private AiProviderConfig openaiConfig;
    private AiProviderConfig anthropicConfig;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        configId = UUID.randomUUID();
        credentialId = UUID.randomUUID();

        openaiConfig = AiProviderConfig.builder()
                .id(configId)
                .ownerId(adminId)
                .provider("openai")
                .name("Platform OpenAI")
                .description("OpenAI provider config")
                .credentialId(credentialId)
                .baseUrl("https://api.openai.com/v1")
                .defaultModel("gpt-4")
                .settings(Map.of("temperature", 0.7))
                .isActive(true)
                .isDefault(true)
                .isShared(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        anthropicConfig = AiProviderConfig.builder()
                .id(UUID.randomUUID())
                .ownerId(adminId)
                .provider("anthropic")
                .name("Platform Anthropic")
                .description("Anthropic provider config")
                .credentialId(UUID.randomUUID())
                .baseUrl("https://api.anthropic.com/v1")
                .defaultModel("claude-3-opus")
                .settings(Map.of())
                .isActive(true)
                .isDefault(false)
                .isShared(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("listUserConfigs (platform-shared scope)")
    class ListConfigs {

        @Test
        @DisplayName("should return all shared active configs regardless of requester")
        void listConfigs_returnsSharedConfigs() {
            when(configRepository.findByIsSharedTrueAndIsActiveTrue())
                    .thenReturn(List.of(openaiConfig, anthropicConfig));

            List<AiProviderConfigResponse> result = aiProviderService.listUserConfigs(adminId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getProvider()).isEqualTo("openai");
            assertThat(result.get(0).getIsDefault()).isTrue();
            assertThat(result.get(1).getProvider()).isEqualTo("anthropic");
            verify(configRepository).findByIsSharedTrueAndIsActiveTrue();
            verify(configRepository, never()).findByOwnerIdAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("should return empty list when no shared configs exist")
        void listConfigs_empty_returnsEmptyList() {
            when(configRepository.findByIsSharedTrueAndIsActiveTrue()).thenReturn(List.of());

            List<AiProviderConfigResponse> result = aiProviderService.listUserConfigs(adminId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDefaultConfig (platform-shared scope)")
    class GetDefaultConfig {

        @Test
        @DisplayName("should return the shared default config")
        void getDefaultConfig_returnsSharedDefault() {
            when(configRepository.findByIsSharedTrueAndIsDefaultTrue())
                    .thenReturn(Optional.of(openaiConfig));

            AiProviderConfigResponse result = aiProviderService.getDefaultConfig(adminId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(configId);
            assertThat(result.getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("should return null when no shared default exists")
        void getDefaultConfig_noDefault_returnsNull() {
            when(configRepository.findByIsSharedTrueAndIsDefaultTrue())
                    .thenReturn(Optional.empty());

            assertThat(aiProviderService.getDefaultConfig(adminId)).isNull();
        }
    }

    @Nested
    @DisplayName("getConfig (platform-shared scope)")
    class GetConfig {

        @Test
        @DisplayName("should return shared config by id")
        void getConfig_success() {
            when(configRepository.findByIdAndIsSharedTrue(configId))
                    .thenReturn(Optional.of(openaiConfig));

            AiProviderConfigResponse result = aiProviderService.getConfig(configId, adminId);

            assertThat(result.getId()).isEqualTo(configId);
            assertThat(result.getName()).isEqualTo("Platform OpenAI");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not shared or missing")
        void getConfig_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(configRepository.findByIdAndIsSharedTrue(nonExistingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.getConfig(nonExistingId, adminId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AI Provider config not found");
        }
    }

    @Nested
    @DisplayName("deleteConfig (platform-shared scope)")
    class DeleteConfig {

        @Test
        @DisplayName("should delete shared config and credential owned by its creator")
        void deleteConfig_withCredential_success() {
            when(configRepository.findByIdAndIsSharedTrue(configId))
                    .thenReturn(Optional.of(openaiConfig));

            UUID otherAdmin = UUID.randomUUID();
            aiProviderService.deleteConfig(configId, otherAdmin);

            // credential is deleted with the config creator's identity, not the acting admin's
            verify(credentialService).deleteCredential(credentialId, adminId);
            verify(configRepository).delete(openaiConfig);
        }

        @Test
        @DisplayName("should delete config without credential")
        void deleteConfig_withoutCredential_success() {
            AiProviderConfig noCredConfig = AiProviderConfig.builder()
                    .id(configId)
                    .ownerId(adminId)
                    .provider("ollama")
                    .name("Local Ollama")
                    .credentialId(null)
                    .isActive(true)
                    .isDefault(false)
                    .isShared(true)
                    .settings(Map.of())
                    .build();

            when(configRepository.findByIdAndIsSharedTrue(configId))
                    .thenReturn(Optional.of(noCredConfig));

            aiProviderService.deleteConfig(configId, adminId);

            verify(credentialService, never()).deleteCredential(any(), any());
            verify(configRepository).delete(noCredConfig);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found")
        void deleteConfig_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(configRepository.findByIdAndIsSharedTrue(nonExistingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.deleteConfig(nonExistingId, adminId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(configRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should still delete config when credential deletion fails")
        void deleteConfig_credentialDeletionFails_stillDeletesConfig() {
            when(configRepository.findByIdAndIsSharedTrue(configId))
                    .thenReturn(Optional.of(openaiConfig));
            doThrow(new RuntimeException("Credential deletion failed"))
                    .when(credentialService).deleteCredential(credentialId, adminId);

            aiProviderService.deleteConfig(configId, adminId);

            verify(configRepository).delete(openaiConfig);
        }
    }

    @Nested
    @DisplayName("setAsDefault (platform-wide single default)")
    class SetAsDefault {

        @Test
        @DisplayName("should clear all shared defaults and set config as default")
        void setAsDefault_success() {
            AiProviderConfig nonDefaultConfig = AiProviderConfig.builder()
                    .id(configId)
                    .ownerId(adminId)
                    .provider("openai")
                    .name("Platform OpenAI")
                    .credentialId(credentialId)
                    .isActive(true)
                    .isDefault(false)
                    .isShared(true)
                    .settings(Map.of())
                    .build();

            when(configRepository.findByIdAndIsSharedTrue(configId))
                    .thenReturn(Optional.of(nonDefaultConfig));

            aiProviderService.setAsDefault(configId, adminId);

            verify(configRepository).clearDefaultForShared();
            assertThat(nonDefaultConfig.getIsDefault()).isTrue();
            verify(configRepository).save(nonDefaultConfig);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found")
        void setAsDefault_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(configRepository.findByIdAndIsSharedTrue(nonExistingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiProviderService.setAsDefault(nonExistingId, adminId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(configRepository, never()).clearDefaultForShared();
            verify(configRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("resolveConfigForExecution / getAvailability")
    class ResolveForExecution {

        private final UUID memberId = UUID.randomUUID();

        @Test
        @DisplayName("shared default wins")
        void resolve_sharedDefaultFirst() {
            when(configRepository.findByIsSharedTrueAndIsDefaultTrue())
                    .thenReturn(Optional.of(openaiConfig));

            Optional<AiProviderConfig> result = aiProviderService.resolveConfigForExecution(memberId);

            assertThat(result).contains(openaiConfig);
            verify(configRepository, never()).findByOwnerIdAndIsDefaultTrue(any());
        }

        @Test
        @DisplayName("falls back to any active shared config when no shared default")
        void resolve_fallsBackToAnySharedActive() {
            when(configRepository.findByIsSharedTrueAndIsDefaultTrue())
                    .thenReturn(Optional.empty());
            when(configRepository.findByIsSharedTrueAndIsActiveTrue())
                    .thenReturn(List.of(anthropicConfig));

            Optional<AiProviderConfig> result = aiProviderService.resolveConfigForExecution(memberId);

            assertThat(result).contains(anthropicConfig);
        }

        @Test
        @DisplayName("falls back to the user's own legacy configs when no shared config exists")
        void resolve_fallsBackToLegacyUserConfig() {
            AiProviderConfig legacyConfig = AiProviderConfig.builder()
                    .id(UUID.randomUUID())
                    .ownerId(memberId)
                    .provider("gemini")
                    .name("My legacy Gemini")
                    .isActive(true)
                    .isDefault(true)
                    .isShared(false)
                    .settings(Map.of())
                    .build();

            when(configRepository.findByIsSharedTrueAndIsDefaultTrue()).thenReturn(Optional.empty());
            when(configRepository.findByIsSharedTrueAndIsActiveTrue()).thenReturn(List.of());
            when(configRepository.findByOwnerIdAndIsDefaultTrue(memberId))
                    .thenReturn(Optional.of(legacyConfig));

            Optional<AiProviderConfig> result = aiProviderService.resolveConfigForExecution(memberId);

            assertThat(result).contains(legacyConfig);
        }

        @Test
        @DisplayName("availability reports configured=false without leaking secrets when nothing is set up")
        void availability_notConfigured() {
            when(configRepository.findByIsSharedTrueAndIsDefaultTrue()).thenReturn(Optional.empty());
            when(configRepository.findByIsSharedTrueAndIsActiveTrue()).thenReturn(List.of());
            when(configRepository.findByOwnerIdAndIsDefaultTrue(memberId)).thenReturn(Optional.empty());
            when(configRepository.findByOwnerIdAndIsActiveTrue(memberId)).thenReturn(List.of());

            AiProviderService.AiAvailabilityResponse availability = aiProviderService.getAvailability(memberId);

            assertThat(availability.configured()).isFalse();
            assertThat(availability.provider()).isNull();
            assertThat(availability.defaultModel()).isNull();
        }

        @Test
        @DisplayName("availability exposes provider and default model only")
        void availability_configured() {
            when(configRepository.findByIsSharedTrueAndIsDefaultTrue())
                    .thenReturn(Optional.of(openaiConfig));

            AiProviderService.AiAvailabilityResponse availability = aiProviderService.getAvailability(memberId);

            assertThat(availability.configured()).isTrue();
            assertThat(availability.provider()).isEqualTo("openai");
            assertThat(availability.defaultModel()).isEqualTo("gpt-4");
        }
    }
}
