package com.aiinpocket.n3n.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for encrypting and decrypting agent messages.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecureMessageService {

    private static final long MAX_TIME_DRIFT_MS = 5 * 60 * 1000; // 5 minutes

    private final AgentCrypto agentCrypto;
    private final DeviceKeyStore deviceKeyStore;
    private final ObjectMapper objectMapper;

    // Sequence counter for outgoing messages
    private final AtomicLong outgoingSequence = new AtomicLong(System.currentTimeMillis());

    /**
     * Encrypt a message to send to a device.
     */
    public String encrypt(String deviceId, Object payload) throws SecureMessageException {
        DeviceKeyStore.DeviceKey deviceKey = deviceKeyStore.getDeviceKey(deviceId)
            .orElseThrow(() -> new SecureMessageException("Unknown device: " + deviceId));

        if (deviceKey.isRevoked()) {
            throw new SecureMessageException("Device has been revoked: " + deviceId);
        }

        try {
            // Serialize payload
            byte[] plaintext = objectMapper.writeValueAsBytes(payload);

            // Get encryption key (server to client)
            byte[] encryptKey = Base64.getDecoder().decode(deviceKey.getEncryptKeyS2C());

            // Encrypt
            AgentCrypto.EncryptedMessage encrypted = agentCrypto.encrypt(plaintext, encryptKey, null);

            // Build header
            SecureMessage.Header header = SecureMessage.Header.builder()
                .v(1)
                .alg("A256GCM")
                .did(deviceId)
                .ts(System.currentTimeMillis())
                .seq(outgoingSequence.incrementAndGet())
                .nonce(Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.nonce()))
                .dir("s2c")
                .build();

            // Re-encrypt with AAD (header)
            byte[] headerBytes = objectMapper.writeValueAsBytes(header);
            encrypted = agentCrypto.encrypt(plaintext, encryptKey, headerBytes);

            // Build secure message
            SecureMessage message = SecureMessage.builder()
                .header(header)
                .ciphertext(Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.ciphertext()))
                .tag(Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.tag()))
                .build();

            // Update header with actual nonce used
            message.getHeader().setNonce(
                Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.nonce())
            );

            return message.toCompact(objectMapper);

        } catch (Exception e) {
            throw new SecureMessageException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypt and verify a message from a device.
     */
    public <T> DecryptedMessage<T> decrypt(String encryptedMessage, Class<T> payloadType)
            throws SecureMessageException {

        SecureMessage message;
        try {
            message = SecureMessage.fromCompact(encryptedMessage, objectMapper);
        } catch (Exception e) {
            throw new SecureMessageException("Invalid message format: " + e.getMessage(), e);
        }

        SecureMessage.Header header = message.getHeader();

        // 1. Validate protocol version
        if (header.getV() != 1) {
            throw new SecureMessageException("Unsupported protocol version: " + header.getV());
        }

        // 2. Get device key
        DeviceKeyStore.DeviceKey deviceKey = deviceKeyStore.getDeviceKey(header.getDid())
            .orElseThrow(() -> new SecureMessageException("Unknown device: " + header.getDid()));

        if (deviceKey.isRevoked()) {
            throw new SecureMessageException("Device has been revoked: " + header.getDid());
        }

        // 3. Validate timestamp (prevent replay with old messages)
        long now = System.currentTimeMillis();
        long timeDrift = Math.abs(now - header.getTs());
        if (timeDrift > MAX_TIME_DRIFT_MS) {
            throw new SecureMessageException("Message expired or clock drift too large: " + timeDrift + "ms");
        }

        // 4. Validate sequence (prevent replay within time window)
        if (header.getSeq() <= deviceKey.getLastSequence()) {
            throw new SecureMessageException("Replay detected: sequence " + header.getSeq() +
                " <= last " + deviceKey.getLastSequence());
        }

        try {
            // 5. Get decryption key (client to server)
            byte[] decryptKey = Base64.getDecoder().decode(deviceKey.getEncryptKeyC2S());

            // 6. Get AAD (header bytes)
            byte[] headerBytes = message.getHeaderBytes(objectMapper);

            // 7. Decrypt
            byte[] plaintext = agentCrypto.decrypt(
                message.getCiphertextBytes(),
                message.getTagBytes(),
                decryptKey,
                message.getNonceBytes(),
                headerBytes
            );

            // 8. Update sequence number
            deviceKeyStore.updateSequence(header.getDid(), header.getSeq());

            // 9. Parse payload
            T payload = objectMapper.readValue(plaintext, payloadType);

            log.debug("Successfully decrypted message from device: {}", header.getDid());

            return new DecryptedMessage<>(
                header.getDid(),
                deviceKey.getUserId(),
                header.getTs(),
                header.getSeq(),
                payload
            );

        } catch (GeneralSecurityException e) {
            throw new SecureMessageException("Decryption failed (invalid key or tampered): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new SecureMessageException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verify message authenticity without decrypting (for initial validation).
     */
    public VerificationResult verify(String encryptedMessage) {
        try {
            SecureMessage message = SecureMessage.fromCompact(encryptedMessage, objectMapper);
            SecureMessage.Header header = message.getHeader();

            // Basic validation
            if (header.getV() != 1) {
                return VerificationResult.invalid("Unsupported protocol version");
            }

            // Check device exists
            var deviceKey = deviceKeyStore.getDeviceKey(header.getDid());
            if (deviceKey.isEmpty()) {
                return VerificationResult.invalid("Unknown device");
            }

            if (deviceKey.get().isRevoked()) {
                return VerificationResult.invalid("Device revoked");
            }

            // Check timestamp
            long now = System.currentTimeMillis();
            long timeDrift = Math.abs(now - header.getTs());
            if (timeDrift > MAX_TIME_DRIFT_MS) {
                return VerificationResult.invalid("Message expired");
            }

            // Check sequence
            if (header.getSeq() <= deviceKey.get().getLastSequence()) {
                return VerificationResult.invalid("Replay detected");
            }

            return VerificationResult.valid(header.getDid(), deviceKey.get().getUserId());

        } catch (Exception e) {
            return VerificationResult.invalid("Parse error: " + e.getMessage());
        }
    }

    // Result classes

    public record DecryptedMessage<T>(
        String deviceId,
        java.util.UUID userId,
        long timestamp,
        long sequence,
        T payload
    ) {}

    public record VerificationResult(
        boolean valid,
        String deviceId,
        java.util.UUID userId,
        String error
    ) {
        public static VerificationResult valid(String deviceId, java.util.UUID userId) {
            return new VerificationResult(true, deviceId, userId, null);
        }

        public static VerificationResult invalid(String error) {
            return new VerificationResult(false, null, null, error);
        }
    }

    public static class SecureMessageException extends Exception {
        public SecureMessageException(String message) {
            super(message);
        }

        public SecureMessageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
