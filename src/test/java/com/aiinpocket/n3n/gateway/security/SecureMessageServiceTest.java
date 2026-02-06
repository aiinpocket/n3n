package com.aiinpocket.n3n.gateway.security;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SecureMessageServiceTest extends BaseServiceTest {

    @Mock
    private AgentCrypto agentCrypto;

    @Mock
    private DeviceKeyStore deviceKeyStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SecureMessageService secureMessageService;

    @BeforeEach
    void setUp() {
        secureMessageService = new SecureMessageService(agentCrypto, deviceKeyStore, objectMapper);
    }

    // ========== Encrypt Tests ==========

    @Test
    void encrypt_validDevice_returnsEncryptedMessage() throws Exception {
        // Given
        String deviceId = "device-123";
        Map<String, Object> payload = Map.of("type", "command", "action", "screenshot");

        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, false);
        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        byte[] nonce = new byte[12];
        byte[] ciphertext = "encrypted-data".getBytes(StandardCharsets.UTF_8);
        byte[] tag = new byte[16];

        when(agentCrypto.encrypt(any(byte[].class), any(byte[].class), any()))
                .thenReturn(new AgentCrypto.EncryptedMessage(nonce, ciphertext, tag));

        // When
        String result = secureMessageService.encrypt(deviceId, payload);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("."); // Compact format has dots
        verify(agentCrypto, times(2)).encrypt(any(byte[].class), any(byte[].class), any());
    }

    @Test
    void encrypt_unknownDevice_throwsException() {
        // Given
        when(deviceKeyStore.getDeviceKey("unknown")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> secureMessageService.encrypt("unknown", Map.of("test", "data")))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("Unknown device");
    }

    @Test
    void encrypt_revokedDevice_throwsException() {
        // Given
        String deviceId = "revoked-device";
        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, true);
        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When/Then
        assertThatThrownBy(() -> secureMessageService.encrypt(deviceId, Map.of("test", "data")))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("revoked");
    }

    // ========== Decrypt Tests ==========

    @Test
    void decrypt_validMessage_returnsDecryptedPayload() throws Exception {
        // Given
        String deviceId = "device-123";
        UUID userId = UUID.randomUUID();
        long currentTime = System.currentTimeMillis();

        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, false);
        deviceKey.setUserId(userId);
        deviceKey.setLastSequence(100);

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1)
                .alg("A256GCM")
                .did(deviceId)
                .ts(currentTime)
                .seq(101)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s")
                .build();

        String headerJson = objectMapper.writeValueAsString(header);
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String ciphertextB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("encrypted".getBytes(StandardCharsets.UTF_8));
        String tagB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[16]);

        String compactMessage = headerB64 + "." + ciphertextB64 + "." + tagB64;

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        byte[] decryptedBytes = objectMapper.writeValueAsBytes(Map.of("result", "ok"));
        when(agentCrypto.decrypt(any(), any(), any(), any(), any())).thenReturn(decryptedBytes);

        // When
        @SuppressWarnings("unchecked")
        SecureMessageService.DecryptedMessage<Map> result =
                secureMessageService.decrypt(compactMessage, Map.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.sequence()).isEqualTo(101);
        verify(deviceKeyStore).updateSequence(deviceId, 101);
    }

    @Test
    void decrypt_unknownDevice_throwsException() throws Exception {
        // Given
        String deviceId = "unknown-device";
        long currentTime = System.currentTimeMillis();

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(currentTime).seq(1)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> secureMessageService.decrypt(compactMessage, Map.class))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("Unknown device");
    }

    @Test
    void decrypt_expiredMessage_throwsException() throws Exception {
        // Given
        String deviceId = "device-123";
        long oldTimestamp = System.currentTimeMillis() - (6 * 60 * 1000); // 6 minutes ago

        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, false);
        deviceKey.setLastSequence(100);

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(oldTimestamp).seq(101)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When/Then
        assertThatThrownBy(() -> secureMessageService.decrypt(compactMessage, Map.class))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void decrypt_replayedSequence_throwsException() throws Exception {
        // Given
        String deviceId = "device-123";
        long currentTime = System.currentTimeMillis();

        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, false);
        deviceKey.setLastSequence(200); // Last sequence is 200

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(currentTime).seq(150) // Sequence less than last
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When/Then
        assertThatThrownBy(() -> secureMessageService.decrypt(compactMessage, Map.class))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("Replay detected");
    }

    @Test
    void decrypt_unsupportedProtocolVersion_throwsException() throws Exception {
        // Given
        String deviceId = "device-123";

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(99).alg("A256GCM").did(deviceId)
                .ts(System.currentTimeMillis()).seq(1)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        // When/Then
        assertThatThrownBy(() -> secureMessageService.decrypt(compactMessage, Map.class))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("Unsupported protocol version");
    }

    @Test
    void decrypt_revokedDevice_throwsException() throws Exception {
        // Given
        String deviceId = "revoked-device";
        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, true);

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(System.currentTimeMillis()).seq(1)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When/Then
        assertThatThrownBy(() -> secureMessageService.decrypt(compactMessage, Map.class))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void decrypt_invalidFormat_throwsException() {
        // Given - invalid compact message format
        String invalidMessage = "not-a-valid-message";

        // When/Then
        assertThatThrownBy(() -> secureMessageService.decrypt(invalidMessage, Map.class))
                .isInstanceOf(SecureMessageService.SecureMessageException.class)
                .hasMessageContaining("Invalid message format");
    }

    // ========== Verify Tests ==========

    @Test
    void verify_validMessage_returnsValid() throws Exception {
        // Given
        String deviceId = "device-123";
        UUID userId = UUID.randomUUID();

        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, false);
        deviceKey.setUserId(userId);
        deviceKey.setLastSequence(0);

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(System.currentTimeMillis()).seq(1)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        SecureMessageService.VerificationResult result = secureMessageService.verify(compactMessage);

        // Then
        assertThat(result.valid()).isTrue();
        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.error()).isNull();
    }

    @Test
    void verify_unknownDevice_returnsInvalid() throws Exception {
        // Given
        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did("unknown")
                .ts(System.currentTimeMillis()).seq(1)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey("unknown")).thenReturn(Optional.empty());

        // When
        SecureMessageService.VerificationResult result = secureMessageService.verify(compactMessage);

        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo("Unknown device");
    }

    @Test
    void verify_revokedDevice_returnsInvalid() throws Exception {
        // Given
        String deviceId = "revoked";
        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, true);

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(System.currentTimeMillis()).seq(1)
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        SecureMessageService.VerificationResult result = secureMessageService.verify(compactMessage);

        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo("Device revoked");
    }

    @Test
    void verify_replayDetected_returnsInvalid() throws Exception {
        // Given
        String deviceId = "device-123";
        DeviceKeyStore.DeviceKey deviceKey = createDeviceKey(deviceId, false);
        deviceKey.setLastSequence(100);

        SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1).alg("A256GCM").did(deviceId)
                .ts(System.currentTimeMillis()).seq(50) // Less than last
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]))
                .dir("c2s").build();

        String compactMessage = buildCompactMessage(header);

        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        SecureMessageService.VerificationResult result = secureMessageService.verify(compactMessage);

        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo("Replay detected");
    }

    // ========== Helper Methods ==========

    private DeviceKeyStore.DeviceKey createDeviceKey(String deviceId, boolean revoked) {
        return DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(UUID.randomUUID())
                .deviceName("Test Device")
                .platform("macos")
                .encryptKeyC2S(Base64.getEncoder().encodeToString(new byte[32]))
                .encryptKeyS2C(Base64.getEncoder().encodeToString(new byte[32]))
                .authKey(Base64.getEncoder().encodeToString(new byte[32]))
                .lastSequence(0)
                .revoked(revoked)
                .build();
    }

    private String buildCompactMessage(SecureMessage.Header header) {
        try {
            String headerJson = objectMapper.writeValueAsString(header);
            String headerB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String ciphertextB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("test-ciphertext".getBytes(StandardCharsets.UTF_8));
            String tagB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new byte[16]);
            return headerB64 + "." + ciphertextB64 + "." + tagB64;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
