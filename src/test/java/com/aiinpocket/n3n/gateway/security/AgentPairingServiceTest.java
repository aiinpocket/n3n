package com.aiinpocket.n3n.gateway.security;

import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentPairingServiceTest extends BaseServiceTest {

    @Mock
    private AgentCrypto agentCrypto;

    @Mock
    private DeviceKeyStore deviceKeyStore;

    @InjectMocks
    private AgentPairingService agentPairingService;

    // ========== Initiate Pairing Tests ==========

    @Test
    void initiatePairing_validUserId_returnsPairingInitiation() {
        // Given
        UUID userId = UUID.randomUUID();
        when(agentCrypto.generatePairingCode()).thenReturn("123456");
        when(agentCrypto.generatePairingSecret()).thenReturn(new byte[32]);

        // When
        AgentPairingService.PairingInitiation result = agentPairingService.initiatePairing(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.pairingCode()).isEqualTo("123456");
        assertThat(result.expiresAt()).isAfter(Instant.now());
        verify(deviceKeyStore).storePairing(eq("123456"), any(DeviceKeyStore.PairingRequest.class));
    }

    @Test
    void initiatePairing_storesPairingWithCorrectExpiry() {
        // Given
        UUID userId = UUID.randomUUID();
        when(agentCrypto.generatePairingCode()).thenReturn("654321");
        when(agentCrypto.generatePairingSecret()).thenReturn(new byte[32]);

        // When
        agentPairingService.initiatePairing(userId);

        // Then
        verify(deviceKeyStore).storePairing(eq("654321"), argThat(req -> {
            long secondsUntilExpiry = req.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            return req.getUserId().equals(userId)
                    && secondsUntilExpiry > 290 && secondsUntilExpiry <= 300;
        }));
    }

    // ========== Complete Pairing Tests ==========

    @Test
    void completePairing_validCode_returnsResult() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String deviceId = "device-123";

        DeviceKeyStore.PairingRequest storedPairing = DeviceKeyStore.PairingRequest.builder()
                .userId(userId)
                .pairingSecret(Base64.getEncoder().encodeToString(new byte[32]))
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(240))
                .build();

        KeyPair keyPair = mockKeyPair();

        when(deviceKeyStore.consumePairing("123456")).thenReturn(Optional.of(storedPairing));
        when(agentCrypto.generateKeyPair()).thenReturn(keyPair);
        when(agentCrypto.parsePublicKey(any())).thenReturn(keyPair.getPublic());
        when(agentCrypto.deriveSharedSecret(any(PrivateKey.class), any(PublicKey.class))).thenReturn(new byte[32]);
        when(agentCrypto.deriveKeys(any(), any(), any())).thenReturn(
                new AgentCrypto.DerivedKeys(new byte[32], new byte[32], new byte[32]));
        when(agentCrypto.computeFingerprint(any())).thenReturn("platform-fingerprint");
        when(agentCrypto.encodePublicKey(any())).thenReturn("encoded-public-key");
        when(agentCrypto.generateInitialSequence()).thenReturn(1000L);

        AgentPairingService.PairingRequest request = new AgentPairingService.PairingRequest(
                "123456", deviceId, "My Mac", "macos",
                "device-public-key", "device-fingerprint",
                "192.168.1.100", true, List.of("192.168.1.0/24")
        );

        // When
        AgentPairingService.PairingResult result = agentPairingService.completePairing(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.platformPublicKey()).isEqualTo("encoded-public-key");
        assertThat(result.platformFingerprint()).isEqualTo("platform-fingerprint");
        assertThat(result.deviceToken()).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        verify(deviceKeyStore).storeDeviceKey(any(DeviceKeyStore.DeviceKey.class));
    }

    @Test
    void completePairing_invalidCode_throwsPairingException() {
        // Given
        when(deviceKeyStore.consumePairing("invalid")).thenReturn(Optional.empty());

        AgentPairingService.PairingRequest request = new AgentPairingService.PairingRequest(
                "invalid", "device-1", "Mac", "macos",
                "key", "fp", null, false, null
        );

        // When/Then
        assertThatThrownBy(() -> agentPairingService.completePairing(request))
                .isInstanceOf(AgentPairingService.PairingException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void completePairing_expiredCode_throwsPairingException() {
        // Given
        DeviceKeyStore.PairingRequest storedPairing = DeviceKeyStore.PairingRequest.builder()
                .userId(UUID.randomUUID())
                .pairingSecret(Base64.getEncoder().encodeToString(new byte[32]))
                .createdAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(300)) // Already expired
                .build();

        when(deviceKeyStore.consumePairing("expired")).thenReturn(Optional.of(storedPairing));

        AgentPairingService.PairingRequest request = new AgentPairingService.PairingRequest(
                "expired", "device-1", "Mac", "macos",
                "key", "fp", null, false, null
        );

        // When/Then
        assertThatThrownBy(() -> agentPairingService.completePairing(request))
                .isInstanceOf(AgentPairingService.PairingException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void completePairing_cryptoError_throwsPairingException() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();

        DeviceKeyStore.PairingRequest storedPairing = DeviceKeyStore.PairingRequest.builder()
                .userId(userId)
                .pairingSecret(Base64.getEncoder().encodeToString(new byte[32]))
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(240))
                .build();

        when(deviceKeyStore.consumePairing("123456")).thenReturn(Optional.of(storedPairing));
        when(agentCrypto.generateKeyPair()).thenThrow(new GeneralSecurityException("Crypto error"));

        AgentPairingService.PairingRequest request = new AgentPairingService.PairingRequest(
                "123456", "device-1", "Mac", "macos",
                "key", "fp", null, false, null
        );

        // When/Then
        assertThatThrownBy(() -> agentPairingService.completePairing(request))
                .isInstanceOf(AgentPairingService.PairingException.class)
                .hasMessageContaining("Cryptographic error");
    }

    // ========== Validate Device Token Tests ==========

    @Test
    void validateDeviceToken_validToken_returnsUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        String deviceId = "device-123";
        String tokenData = userId.toString() + ":" + deviceId + ":12345678:abcdef1234567890";
        String token = Base64.getEncoder().encodeToString(tokenData.getBytes());

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(userId)
                .revoked(false)
                .build();

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        Optional<UUID> result = agentPairingService.validateDeviceToken(token);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(userId);
    }

    @Test
    void validateDeviceToken_revokedDevice_returnsEmpty() {
        // Given
        UUID userId = UUID.randomUUID();
        String deviceId = "device-123";
        String tokenData = userId.toString() + ":" + deviceId + ":12345678:abcdef1234567890";
        String token = Base64.getEncoder().encodeToString(tokenData.getBytes());

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(userId)
                .revoked(true)
                .build();

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        Optional<UUID> result = agentPairingService.validateDeviceToken(token);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void validateDeviceToken_unknownDevice_returnsEmpty() {
        // Given
        UUID userId = UUID.randomUUID();
        String tokenData = userId.toString() + ":unknown-device:12345678:abcdef1234567890";
        String token = Base64.getEncoder().encodeToString(tokenData.getBytes());

        when(deviceKeyStore.getDeviceKey("unknown-device")).thenReturn(Optional.empty());

        // When
        Optional<UUID> result = agentPairingService.validateDeviceToken(token);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void validateDeviceToken_invalidFormat_returnsEmpty() {
        // Given - token with fewer than 4 parts
        String tokenData = "invalid:format";
        String token = Base64.getEncoder().encodeToString(tokenData.getBytes());

        // When
        Optional<UUID> result = agentPairingService.validateDeviceToken(token);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void validateDeviceToken_userIdMismatch_returnsEmpty() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String deviceId = "device-123";
        String tokenData = userId.toString() + ":" + deviceId + ":12345678:abcdef1234567890";
        String token = Base64.getEncoder().encodeToString(tokenData.getBytes());

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(otherUserId) // Different user
                .revoked(false)
                .build();

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        Optional<UUID> result = agentPairingService.validateDeviceToken(token);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== Unpair Device Tests ==========

    @Test
    void unpairDevice_ownedDevice_deletesKey() {
        // Given
        UUID userId = UUID.randomUUID();
        String deviceId = "device-123";

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(userId)
                .build();

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        agentPairingService.unpairDevice(userId, deviceId);

        // Then
        verify(deviceKeyStore).deleteDeviceKey(deviceId);
    }

    @Test
    void unpairDevice_notOwnedDevice_throwsSecurityException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String deviceId = "device-123";

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(otherUserId)
                .build();

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When/Then
        assertThatThrownBy(() -> agentPairingService.unpairDevice(userId, deviceId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void unpairDevice_unknownDevice_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(deviceKeyStore.getDeviceKey("unknown")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> agentPairingService.unpairDevice(userId, "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device not found");
    }

    // ========== Revoke All Devices Tests ==========

    @Test
    void revokeAllDevices_delegatesToKeyStore() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        agentPairingService.revokeAllDevices(userId);

        // Then
        verify(deviceKeyStore).revokeAllForUser(userId);
    }

    // ========== Helper Methods ==========

    private KeyPair mockKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("X25519");
        return keyGen.generateKeyPair();
    }
}
