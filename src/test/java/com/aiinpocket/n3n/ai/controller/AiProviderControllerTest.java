package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.dto.request.CreateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.request.UpdateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.response.*;
import com.aiinpocket.n3n.ai.service.AiProviderService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiProviderControllerTest {

    @Mock
    private AiProviderService providerService;

    @InjectMocks
    private AiProviderController controller;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private AiProviderConfigResponse sampleConfigResponse() {
        return AiProviderConfigResponse.builder()
                .id(UUID.randomUUID())
                .provider("openai")
                .name("My OpenAI")
                .description("OpenAI config")
                .baseUrl("https://api.openai.com/v1")
                .defaultModel("gpt-4")
                .settings(Map.of("temperature", 0.7))
                .isActive(true)
                .isDefault(false)
                .hasCredential(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ProviderTypeResponse sampleProviderType(String id, String displayName) {
        return ProviderTypeResponse.builder()
                .id(id)
                .displayName(displayName)
                .defaultBaseUrl("https://api." + id + ".com/v1")
                .defaultTimeoutMs(30000)
                .requiresApiKey(true)
                .configSchema(Map.of())
                .build();
    }

    private AiModelResponse sampleModelResponse(String id, String displayName) {
        return AiModelResponse.builder()
                .id(id)
                .displayName(displayName)
                .providerId("openai")
                .contextWindow(128000)
                .maxOutputTokens(4096)
                .supportsVision(true)
                .supportsStreaming(true)
                .capabilities(Map.of("function_calling", true))
                .build();
    }

    // ===== listProviderTypes (GET /api/ai/providers/types) =====

    @Test
    void listProviderTypes_returnsAllTypes() {
        var types = List.of(
                sampleProviderType("claude", "Claude"),
                sampleProviderType("openai", "OpenAI"),
                sampleProviderType("gemini", "Gemini"),
                sampleProviderType("fal", "fal.ai")
        );
        when(providerService.listProviderTypes()).thenReturn(types);

        var result = controller.listProviderTypes();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(4);
        assertThat(result.getBody().get(0).getId()).isEqualTo("claude");
        assertThat(result.getBody().get(1).getId()).isEqualTo("openai");
        assertThat(result.getBody().get(2).getId()).isEqualTo("gemini");
        assertThat(result.getBody().get(3).getId()).isEqualTo("fal");
        verify(providerService).listProviderTypes();
    }

    @Test
    void listProviderTypes_empty_returnsEmptyList() {
        when(providerService.listProviderTypes()).thenReturn(List.of());

        var result = controller.listProviderTypes();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listProviderTypes_singleType_returnsSingleItem() {
        var types = List.of(sampleProviderType("fal", "fal.ai"));
        when(providerService.listProviderTypes()).thenReturn(types);

        var result = controller.listProviderTypes();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getDisplayName()).isEqualTo("fal.ai");
        assertThat(result.getBody().get(0).isRequiresApiKey()).isTrue();
    }

    // ===== listConfigs (GET /api/ai/providers/configs) =====

    @Test
    void listConfigs_returnsUserConfigs() {
        var user = testUser();
        var config1 = sampleConfigResponse();
        var config2 = AiProviderConfigResponse.builder()
                .id(UUID.randomUUID())
                .provider("claude")
                .name("My Claude")
                .isActive(true)
                .isDefault(true)
                .hasCredential(true)
                .build();
        when(providerService.listUserConfigs(any(UUID.class))).thenReturn(List.of(config1, config2));

        var result = controller.listConfigs(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getProvider()).isEqualTo("openai");
        assertThat(result.getBody().get(1).getProvider()).isEqualTo("claude");
        verify(providerService).listUserConfigs(any(UUID.class));
    }

    @Test
    void listConfigs_emptyList_returnsOk() {
        var user = testUser();
        when(providerService.listUserConfigs(any(UUID.class))).thenReturn(List.of());

        var result = controller.listConfigs(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listConfigs_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(providerService.listUserConfigs(eq(userId))).thenReturn(List.of());

        controller.listConfigs(user);

        verify(providerService).listUserConfigs(eq(userId));
    }

    // ===== getDefaultConfig (GET /api/ai/providers/configs/default) =====

    @Test
    void getDefaultConfig_found_returnsOk() {
        var user = testUser();
        var config = AiProviderConfigResponse.builder()
                .id(UUID.randomUUID())
                .provider("openai")
                .name("Default OpenAI")
                .isDefault(true)
                .isActive(true)
                .hasCredential(true)
                .build();
        when(providerService.getDefaultConfig(any(UUID.class))).thenReturn(config);

        var result = controller.getDefaultConfig(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("Default OpenAI");
        assertThat(result.getBody().getIsDefault()).isTrue();
    }

    @Test
    void getDefaultConfig_notFound_returnsNotFound() {
        var user = testUser();
        when(providerService.getDefaultConfig(any(UUID.class))).thenReturn(null);

        var result = controller.getDefaultConfig(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void getDefaultConfig_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(providerService.getDefaultConfig(eq(userId))).thenReturn(null);

        controller.getDefaultConfig(user);

        verify(providerService).getDefaultConfig(eq(userId));
    }

    // ===== getConfig (GET /api/ai/providers/configs/{id}) =====

    @Test
    void getConfig_found_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var config = AiProviderConfigResponse.builder()
                .id(configId)
                .provider("claude")
                .name("My Claude")
                .defaultModel("claude-3-opus-20240229")
                .isActive(true)
                .isDefault(false)
                .hasCredential(true)
                .build();
        when(providerService.getConfig(eq(configId), any(UUID.class))).thenReturn(config);

        var result = controller.getConfig(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(configId);
        assertThat(result.getBody().getProvider()).isEqualTo("claude");
        assertThat(result.getBody().getDefaultModel()).isEqualTo("claude-3-opus-20240229");
    }

    @Test
    void getConfig_notFound_throwsException() {
        var user = testUser();
        var configId = UUID.randomUUID();
        when(providerService.getConfig(eq(configId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("AI Provider config not found: " + configId));

        assertThatThrownBy(() -> controller.getConfig(configId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Provider config not found");
    }

    @Test
    void getConfig_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();
        var config = sampleConfigResponse();
        when(providerService.getConfig(eq(configId), eq(userId))).thenReturn(config);

        controller.getConfig(configId, user);

        verify(providerService).getConfig(eq(configId), eq(userId));
    }

    // ===== createConfig (POST /api/ai/providers/configs) =====

    @Test
    void createConfig_success_returnsCreated() {
        var user = testUser();
        var request = new CreateAiProviderRequest();
        request.setProvider("openai");
        request.setName("New OpenAI Config");
        request.setDescription("My new OpenAI config");
        request.setApiKey("sk-test-key-12345");
        request.setBaseUrl("https://api.openai.com/v1");
        request.setDefaultModel("gpt-4");
        request.setSettings(Map.of("temperature", 0.7));
        request.setIsDefault(true);

        var response = AiProviderConfigResponse.builder()
                .id(UUID.randomUUID())
                .provider("openai")
                .name("New OpenAI Config")
                .description("My new OpenAI config")
                .baseUrl("https://api.openai.com/v1")
                .defaultModel("gpt-4")
                .settings(Map.of("temperature", 0.7))
                .isActive(true)
                .isDefault(true)
                .hasCredential(true)
                .build();
        when(providerService.createConfig(any(CreateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.createConfig(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("New OpenAI Config");
        assertThat(result.getBody().getProvider()).isEqualTo("openai");
        assertThat(result.getBody().getIsDefault()).isTrue();
        assertThat(result.getBody().getHasCredential()).isTrue();
        verify(providerService).createConfig(any(CreateAiProviderRequest.class), any(UUID.class));
    }

    @Test
    void createConfig_duplicateName_throwsException() {
        var user = testUser();
        var request = new CreateAiProviderRequest();
        request.setProvider("openai");
        request.setName("Existing Config");
        request.setApiKey("sk-test-key");

        when(providerService.createConfig(any(CreateAiProviderRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("AI Provider with this name already exists"));

        assertThatThrownBy(() -> controller.createConfig(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createConfig_missingApiKey_throwsException() {
        var user = testUser();
        var request = new CreateAiProviderRequest();
        request.setProvider("claude");
        request.setName("Claude Config");

        when(providerService.createConfig(any(CreateAiProviderRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("API Key is required for Claude"));

        assertThatThrownBy(() -> controller.createConfig(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API Key is required");
    }

    @Test
    void createConfig_withoutApiKey_succeeds() {
        var user = testUser();
        var request = new CreateAiProviderRequest();
        request.setProvider("gemini");
        request.setName("Gemini Config");
        request.setBaseUrl("http://localhost:11434");

        var response = AiProviderConfigResponse.builder()
                .id(UUID.randomUUID())
                .provider("gemini")
                .name("Gemini Config")
                .baseUrl("http://localhost:11434")
                .isActive(true)
                .isDefault(false)
                .hasCredential(false)
                .build();
        when(providerService.createConfig(any(CreateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.createConfig(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getHasCredential()).isFalse();
        assertThat(result.getBody().getProvider()).isEqualTo("gemini");
    }

    @Test
    void createConfig_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new CreateAiProviderRequest();
        request.setProvider("openai");
        request.setName("Config");
        request.setApiKey("sk-test");

        var response = sampleConfigResponse();
        when(providerService.createConfig(any(CreateAiProviderRequest.class), eq(userId)))
                .thenReturn(response);

        controller.createConfig(request, user);

        verify(providerService).createConfig(any(CreateAiProviderRequest.class), eq(userId));
    }

    @Test
    void createConfig_withWorkspaceId_passesRequestThrough() {
        var user = testUser();
        var request = new CreateAiProviderRequest();
        request.setProvider("gemini");
        request.setName("Workspace Gemini");
        request.setApiKey("gemini-key");
        request.setWorkspaceId(UUID.randomUUID());

        var response = sampleConfigResponse();
        when(providerService.createConfig(any(CreateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.createConfig(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(providerService).createConfig(any(CreateAiProviderRequest.class), any(UUID.class));
    }

    // ===== updateConfig (PUT /api/ai/providers/configs/{id}) =====

    @Test
    void updateConfig_success_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var request = new UpdateAiProviderRequest();
        request.setName("Updated Name");
        request.setDescription("Updated description");
        request.setDefaultModel("gpt-4-turbo");

        var response = AiProviderConfigResponse.builder()
                .id(configId)
                .provider("openai")
                .name("Updated Name")
                .description("Updated description")
                .defaultModel("gpt-4-turbo")
                .isActive(true)
                .isDefault(false)
                .hasCredential(true)
                .build();
        when(providerService.updateConfig(eq(configId), any(UpdateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateConfig(configId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("Updated Name");
        assertThat(result.getBody().getDescription()).isEqualTo("Updated description");
        assertThat(result.getBody().getDefaultModel()).isEqualTo("gpt-4-turbo");
    }

    @Test
    void updateConfig_notFound_throwsException() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var request = new UpdateAiProviderRequest();
        request.setName("Updated");

        when(providerService.updateConfig(eq(configId), any(UpdateAiProviderRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("AI Provider config not found: " + configId));

        assertThatThrownBy(() -> controller.updateConfig(configId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Provider config not found");
    }

    @Test
    void updateConfig_withNewApiKey_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var request = new UpdateAiProviderRequest();
        request.setApiKey("sk-new-api-key");

        var response = AiProviderConfigResponse.builder()
                .id(configId)
                .provider("openai")
                .name("Config")
                .isActive(true)
                .isDefault(false)
                .hasCredential(true)
                .build();
        when(providerService.updateConfig(eq(configId), any(UpdateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateConfig(configId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getHasCredential()).isTrue();
    }

    @Test
    void updateConfig_deactivate_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var request = new UpdateAiProviderRequest();
        request.setIsActive(false);

        var response = AiProviderConfigResponse.builder()
                .id(configId)
                .provider("openai")
                .name("Deactivated Config")
                .isActive(false)
                .isDefault(false)
                .hasCredential(true)
                .build();
        when(providerService.updateConfig(eq(configId), any(UpdateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateConfig(configId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getIsActive()).isFalse();
    }

    @Test
    void updateConfig_withSettings_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var request = new UpdateAiProviderRequest();
        request.setSettings(Map.of("temperature", 0.9, "max_tokens", 2048));

        var response = AiProviderConfigResponse.builder()
                .id(configId)
                .provider("openai")
                .name("Config")
                .settings(Map.of("temperature", 0.9, "max_tokens", 2048))
                .isActive(true)
                .isDefault(false)
                .hasCredential(true)
                .build();
        when(providerService.updateConfig(eq(configId), any(UpdateAiProviderRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateConfig(configId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getSettings()).containsEntry("temperature", 0.9);
        assertThat(result.getBody().getSettings()).containsEntry("max_tokens", 2048);
    }

    @Test
    void updateConfig_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();
        var request = new UpdateAiProviderRequest();
        request.setName("Updated");

        var response = sampleConfigResponse();
        when(providerService.updateConfig(eq(configId), any(UpdateAiProviderRequest.class), eq(userId)))
                .thenReturn(response);

        controller.updateConfig(configId, request, user);

        verify(providerService).updateConfig(eq(configId), any(UpdateAiProviderRequest.class), eq(userId));
    }

    // ===== deleteConfig (DELETE /api/ai/providers/configs/{id}) =====

    @Test
    void deleteConfig_success_returnsNoContent() {
        var user = testUser();
        var configId = UUID.randomUUID();
        doNothing().when(providerService).deleteConfig(eq(configId), any(UUID.class));

        var result = controller.deleteConfig(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(providerService).deleteConfig(eq(configId), any(UUID.class));
    }

    @Test
    void deleteConfig_notFound_throwsException() {
        var user = testUser();
        var configId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("AI Provider config not found: " + configId))
                .when(providerService).deleteConfig(eq(configId), any(UUID.class));

        assertThatThrownBy(() -> controller.deleteConfig(configId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Provider config not found");
    }

    @Test
    void deleteConfig_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();
        doNothing().when(providerService).deleteConfig(eq(configId), eq(userId));

        controller.deleteConfig(configId, user);

        verify(providerService).deleteConfig(eq(configId), eq(userId));
    }

    // ===== setAsDefault (POST /api/ai/providers/configs/{id}/default) =====

    @Test
    void setAsDefault_success_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        doNothing().when(providerService).setAsDefault(eq(configId), any(UUID.class));

        var result = controller.setAsDefault(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(providerService).setAsDefault(eq(configId), any(UUID.class));
    }

    @Test
    void setAsDefault_notFound_throwsException() {
        var user = testUser();
        var configId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("AI Provider config not found: " + configId))
                .when(providerService).setAsDefault(eq(configId), any(UUID.class));

        assertThatThrownBy(() -> controller.setAsDefault(configId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Provider config not found");
    }

    @Test
    void setAsDefault_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();
        doNothing().when(providerService).setAsDefault(eq(configId), eq(userId));

        controller.setAsDefault(configId, user);

        verify(providerService).setAsDefault(eq(configId), eq(userId));
    }

    // ===== testConnection (POST /api/ai/providers/configs/{id}/test) =====

    @Test
    void testConnection_success_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var response = TestConnectionResponse.success(150L);
        when(providerService.testConnection(eq(configId), any(UUID.class))).thenReturn(response);

        var result = controller.testConnection(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("Connection successful");
        assertThat(result.getBody().getLatencyMs()).isEqualTo(150L);
    }

    @Test
    void testConnection_failure_returnsOkWithFailure() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var response = TestConnectionResponse.failed("Connection failed");
        when(providerService.testConnection(eq(configId), any(UUID.class))).thenReturn(response);

        var result = controller.testConnection(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("Connection failed");
        assertThat(result.getBody().getLatencyMs()).isNull();
    }

    @Test
    void testConnection_notFound_throwsException() {
        var user = testUser();
        var configId = UUID.randomUUID();
        when(providerService.testConnection(eq(configId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("AI Provider config not found: " + configId));

        assertThatThrownBy(() -> controller.testConnection(configId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Provider config not found");
    }

    @Test
    void testConnection_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();
        var response = TestConnectionResponse.success(100L);
        when(providerService.testConnection(eq(configId), eq(userId))).thenReturn(response);

        controller.testConnection(configId, user);

        verify(providerService).testConnection(eq(configId), eq(userId));
    }

    // ===== listModels (GET /api/ai/providers/configs/{id}/models) =====

    @Test
    void listModels_returnsModelList() {
        var user = testUser();
        var configId = UUID.randomUUID();
        var models = List.of(
                sampleModelResponse("gpt-4", "GPT-4"),
                sampleModelResponse("gpt-4-turbo", "GPT-4 Turbo"),
                sampleModelResponse("gpt-3.5-turbo", "GPT-3.5 Turbo")
        );
        when(providerService.fetchModels(eq(configId), any(UUID.class))).thenReturn(models);

        var result = controller.listModels(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(3);
        assertThat(result.getBody().get(0).getId()).isEqualTo("gpt-4");
        assertThat(result.getBody().get(0).getContextWindow()).isEqualTo(128000);
        assertThat(result.getBody().get(0).isSupportsVision()).isTrue();
    }

    @Test
    void listModels_emptyList_returnsOk() {
        var user = testUser();
        var configId = UUID.randomUUID();
        when(providerService.fetchModels(eq(configId), any(UUID.class))).thenReturn(List.of());

        var result = controller.listModels(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listModels_notFound_throwsException() {
        var user = testUser();
        var configId = UUID.randomUUID();
        when(providerService.fetchModels(eq(configId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("AI Provider config not found: " + configId));

        assertThatThrownBy(() -> controller.listModels(configId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Provider config not found");
    }

    @Test
    void listModels_serviceThrowsRuntimeException_propagates() {
        var user = testUser();
        var configId = UUID.randomUUID();
        when(providerService.fetchModels(eq(configId), any(UUID.class)))
                .thenThrow(new RuntimeException("Failed to fetch models: request timed out"));

        assertThatThrownBy(() -> controller.listModels(configId, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch models");
    }

    @Test
    void listModels_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();
        when(providerService.fetchModels(eq(configId), eq(userId))).thenReturn(List.of());

        controller.listModels(configId, user);

        verify(providerService).fetchModels(eq(configId), eq(userId));
    }

    // ===== fetchModelsWithKey (POST /api/ai/providers/models) =====

    @Test
    void fetchModelsWithKey_success_returnsModels() {
        var request = new AiProviderController.FetchModelsRequest("openai", "sk-test-key", "https://api.openai.com/v1");
        var models = List.of(
                sampleModelResponse("gpt-4", "GPT-4"),
                sampleModelResponse("gpt-3.5-turbo", "GPT-3.5 Turbo")
        );
        when(providerService.fetchModelsWithKey("openai", "sk-test-key", "https://api.openai.com/v1"))
                .thenReturn(models);

        var result = controller.fetchModelsWithKey(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getId()).isEqualTo("gpt-4");
        verify(providerService).fetchModelsWithKey("openai", "sk-test-key", "https://api.openai.com/v1");
    }

    @Test
    void fetchModelsWithKey_withNullBaseUrl_passesNull() {
        var request = new AiProviderController.FetchModelsRequest("claude", "sk-ant-test", null);
        when(providerService.fetchModelsWithKey("claude", "sk-ant-test", null))
                .thenReturn(List.of(sampleModelResponse("claude-3-opus", "Claude 3 Opus")));

        var result = controller.fetchModelsWithKey(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        verify(providerService).fetchModelsWithKey("claude", "sk-ant-test", null);
    }

    @Test
    void fetchModelsWithKey_invalidProvider_throwsException() {
        var request = new AiProviderController.FetchModelsRequest("invalid", "key", null);
        when(providerService.fetchModelsWithKey("invalid", "key", null))
                .thenThrow(new IllegalArgumentException("Unknown provider: invalid"));

        assertThatThrownBy(() -> controller.fetchModelsWithKey(request, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider");
    }

    @Test
    void fetchModelsWithKey_serviceException_propagates() {
        var request = new AiProviderController.FetchModelsRequest("openai", "bad-key", null);
        when(providerService.fetchModelsWithKey("openai", "bad-key", null))
                .thenThrow(new RuntimeException("Failed to fetch models"));

        assertThatThrownBy(() -> controller.fetchModelsWithKey(request, testUser()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch models");
    }

    @Test
    void fetchModelsWithKey_emptyResult_returnsEmptyList() {
        var request = new AiProviderController.FetchModelsRequest("gemini", "no-key", null);
        when(providerService.fetchModelsWithKey("gemini", "no-key", null))
                .thenReturn(List.of());

        var result = controller.fetchModelsWithKey(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    // ===== FetchModelsRequest record field access =====

    @Test
    void fetchModelsRequest_recordAccessors_work() {
        var request = new AiProviderController.FetchModelsRequest("openai", "sk-key", "https://api.openai.com/v1");

        assertThat(request.provider()).isEqualTo("openai");
        assertThat(request.apiKey()).isEqualTo("sk-key");
        assertThat(request.baseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    // ===== Cross-cutting: verify all endpoints extract userId properly =====

    @Test
    void allAuthenticatedEndpoints_useCorrectUserId() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var configId = UUID.randomUUID();

        // listConfigs
        when(providerService.listUserConfigs(eq(userId))).thenReturn(List.of());
        controller.listConfigs(user);
        verify(providerService).listUserConfigs(eq(userId));

        // getDefaultConfig
        when(providerService.getDefaultConfig(eq(userId))).thenReturn(null);
        controller.getDefaultConfig(user);
        verify(providerService).getDefaultConfig(eq(userId));

        // getConfig
        var config = sampleConfigResponse();
        when(providerService.getConfig(eq(configId), eq(userId))).thenReturn(config);
        controller.getConfig(configId, user);
        verify(providerService).getConfig(eq(configId), eq(userId));

        // createConfig
        var createReq = new CreateAiProviderRequest();
        createReq.setProvider("openai");
        createReq.setName("Test");
        createReq.setApiKey("key");
        when(providerService.createConfig(any(), eq(userId))).thenReturn(config);
        controller.createConfig(createReq, user);
        verify(providerService).createConfig(any(), eq(userId));

        // updateConfig
        var updateReq = new UpdateAiProviderRequest();
        updateReq.setName("Updated");
        when(providerService.updateConfig(eq(configId), any(), eq(userId))).thenReturn(config);
        controller.updateConfig(configId, updateReq, user);
        verify(providerService).updateConfig(eq(configId), any(), eq(userId));

        // deleteConfig
        doNothing().when(providerService).deleteConfig(eq(configId), eq(userId));
        controller.deleteConfig(configId, user);
        verify(providerService).deleteConfig(eq(configId), eq(userId));

        // setAsDefault
        doNothing().when(providerService).setAsDefault(eq(configId), eq(userId));
        controller.setAsDefault(configId, user);
        verify(providerService).setAsDefault(eq(configId), eq(userId));

        // testConnection
        when(providerService.testConnection(eq(configId), eq(userId)))
                .thenReturn(TestConnectionResponse.success(100L));
        controller.testConnection(configId, user);
        verify(providerService).testConnection(eq(configId), eq(userId));

        // listModels
        when(providerService.fetchModels(eq(configId), eq(userId))).thenReturn(List.of());
        controller.listModels(configId, user);
        verify(providerService).fetchModels(eq(configId), eq(userId));
    }

    // ===== Response status code verification for all endpoints =====

    @Test
    void createConfig_returnsHttpStatusCreated_not200() {
        var user = testUser();
        var request = new CreateAiProviderRequest();
        request.setProvider("openai");
        request.setName("Test");
        request.setApiKey("key");

        when(providerService.createConfig(any(), any())).thenReturn(sampleConfigResponse());

        var result = controller.createConfig(request, user);

        // Specifically verify it's 201, not 200
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteConfig_returnsHttpStatus204_noBody() {
        var user = testUser();
        var configId = UUID.randomUUID();
        doNothing().when(providerService).deleteConfig(any(), any());

        var result = controller.deleteConfig(configId, user);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void setAsDefault_returnsHttpStatus200_noBody() {
        var user = testUser();
        var configId = UUID.randomUUID();
        doNothing().when(providerService).setAsDefault(any(), any());

        var result = controller.setAsDefault(configId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // setAsDefault returns ResponseEntity.ok().build() which has null body
        assertThat(result.getBody()).isNull();
    }
}
