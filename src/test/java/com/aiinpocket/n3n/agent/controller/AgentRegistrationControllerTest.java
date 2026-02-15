package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.AgentRegistration;
import com.aiinpocket.n3n.agent.entity.AgentRegistration.AgentStatus;
import com.aiinpocket.n3n.agent.service.AgentRegistrationService;
import com.aiinpocket.n3n.agent.service.AgentRegistrationService.RegistrationResult;
import com.aiinpocket.n3n.agent.service.AgentRegistrationService.TokenGenerationResult;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRegistrationControllerTest {

    @Mock
    private AgentRegistrationService registrationService;

    @Mock
    private GatewaySettingsService gatewaySettingsService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AgentRegistrationController controller;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID id) {
        return User.withUsername(id.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private TokenGenerationResult sampleTokenResult() {
        var config = new AgentConfig(
                1,
                new AgentConfig.Gateway("wss://localhost:9443/gateway/agent/secure", "localhost", 9443),
                new AgentConfig.Registration("test-token-abc123", "agent-id-1")
        );
        return new TokenGenerationResult(
                UUID.randomUUID(),
                "agent-id-1",
                "test-token-abc123",
                config
        );
    }

    private AgentRegistration sampleRegistration() {
        return AgentRegistration.builder()
                .id(UUID.randomUUID())
                .deviceId("device-001")
                .deviceName("MacBook Pro")
                .platform("macos")
                .status(AgentStatus.REGISTERED)
                .createdAt(Instant.now())
                .registeredAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
    }

    private AgentRegistration sampleRegistration(UUID id, String deviceId, String deviceName,
                                                  String platform, AgentStatus status) {
        var reg = AgentRegistration.builder()
                .id(id)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .platform(platform)
                .status(status)
                .createdAt(Instant.now())
                .build();
        if (status == AgentStatus.REGISTERED) {
            reg.setRegisteredAt(Instant.now());
            reg.setLastSeenAt(Instant.now());
        }
        if (status == AgentStatus.BLOCKED) {
            reg.setBlockedAt(Instant.now());
            reg.setBlockedReason("Suspicious activity");
        }
        return reg;
    }

    // ===== generateToken (POST /api/agents/tokens) =====

    @Test
    void generateToken_success_returnsOk() {
        var user = testUser();
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.generateToken(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(registrationService).generateToken(userId);
    }

    @Test
    void generateToken_nullUserDetails_returnsUnauthorized() {
        var result = controller.generateToken(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
        verify(registrationService, never()).generateToken(any());
    }

    @Test
    void generateToken_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        when(registrationService.generateToken(eq(userId)))
                .thenThrow(new RuntimeException("Database error"));

        var result = controller.generateToken(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to generate token");
    }

    @Test
    void generateToken_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(specificUserId))).thenReturn(tokenResult);

        controller.generateToken(user);

        verify(registrationService).generateToken(eq(specificUserId));
    }

    // ===== generateTokenJson (POST /api/agents/tokens/json) =====

    @Test
    void generateTokenJson_success_returnsOk() {
        var user = testUser();
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.generateTokenJson(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsKey("registrationId");
        assertThat(body).containsKey("agentId");
        assertThat(body).containsKey("config");
        verify(registrationService).generateToken(userId);
    }

    @Test
    void generateTokenJson_nullUserDetails_returnsUnauthorized() {
        var result = controller.generateTokenJson(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
    }

    @Test
    void generateTokenJson_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        when(registrationService.generateToken(eq(userId)))
                .thenThrow(new RuntimeException("Error"));

        var result = controller.generateTokenJson(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to generate token");
    }

    @Test
    void generateTokenJson_returnsCorrectFields() {
        var user = testUser();
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.generateTokenJson(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body.get("registrationId")).isEqualTo(tokenResult.registrationId());
        assertThat(body.get("agentId")).isEqualTo(tokenResult.agentId());
        assertThat(body.get("config")).isEqualTo(tokenResult.config());
    }

    // ===== generateInstallCommand (POST /api/agents/install-command) =====

    @Test
    void generateInstallCommand_success_returnsOk() {
        var user = testUser();
        ReflectionTestUtils.setField(controller, "configuredBaseUrl", "https://n3n.example.com");
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.generateInstallCommand(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsKey("command");
        assertThat(body).containsKey("registrationId");
        assertThat(body).containsKey("agentId");
        assertThat((String) body.get("command")).contains("curl");
        assertThat((String) body.get("command")).contains("n3n.example.com");
    }

    @Test
    void generateInstallCommand_nullUserDetails_returnsUnauthorized() {
        var result = controller.generateInstallCommand(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void generateInstallCommand_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        when(registrationService.generateToken(eq(userId)))
                .thenThrow(new RuntimeException("Error"));

        var result = controller.generateInstallCommand(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to generate install command");
    }

    @Test
    void generateInstallCommand_noBaseUrl_usesLocalhost() {
        var user = testUser();
        ReflectionTestUtils.setField(controller, "configuredBaseUrl", "");
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.generateInstallCommand(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat((String) body.get("command")).contains("http://localhost:8080");
    }

    @Test
    void generateInstallCommand_nullBaseUrl_usesLocalhost() {
        var user = testUser();
        ReflectionTestUtils.setField(controller, "configuredBaseUrl", null);
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.generateInstallCommand(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat((String) body.get("command")).contains("http://localhost:8080");
    }

    // ===== getInstallScript (GET /api/agents/install.sh) =====

    @Test
    void getInstallScript_success_returnsScript() {
        ReflectionTestUtils.setField(controller, "configuredBaseUrl", "https://n3n.example.com");

        var result = controller.getInstallScript("valid-token-12345");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).contains("#!/bin/bash");
        assertThat(result.getBody()).contains("N3N Agent Installer");
        assertThat(result.getBody()).contains("n3n.example.com");
        assertThat(result.getBody()).contains("valid-token-12345");
    }

    @Test
    void getInstallScript_noBaseUrl_usesLocalhost() {
        ReflectionTestUtils.setField(controller, "configuredBaseUrl", "");

        var result = controller.getInstallScript("valid-token-12345");

        assertThat(result.getBody()).contains("http://localhost:8080");
    }

    // ===== downloadBinary (GET /api/agents/binary/{platform}) =====

    @Test
    void downloadBinary_unsupportedPlatform_returnsBadRequest() {
        var result = controller.downloadBinary("linux");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void downloadBinary_unknownPlatform_returnsBadRequest() {
        var result = controller.downloadBinary("android");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== downloadConfig (GET /api/agents/config) =====

    @Test
    void downloadConfig_validToken_returnsConfig() throws Exception {
        var registration = sampleRegistration();
        var config = new AgentConfig(
                1,
                new AgentConfig.Gateway("wss://localhost:9443/gateway/agent/secure", "localhost", 9443),
                new AgentConfig.Registration("token123", "agent-1")
        );
        when(registrationService.getRegistrationByToken("token123")).thenReturn(registration);
        when(registrationService.generateConfigForToken("token123")).thenReturn(config);

        var result = controller.downloadConfig("token123");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(registrationService).getRegistrationByToken("token123");
        verify(registrationService).generateConfigForToken("token123");
    }

    @Test
    void downloadConfig_invalidToken_returnsNotFound() {
        when(registrationService.getRegistrationByToken("invalid-token")).thenReturn(null);

        var result = controller.downloadConfig("invalid-token");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Invalid token");
    }

    @Test
    void downloadConfig_serviceThrowsException_returnsInternalServerError() {
        when(registrationService.getRegistrationByToken(anyString()))
                .thenThrow(new RuntimeException("Database error"));

        var result = controller.downloadConfig("some-token-value");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to get config");
    }

    // ===== registerWithToken (POST /api/agents/register) =====

    @Test
    void registerWithToken_success_returnsOk() {
        var request = new AgentRegistrationController.TokenRegistrationRequest(
                "valid-token", "device-001", "MacBook", "macos", "publicKey123", "fingerprint");
        var registration = sampleRegistration();
        var registrationResult = new RegistrationResult(true, "platformPubKey", "platformFP", "deviceToken123");
        when(registrationService.getRegistrationByToken("valid-token")).thenReturn(registration);
        when(registrationService.completeTokenRegistration(
                eq("valid-token"), eq("device-001"), eq("MacBook"),
                eq("macos"), eq("publicKey123"), eq("fingerprint")))
                .thenReturn(registrationResult);

        var result = controller.registerWithToken(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("platformPublicKey", "platformPubKey");
        assertThat(body).containsEntry("platformFingerprint", "platformFP");
        assertThat(body).containsEntry("deviceToken", "deviceToken123");
    }

    @Test
    void registerWithToken_invalidToken_returnsBadRequest() {
        var request = new AgentRegistrationController.TokenRegistrationRequest(
                "invalid-token", "device-001", "MacBook", "macos", "pubKey", "fp");
        when(registrationService.getRegistrationByToken("invalid-token")).thenReturn(null);

        var result = controller.registerWithToken(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("error", "Invalid or expired token");
    }

    @Test
    void registerWithToken_registrationFails_returnsBadRequest() {
        var request = new AgentRegistrationController.TokenRegistrationRequest(
                "valid-token", "device-001", "MacBook", "macos", "pubKey", "fp");
        var registration = sampleRegistration();
        when(registrationService.getRegistrationByToken("valid-token")).thenReturn(registration);
        when(registrationService.completeTokenRegistration(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(null);

        var result = controller.registerWithToken(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("error", "Registration failed");
    }

    @Test
    void registerWithToken_serviceThrowsException_returnsInternalServerError() {
        var request = new AgentRegistrationController.TokenRegistrationRequest(
                "valid-token", "device-001", "MacBook", "macos", "pubKey", "fp");
        when(registrationService.getRegistrationByToken(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        var result = controller.registerWithToken(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("error", "Registration failed. Please try again.");
    }

    // ===== listRegistrations (GET /api/agents/registrations) =====

    @Test
    void listRegistrations_success_returnsRegistrations() {
        var user = testUser();
        var reg1 = sampleRegistration(UUID.randomUUID(), "device-001", "MacBook Pro", "macos", AgentStatus.REGISTERED);
        var reg2 = sampleRegistration(UUID.randomUUID(), "device-002", "iMac", "macos", AgentStatus.PENDING);
        when(registrationService.getRegistrations(eq(userId))).thenReturn(List.of(reg1, reg2));

        var result = controller.listRegistrations(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsKey("registrations");
        @SuppressWarnings("unchecked")
        var registrations = (List<?>) body.get("registrations");
        assertThat(registrations).hasSize(2);
        verify(registrationService).getRegistrations(userId);
    }

    @Test
    void listRegistrations_nullUserDetails_returnsUnauthorized() {
        var result = controller.listRegistrations(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(registrationService, never()).getRegistrations(any());
    }

    @Test
    void listRegistrations_emptyList_returnsOk() {
        var user = testUser();
        when(registrationService.getRegistrations(eq(userId))).thenReturn(List.of());

        var result = controller.listRegistrations(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var registrations = (List<?>) body.get("registrations");
        assertThat(registrations).isEmpty();
    }

    @Test
    void listRegistrations_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        when(registrationService.getRegistrations(eq(userId)))
                .thenThrow(new RuntimeException("Database error"));

        var result = controller.listRegistrations(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to list registrations");
    }

    @Test
    void listRegistrations_registrationInfoContainsAllFields() {
        var user = testUser();
        var regId = UUID.randomUUID();
        var reg = sampleRegistration(regId, "device-001", "MacBook Pro", "macos", AgentStatus.BLOCKED);
        when(registrationService.getRegistrations(eq(userId))).thenReturn(List.of(reg));

        var result = controller.listRegistrations(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var registrations = (List<AgentRegistrationController.RegistrationInfo>) body.get("registrations");
        var info = registrations.get(0);
        assertThat(info.id()).isEqualTo(regId);
        assertThat(info.deviceId()).isEqualTo("device-001");
        assertThat(info.deviceName()).isEqualTo("MacBook Pro");
        assertThat(info.platform()).isEqualTo("macos");
        assertThat(info.status()).isEqualTo("BLOCKED");
        assertThat(info.blockedReason()).isEqualTo("Suspicious activity");
        assertThat(info.blockedAt()).isNotNull();
    }

    // ===== blockAgent (PUT /api/agents/{id}/block) =====

    @Test
    void blockAgent_success_returnsOk() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        var request = new AgentRegistrationController.BlockRequest("Suspicious");
        doNothing().when(registrationService).blockAgent(eq(userId), eq(agentId), eq("Suspicious"));

        var result = controller.blockAgent(agentId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        verify(registrationService).blockAgent(userId, agentId, "Suspicious");
    }

    @Test
    void blockAgent_nullRequest_usesDefaultReason() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doNothing().when(registrationService).blockAgent(eq(userId), eq(agentId), eq("Blocked by user"));

        var result = controller.blockAgent(agentId, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(registrationService).blockAgent(userId, agentId, "Blocked by user");
    }

    @Test
    void blockAgent_nullUserDetails_returnsUnauthorized() {
        var agentId = UUID.randomUUID();

        var result = controller.blockAgent(agentId, null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(registrationService, never()).blockAgent(any(), any(), anyString());
    }

    @Test
    void blockAgent_notFound_returnsNotFound() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Registration not found"))
                .when(registrationService).blockAgent(eq(userId), eq(agentId), anyString());

        var result = controller.blockAgent(agentId, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Agent not found");
    }

    @Test
    void blockAgent_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected error"))
                .when(registrationService).blockAgent(eq(userId), eq(agentId), anyString());

        var result = controller.blockAgent(agentId, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to block agent");
    }

    // ===== unblockAgent (PUT /api/agents/{id}/unblock) =====

    @Test
    void unblockAgent_success_returnsOk() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doNothing().when(registrationService).unblockAgent(eq(userId), eq(agentId));

        var result = controller.unblockAgent(agentId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        verify(registrationService).unblockAgent(userId, agentId);
    }

    @Test
    void unblockAgent_nullUserDetails_returnsUnauthorized() {
        var agentId = UUID.randomUUID();

        var result = controller.unblockAgent(agentId, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(registrationService, never()).unblockAgent(any(), any());
    }

    @Test
    void unblockAgent_notFound_returnsNotFound() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Registration not found"))
                .when(registrationService).unblockAgent(eq(userId), eq(agentId));

        var result = controller.unblockAgent(agentId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Agent not found");
    }

    @Test
    void unblockAgent_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected error"))
                .when(registrationService).unblockAgent(eq(userId), eq(agentId));

        var result = controller.unblockAgent(agentId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to unblock agent");
    }

    @Test
    void unblockAgent_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var agentId = UUID.randomUUID();
        doNothing().when(registrationService).unblockAgent(eq(specificUserId), eq(agentId));

        controller.unblockAgent(agentId, user);

        verify(registrationService).unblockAgent(eq(specificUserId), eq(agentId));
    }

    // ===== deleteRegistration (DELETE /api/agents/{id}) =====

    @Test
    void deleteRegistration_success_returnsOk() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doNothing().when(registrationService).deleteRegistration(eq(userId), eq(agentId));

        var result = controller.deleteRegistration(agentId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        verify(registrationService).deleteRegistration(userId, agentId);
    }

    @Test
    void deleteRegistration_nullUserDetails_returnsUnauthorized() {
        var agentId = UUID.randomUUID();

        var result = controller.deleteRegistration(agentId, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(registrationService, never()).deleteRegistration(any(), any());
    }

    @Test
    void deleteRegistration_notFound_returnsNotFound() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Registration not found"))
                .when(registrationService).deleteRegistration(eq(userId), eq(agentId));

        var result = controller.deleteRegistration(agentId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Agent not found");
    }

    @Test
    void deleteRegistration_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        var agentId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected error"))
                .when(registrationService).deleteRegistration(eq(userId), eq(agentId));

        var result = controller.deleteRegistration(agentId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to delete registration");
    }

    @Test
    void deleteRegistration_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var agentId = UUID.randomUUID();
        doNothing().when(registrationService).deleteRegistration(eq(specificUserId), eq(agentId));

        controller.deleteRegistration(agentId, user);

        verify(registrationService).deleteRegistration(eq(specificUserId), eq(agentId));
    }

    // ===== downloadAgentPackage (POST /api/agents/download/{platform}) =====

    @Test
    void downloadAgentPackage_nullUserDetails_returnsUnauthorized() {
        var result = controller.downloadAgentPackage("macos", null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void downloadAgentPackage_unsupportedPlatform_returnsBadRequest() {
        var user = testUser();
        var tokenResult = sampleTokenResult();
        when(registrationService.generateToken(eq(userId))).thenReturn(tokenResult);

        var result = controller.downloadAgentPackage("linux", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void downloadAgentPackage_serviceThrowsException_returnsInternalServerError() {
        var user = testUser();
        when(registrationService.generateToken(eq(userId)))
                .thenThrow(new RuntimeException("Error"));

        var result = controller.downloadAgentPackage("macos", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ===== Cross-cutting concerns =====

    @Test
    void registrationInfo_pendingStatus_hasNullOptionalFields() {
        var user = testUser();
        var reg = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .status(AgentStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(registrationService.getRegistrations(eq(userId))).thenReturn(List.of(reg));

        var result = controller.listRegistrations(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var registrations = (List<AgentRegistrationController.RegistrationInfo>) body.get("registrations");
        var info = registrations.get(0);
        assertThat(info.status()).isEqualTo("PENDING");
        assertThat(info.registeredAt()).isNull();
        assertThat(info.blockedAt()).isNull();
        assertThat(info.blockedReason()).isNull();
        assertThat(info.lastSeenAt()).isNull();
    }

    @Test
    void allAuthenticatedEndpoints_rejectNullUserDetails() {
        // generateToken
        assertThat(controller.generateToken(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // generateTokenJson
        assertThat(controller.generateTokenJson(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // generateInstallCommand
        assertThat(controller.generateInstallCommand(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // listRegistrations
        assertThat(controller.listRegistrations(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // blockAgent
        assertThat(controller.blockAgent(UUID.randomUUID(), null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // unblockAgent
        assertThat(controller.unblockAgent(UUID.randomUUID(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // deleteRegistration
        assertThat(controller.deleteRegistration(UUID.randomUUID(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // downloadAgentPackage
        assertThat(controller.downloadAgentPackage("macos", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
