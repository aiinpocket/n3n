package com.aiinpocket.n3n.gateway.controller;

import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import com.aiinpocket.n3n.gateway.security.DeviceKeyStore;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPairingControllerTest {

    @Mock
    private AgentPairingService pairingService;

    @Mock
    private DeviceKeyStore deviceKeyStore;

    @InjectMocks
    private AgentPairingController agentPairingController;

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

    private DeviceKeyStore.DeviceKey sampleDeviceKey(UUID userId) {
        Instant now = Instant.now();
        return DeviceKeyStore.DeviceKey.builder()
                .deviceId("device-abc")
                .userId(userId)
                .deviceName("My MacBook Pro")
                .platform("macos")
                .fingerprint("fp-abc123")
                .encryptKeyC2S("encKeyC2S")
                .encryptKeyS2C("encKeyS2C")
                .authKey("authKey")
                .lastSequence(0L)
                .pairedAt(now)
                .lastActiveAt(now)
                .externalAddress("192.168.1.100:8080")
                .directConnectionEnabled(true)
                .revoked(false)
                .build();
    }

    // ===== initiatePairing (POST /api/agent/pair/initiate) =====

    @Test
    void initiatePairing_success_returnsOkWithPairingCode() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Instant expiresAt = Instant.now().plusSeconds(300);
        var pairing = new AgentPairingService.PairingInitiation("ABC123", expiresAt);
        when(pairingService.initiatePairing(userId)).thenReturn(pairing);

        var result = agentPairingController.initiatePairing(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("pairingCode", "ABC123");
        assertThat(body).containsEntry("expiresAt", expiresAt.toEpochMilli());
        assertThat(body).containsEntry("expiresIn", 300);
        verify(pairingService).initiatePairing(userId);
    }

    @Test
    void initiatePairing_nullUser_returnsUnauthorized() {
        var result = agentPairingController.initiatePairing(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
        verify(pairingService, never()).initiatePairing(any());
    }

    @Test
    void initiatePairing_serviceThrowsException_returnsInternalServerError() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(pairingService.initiatePairing(userId)).thenThrow(new RuntimeException("Unexpected error"));

        var result = agentPairingController.initiatePairing(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to initiate pairing");
    }

    @Test
    void initiatePairing_parsesUserIdCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var pairing = new AgentPairingService.PairingInitiation("CODE", Instant.now().plusSeconds(300));
        when(pairingService.initiatePairing(userId)).thenReturn(pairing);

        agentPairingController.initiatePairing(user);

        verify(pairingService).initiatePairing(eq(userId));
    }

    // ===== completePairing (POST /api/agent/pair/complete) =====

    @Test
    void completePairing_success_returnsOkWithResult() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "ABC123", "device-1", "My Mac", "macos",
                "pubKey123", "fp-abc", "192.168.1.1:8080",
                true, List.of("192.168.1.0/24")
        );
        var pairingResult = new AgentPairingService.PairingResult(
                "platformPubKey", "platformFP", "deviceToken123", UUID.randomUUID()
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenReturn(pairingResult);

        var result = agentPairingController.completePairing(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("platformPublicKey", "platformPubKey");
        assertThat(body).containsEntry("platformFingerprint", "platformFP");
        assertThat(body).containsEntry("deviceToken", "deviceToken123");
    }

    @Test
    void completePairing_pairingException_returnsBadRequest() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "INVALID", "device-1", "My Mac", "macos",
                "pubKey", "fp", null, null, null
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenThrow(new AgentPairingService.PairingException("Invalid or expired pairing code"));

        var result = agentPairingController.completePairing(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Pairing failed");
    }

    @Test
    void completePairing_unexpectedException_returnsInternalServerError() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "ABC123", "device-1", "My Mac", "macos",
                "pubKey", "fp", null, null, null
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenThrow(new RuntimeException("Something crashed"));

        var result = agentPairingController.completePairing(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to complete pairing");
    }

    @Test
    void completePairing_constructsPairingRequestCorrectly() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "CODE99", "dev-99", "Test Device", "linux",
                "testPubKey", "testFP", "10.0.0.1:9090",
                true, List.of("10.0.0.0/8")
        );
        var pairingResult = new AgentPairingService.PairingResult(
                "key", "fp", "token", UUID.randomUUID()
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenReturn(pairingResult);

        agentPairingController.completePairing(request);

        verify(pairingService).completePairing(argThat(pr ->
                pr.pairingCode().equals("CODE99") &&
                pr.deviceId().equals("dev-99") &&
                pr.deviceName().equals("Test Device") &&
                pr.platform().equals("linux") &&
                pr.devicePublicKey().equals("testPubKey") &&
                pr.deviceFingerprint().equals("testFP") &&
                pr.externalAddress().equals("10.0.0.1:9090") &&
                pr.directConnectionEnabled() &&
                pr.allowedIps().equals(List.of("10.0.0.0/8"))
        ));
    }

    @Test
    void completePairing_nullDirectConnectionEnabled_defaultsToFalse() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "CODE", "dev-1", "Device", "macos",
                "pubKey", "fp", null, null, null
        );
        var pairingResult = new AgentPairingService.PairingResult(
                "key", "fp", "token", UUID.randomUUID()
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenReturn(pairingResult);

        agentPairingController.completePairing(request);

        verify(pairingService).completePairing(argThat(pr ->
                !pr.directConnectionEnabled()
        ));
    }

    @Test
    void completePairing_directConnectionEnabledTrue_passesTrue() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "CODE", "dev-1", "Device", "macos",
                "pubKey", "fp", "addr", true, null
        );
        var pairingResult = new AgentPairingService.PairingResult(
                "key", "fp", "token", UUID.randomUUID()
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenReturn(pairingResult);

        agentPairingController.completePairing(request);

        verify(pairingService).completePairing(argThat(pr ->
                pr.directConnectionEnabled()
        ));
    }

    // ===== listDevices (GET /api/agent/devices) =====

    @Test
    void listDevices_success_returnsOkWithDevices() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenReturn(List.of(deviceKey));

        var result = agentPairingController.listDevices(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsKey("devices");
        @SuppressWarnings("unchecked")
        var devices = (List<AgentPairingController.DeviceInfo>) body.get("devices");
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).deviceId()).isEqualTo("device-abc");
        assertThat(devices.get(0).deviceName()).isEqualTo("My MacBook Pro");
        assertThat(devices.get(0).platform()).isEqualTo("macos");
        assertThat(devices.get(0).directConnectionEnabled()).isTrue();
        assertThat(devices.get(0).externalAddress()).isEqualTo("192.168.1.100:8080");
        assertThat(devices.get(0).revoked()).isFalse();
        verify(deviceKeyStore).getDeviceKeysForUser(userId);
    }

    @Test
    void listDevices_emptyList_returnsOk() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenReturn(List.of());

        var result = agentPairingController.listDevices(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var devices = (List<AgentPairingController.DeviceInfo>) body.get("devices");
        assertThat(devices).isEmpty();
    }

    @Test
    void listDevices_nullUser_returnsUnauthorized() {
        var result = agentPairingController.listDevices(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
        verify(deviceKeyStore, never()).getDeviceKeysForUser(any());
    }

    @Test
    void listDevices_serviceThrowsException_returnsInternalServerError() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenThrow(new RuntimeException("Redis error"));

        var result = agentPairingController.listDevices(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to list devices");
    }

    @Test
    void listDevices_multipleDevices_returnsAll() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var device1 = sampleDeviceKey(userId);
        Instant now = Instant.now();
        var device2 = DeviceKeyStore.DeviceKey.builder()
                .deviceId("device-def")
                .userId(userId)
                .deviceName("My Windows PC")
                .platform("windows")
                .fingerprint("fp-def456")
                .encryptKeyC2S("encKeyC2S2")
                .encryptKeyS2C("encKeyS2C2")
                .authKey("authKey2")
                .lastSequence(5L)
                .pairedAt(now)
                .lastActiveAt(now)
                .directConnectionEnabled(false)
                .revoked(false)
                .build();
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenReturn(List.of(device1, device2));

        var result = agentPairingController.listDevices(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var devices = (List<AgentPairingController.DeviceInfo>) body.get("devices");
        assertThat(devices).hasSize(2);
    }

    @Test
    void listDevices_revokedDevice_showsRevokedTrue() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        deviceKey.setRevoked(true);
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenReturn(List.of(deviceKey));

        var result = agentPairingController.listDevices(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var devices = (List<AgentPairingController.DeviceInfo>) body.get("devices");
        assertThat(devices.get(0).revoked()).isTrue();
    }

    @Test
    void listDevices_mapsTimestampsToEpochMilli() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Instant pairedAt = Instant.parse("2026-01-15T10:00:00Z");
        Instant lastActive = Instant.parse("2026-02-10T12:00:00Z");
        var deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId("dev-time")
                .userId(userId)
                .deviceName("Time Test")
                .platform("linux")
                .fingerprint("fp")
                .pairedAt(pairedAt)
                .lastActiveAt(lastActive)
                .revoked(false)
                .build();
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenReturn(List.of(deviceKey));

        var result = agentPairingController.listDevices(user);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var devices = (List<AgentPairingController.DeviceInfo>) body.get("devices");
        assertThat(devices.get(0).pairedAt()).isEqualTo(pairedAt.toEpochMilli());
        assertThat(devices.get(0).lastActiveAt()).isEqualTo(lastActive.toEpochMilli());
    }

    // ===== updateDevice (PUT /api/agent/devices/{deviceId}) =====

    @Test
    void updateDevice_success_returnsOk() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:9090", true, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        var result = agentPairingController.updateDevice("device-abc", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        verify(pairingService).updateDeviceAddress("device-abc", "10.0.0.1:9090", true);
    }

    @Test
    void updateDevice_nullUser_returnsUnauthorized() {
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:9090", true, null
        );

        var result = agentPairingController.updateDevice("device-abc", request, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
        verify(pairingService, never()).updateDeviceAddress(any(), any(), anyBoolean());
    }

    @Test
    void updateDevice_deviceNotFound_returnsNotFound() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:9090", true, null
        );
        when(deviceKeyStore.getDeviceKey("nonexistent")).thenReturn(Optional.empty());

        var result = agentPairingController.updateDevice("nonexistent", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Device not found");
        verify(pairingService, never()).updateDeviceAddress(any(), any(), anyBoolean());
    }

    @Test
    void updateDevice_deviceBelongsToDifferentUser_returnsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(otherUserId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:9090", true, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        var result = agentPairingController.updateDevice("device-abc", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Device not found");
        verify(pairingService, never()).updateDeviceAddress(any(), any(), anyBoolean());
    }

    @Test
    void updateDevice_onlyExternalAddress_updatesAddress() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "192.168.1.200:8080", null, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        var result = agentPairingController.updateDevice("device-abc", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pairingService).updateDeviceAddress("device-abc", "192.168.1.200:8080", false);
    }

    @Test
    void updateDevice_onlyDirectConnection_updatesFlag() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                null, true, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        var result = agentPairingController.updateDevice("device-abc", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pairingService).updateDeviceAddress("device-abc", null, true);
    }

    @Test
    void updateDevice_bothFieldsNull_skipsUpdate() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                null, null, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        var result = agentPairingController.updateDevice("device-abc", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pairingService, never()).updateDeviceAddress(any(), any(), anyBoolean());
    }

    @Test
    void updateDevice_serviceThrowsException_returnsInternalServerError() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:9090", true, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));
        doThrow(new RuntimeException("Redis error"))
                .when(pairingService).updateDeviceAddress("device-abc", "10.0.0.1:9090", true);

        var result = agentPairingController.updateDevice("device-abc", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to update device");
    }

    @Test
    void updateDevice_directConnectionEnabledFalse_passesFalse() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(userId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "addr", false, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        agentPairingController.updateDevice("device-abc", request, user);

        verify(pairingService).updateDeviceAddress("device-abc", "addr", false);
    }

    // ===== unpairDevice (DELETE /api/agent/devices/{deviceId}) =====

    @Test
    void unpairDevice_success_returnsNoContent() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        var result = agentPairingController.unpairDevice("device-abc", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(pairingService).unpairDevice(userId, "device-abc");
    }

    @Test
    void unpairDevice_nullUser_returnsUnauthorized() {
        var result = agentPairingController.unpairDevice("device-abc", null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
        verify(pairingService, never()).unpairDevice(any(), any());
    }

    @Test
    void unpairDevice_deviceNotFound_returnsNotFound() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        doThrow(new IllegalArgumentException("Device not found: nonexistent"))
                .when(pairingService).unpairDevice(userId, "nonexistent");

        var result = agentPairingController.unpairDevice("nonexistent", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Device not found");
    }

    @Test
    void unpairDevice_accessDenied_returnsForbidden() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        doThrow(new SecurityException("Device does not belong to user"))
                .when(pairingService).unpairDevice(userId, "device-abc");

        var result = agentPairingController.unpairDevice("device-abc", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Access denied");
    }

    @Test
    void unpairDevice_unexpectedException_returnsInternalServerError() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        doThrow(new RuntimeException("Unexpected crash"))
                .when(pairingService).unpairDevice(userId, "device-abc");

        var result = agentPairingController.unpairDevice("device-abc", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to unpair device");
    }

    @Test
    void unpairDevice_parsesUserIdCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        agentPairingController.unpairDevice("device-abc", user);

        verify(pairingService).unpairDevice(eq(userId), eq("device-abc"));
    }

    // ===== revokeAllDevices (POST /api/agent/devices/revoke-all) =====

    @Test
    void revokeAllDevices_success_returnsOk() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        var result = agentPairingController.revokeAllDevices(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("success", true);
        verify(pairingService).revokeAllDevices(userId);
    }

    @Test
    void revokeAllDevices_nullUser_returnsUnauthorized() {
        var result = agentPairingController.revokeAllDevices(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Authentication required");
        verify(pairingService, never()).revokeAllDevices(any());
    }

    @Test
    void revokeAllDevices_serviceThrowsException_returnsInternalServerError() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        doThrow(new RuntimeException("Redis error"))
                .when(pairingService).revokeAllDevices(userId);

        var result = agentPairingController.revokeAllDevices(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("error", "Failed to revoke devices");
    }

    @Test
    void revokeAllDevices_parsesUserIdCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        agentPairingController.revokeAllDevices(user);

        verify(pairingService).revokeAllDevices(eq(userId));
    }

    // ===== DTO record tests =====

    @Test
    void pairCompleteRequest_allFields() {
        var request = new AgentPairingController.PairCompleteRequest(
                "code", "devId", "devName", "macos",
                "pubKey", "fp", "addr", true, List.of("ip1", "ip2")
        );

        assertThat(request.pairingCode()).isEqualTo("code");
        assertThat(request.deviceId()).isEqualTo("devId");
        assertThat(request.deviceName()).isEqualTo("devName");
        assertThat(request.platform()).isEqualTo("macos");
        assertThat(request.devicePublicKey()).isEqualTo("pubKey");
        assertThat(request.deviceFingerprint()).isEqualTo("fp");
        assertThat(request.externalAddress()).isEqualTo("addr");
        assertThat(request.directConnectionEnabled()).isTrue();
        assertThat(request.allowedIps()).containsExactly("ip1", "ip2");
    }

    @Test
    void deviceUpdateRequest_allFields() {
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:8080", true, List.of("10.0.0.0/8")
        );

        assertThat(request.externalAddress()).isEqualTo("10.0.0.1:8080");
        assertThat(request.directConnectionEnabled()).isTrue();
        assertThat(request.allowedIps()).containsExactly("10.0.0.0/8");
    }

    @Test
    void deviceInfo_allFields() {
        var info = new AgentPairingController.DeviceInfo(
                "dev-1", "My Mac", "macos",
                1700000000000L, 1700001000000L,
                true, "192.168.1.100:8080", false
        );

        assertThat(info.deviceId()).isEqualTo("dev-1");
        assertThat(info.deviceName()).isEqualTo("My Mac");
        assertThat(info.platform()).isEqualTo("macos");
        assertThat(info.pairedAt()).isEqualTo(1700000000000L);
        assertThat(info.lastActiveAt()).isEqualTo(1700001000000L);
        assertThat(info.directConnectionEnabled()).isTrue();
        assertThat(info.externalAddress()).isEqualTo("192.168.1.100:8080");
        assertThat(info.revoked()).isFalse();
    }

    // ===== Edge cases =====

    @Test
    void completePairing_withDirectConnectionEnabledFalse_passesFalse() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "CODE", "dev-1", "Device", "macos",
                "pubKey", "fp", null, false, null
        );
        var pairingResult = new AgentPairingService.PairingResult(
                "key", "fp", "token", UUID.randomUUID()
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenReturn(pairingResult);

        agentPairingController.completePairing(request);

        verify(pairingService).completePairing(argThat(pr ->
                !pr.directConnectionEnabled()
        ));
    }

    @Test
    void updateDevice_verifiesOwnershipBeforeUpdating() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var deviceKey = sampleDeviceKey(otherUserId);
        var request = new AgentPairingController.DeviceUpdateRequest(
                "10.0.0.1:9090", true, null
        );
        when(deviceKeyStore.getDeviceKey("device-abc")).thenReturn(Optional.of(deviceKey));

        agentPairingController.updateDevice("device-abc", request, user);

        verify(pairingService, never()).updateDeviceAddress(any(), any(), anyBoolean());
    }

    @Test
    void listDevices_deviceWithNullExternalAddress_handledCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Instant now = Instant.now();
        var deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId("dev-null-addr")
                .userId(userId)
                .deviceName("No Address Device")
                .platform("macos")
                .fingerprint("fp")
                .pairedAt(now)
                .lastActiveAt(now)
                .directConnectionEnabled(false)
                .externalAddress(null)
                .revoked(false)
                .build();
        when(deviceKeyStore.getDeviceKeysForUser(userId)).thenReturn(List.of(deviceKey));

        var result = agentPairingController.listDevices(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) result.getBody();
        @SuppressWarnings("unchecked")
        var devices = (List<AgentPairingController.DeviceInfo>) body.get("devices");
        assertThat(devices.get(0).externalAddress()).isNull();
        assertThat(devices.get(0).directConnectionEnabled()).isFalse();
    }

    @Test
    void completePairing_withAllowedIps_passesThrough() throws Exception {
        var request = new AgentPairingController.PairCompleteRequest(
                "CODE", "dev-1", "Device", "macos",
                "pubKey", "fp", "addr", true,
                List.of("192.168.1.0/24", "10.0.0.0/8")
        );
        var pairingResult = new AgentPairingService.PairingResult(
                "key", "fp", "token", UUID.randomUUID()
        );
        when(pairingService.completePairing(any(AgentPairingService.PairingRequest.class)))
                .thenReturn(pairingResult);

        agentPairingController.completePairing(request);

        verify(pairingService).completePairing(argThat(pr ->
                pr.allowedIps() != null &&
                pr.allowedIps().size() == 2 &&
                pr.allowedIps().contains("192.168.1.0/24") &&
                pr.allowedIps().contains("10.0.0.0/8")
        ));
    }
}
