package com.aiinpocket.n3n.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling device pairing with the platform.
 * Implements X25519 key exchange for secure communication.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentPairingService {

    private final AgentCrypto agentCrypto;
    private final DeviceKeyStore deviceKeyStore;

    /**
     * Initiate a pairing request (called by platform for logged-in user)
     */
    public PairingInitiation initiatePairing(UUID userId) {
        String pairingCode = agentCrypto.generatePairingCode();
        byte[] pairingSecret = agentCrypto.generatePairingSecret();

        Instant now = Instant.now();
        DeviceKeyStore.PairingRequest request = DeviceKeyStore.PairingRequest.builder()
            .userId(userId)
            .pairingSecret(Base64.getEncoder().encodeToString(pairingSecret))
            .createdAt(now)
            .expiresAt(now.plusSeconds(300)) // 5 minutes
            .build();

        deviceKeyStore.storePairing(pairingCode, request);

        log.info("Pairing initiated for user {}: code={}", userId, pairingCode);

        return new PairingInitiation(pairingCode, now.plusSeconds(300));
    }

    /**
     * Complete the pairing process (called by agent with pairing code)
     */
    public PairingResult completePairing(PairingRequest request) throws PairingException {
        // 1. Validate and consume pairing code
        Optional<DeviceKeyStore.PairingRequest> storedRequest =
            deviceKeyStore.consumePairing(request.pairingCode());

        if (storedRequest.isEmpty()) {
            throw new PairingException("Invalid or expired pairing code");
        }

        DeviceKeyStore.PairingRequest pairing = storedRequest.get();

        // 2. Check expiration
        if (pairing.getExpiresAt().isBefore(Instant.now())) {
            throw new PairingException("Pairing code has expired");
        }

        try {
            // 3. Generate platform key pair
            KeyPair platformKeyPair = agentCrypto.generateKeyPair();

            // 4. Parse device public key
            var devicePublicKey = agentCrypto.parsePublicKey(request.devicePublicKey());

            // 5. Derive shared secret
            byte[] sharedSecret = agentCrypto.deriveSharedSecret(
                platformKeyPair.getPrivate(),
                devicePublicKey
            );

            // 6. Derive encryption keys
            byte[] salt = (request.deviceId() + pairing.getUserId().toString()).getBytes();
            byte[] info = "n3n-agent-v1".getBytes();
            AgentCrypto.DerivedKeys derivedKeys = agentCrypto.deriveKeys(sharedSecret, salt, info);

            // 7. Compute fingerprints for verification
            String platformFingerprint = agentCrypto.computeFingerprint(
                platformKeyPair.getPublic().getEncoded()
            );
            String deviceFingerprint = request.deviceFingerprint();

            // 8. Generate device token
            String deviceToken = generateDeviceToken(pairing.getUserId(), request.deviceId());

            // 9. Store device keys
            Instant now = Instant.now();
            DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(request.deviceId())
                .userId(pairing.getUserId())
                .deviceName(request.deviceName())
                .platform(request.platform())
                .fingerprint(deviceFingerprint)
                .encryptKeyC2S(Base64.getEncoder().encodeToString(derivedKeys.encryptKeyClientToServer()))
                .encryptKeyS2C(Base64.getEncoder().encodeToString(derivedKeys.encryptKeyServerToClient()))
                .authKey(Base64.getEncoder().encodeToString(derivedKeys.authKey()))
                .lastSequence(agentCrypto.generateInitialSequence())
                .pairedAt(now)
                .lastActiveAt(now)
                .externalAddress(request.externalAddress())
                .directConnectionEnabled(request.directConnectionEnabled())
                .allowedIps(request.allowedIps())
                .revoked(false)
                .build();

            deviceKeyStore.storeDeviceKey(deviceKey);

            log.info("Pairing completed: deviceId={}, userId={}", request.deviceId(), pairing.getUserId());

            return new PairingResult(
                agentCrypto.encodePublicKey(platformKeyPair.getPublic()),
                platformFingerprint,
                deviceToken,
                pairing.getUserId()
            );

        } catch (GeneralSecurityException e) {
            log.error("Cryptographic error during pairing: {}", e.getMessage(), e);
            throw new PairingException("Cryptographic operation failed");
        }
    }

    /**
     * Complete registration using a one-time token (for one-click install)
     */
    public PairingResult completeTokenRegistration(
            UUID userId,
            String deviceId,
            String deviceName,
            String platform,
            String devicePublicKey,
            String deviceFingerprint
    ) throws PairingException {
        try {
            // 1. Generate platform key pair
            KeyPair platformKeyPair = agentCrypto.generateKeyPair();

            // 2. Parse device public key
            var parsedDevicePublicKey = agentCrypto.parsePublicKey(devicePublicKey);

            // 3. Derive shared secret
            byte[] sharedSecret = agentCrypto.deriveSharedSecret(
                platformKeyPair.getPrivate(),
                parsedDevicePublicKey
            );

            // 4. Derive encryption keys
            byte[] salt = (deviceId + userId.toString()).getBytes();
            byte[] info = "n3n-agent-v1".getBytes();
            AgentCrypto.DerivedKeys derivedKeys = agentCrypto.deriveKeys(sharedSecret, salt, info);

            // 5. Compute platform fingerprint
            String platformFingerprint = agentCrypto.computeFingerprint(
                platformKeyPair.getPublic().getEncoded()
            );

            // 6. Generate device token
            String deviceToken = generateDeviceToken(userId, deviceId);

            // 7. Store device keys
            Instant now = Instant.now();
            DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(userId)
                .deviceName(deviceName)
                .platform(platform)
                .fingerprint(deviceFingerprint)
                .encryptKeyC2S(Base64.getEncoder().encodeToString(derivedKeys.encryptKeyClientToServer()))
                .encryptKeyS2C(Base64.getEncoder().encodeToString(derivedKeys.encryptKeyServerToClient()))
                .authKey(Base64.getEncoder().encodeToString(derivedKeys.authKey()))
                .lastSequence(agentCrypto.generateInitialSequence())
                .pairedAt(now)
                .lastActiveAt(now)
                .revoked(false)
                .build();

            deviceKeyStore.storeDeviceKey(deviceKey);

            log.info("Token registration completed: deviceId={}, userId={}", deviceId, userId);

            return new PairingResult(
                agentCrypto.encodePublicKey(platformKeyPair.getPublic()),
                platformFingerprint,
                deviceToken,
                userId
            );

        } catch (GeneralSecurityException e) {
            log.error("Cryptographic error during token registration: {}", e.getMessage(), e);
            throw new PairingException("Cryptographic operation failed");
        }
    }

    /**
     * Unpair a device
     */
    public void unpairDevice(UUID userId, String deviceId) {
        Optional<DeviceKeyStore.DeviceKey> deviceKey = deviceKeyStore.getDeviceKey(deviceId);

        if (deviceKey.isEmpty()) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        if (!deviceKey.get().getUserId().equals(userId)) {
            throw new SecurityException("Device does not belong to user");
        }

        deviceKeyStore.deleteDeviceKey(deviceId);
        log.info("Device unpaired: deviceId={}, userId={}", deviceId, userId);
    }

    /**
     * Revoke all devices for a user
     */
    public int revokeAllDevices(UUID userId) {
        int count = deviceKeyStore.revokeAllForUser(userId);
        log.info("All devices revoked for user: {}", userId);
        return count;
    }

    /**
     * Update device external address (for direct connection)
     */
    public void updateDeviceAddress(String deviceId, String externalAddress, boolean directConnectionEnabled) {
        deviceKeyStore.getDeviceKey(deviceId).ifPresent(key -> {
            key.setExternalAddress(externalAddress);
            key.setDirectConnectionEnabled(directConnectionEnabled);
            deviceKeyStore.storeDeviceKey(key);
            log.info("Device address updated: deviceId={}, address={}", deviceId, externalAddress);
        });
    }

    /**
     * Validate device token and return user ID.
     * Token format: base64(userId:deviceId:timestamp:hmac)
     * HMAC is computed using the device's authKey for cryptographic verification.
     */
    public Optional<UUID> validateDeviceToken(String deviceToken) {
        try {
            String decoded = new String(Base64.getDecoder().decode(deviceToken));
            String[] parts = decoded.split(":");

            if (parts.length < 4) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(parts[0]);
            String deviceId = parts[1];
            String timestamp = parts[2];
            String providedHmac = parts[3];

            Optional<DeviceKeyStore.DeviceKey> deviceKey = deviceKeyStore.getDeviceKey(deviceId);
            if (deviceKey.isEmpty() || deviceKey.get().isRevoked()) {
                return Optional.empty();
            }

            if (!deviceKey.get().getUserId().equals(userId)) {
                return Optional.empty();
            }

            // Verify HMAC signature using device authKey
            byte[] authKey = Base64.getDecoder().decode(deviceKey.get().getAuthKey());
            String expectedHmac = computeHmac(authKey, userId.toString() + ":" + deviceId + ":" + timestamp);
            if (!java.security.MessageDigest.isEqual(
                    expectedHmac.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    providedHmac.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                log.warn("Device token HMAC mismatch for deviceId={}", deviceId);
                return Optional.empty();
            }

            return Optional.of(userId);

        } catch (Exception e) {
            log.debug("Invalid device token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /**
     * Generate a secure device token signed with HMAC-SHA256 using the device's authKey.
     */
    private String generateDeviceToken(UUID userId, String deviceId) {
        long timestamp = System.currentTimeMillis();
        String data = userId.toString() + ":" + deviceId + ":" + timestamp;

        // Retrieve authKey for HMAC signing
        Optional<DeviceKeyStore.DeviceKey> deviceKey = deviceKeyStore.getDeviceKey(deviceId);
        String hmac;
        if (deviceKey.isPresent()) {
            byte[] authKey = Base64.getDecoder().decode(deviceKey.get().getAuthKey());
            hmac = computeHmac(authKey, data);
        } else {
            // Fallback: use cryptographically secure random bytes (token will fail validation
            // if device key is later deleted, which is the correct behavior)
            byte[] randomBytes = new byte[32];
            SECURE_RANDOM.nextBytes(randomBytes);
            hmac = java.util.HexFormat.of().formatHex(randomBytes);
        }

        String tokenData = data + ":" + hmac;
        return Base64.getEncoder().encodeToString(tokenData.getBytes());
    }

    /**
     * Compute HMAC-SHA256 signature for device token verification.
     */
    private String computeHmac(byte[] key, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    // Request/Response records

    public record PairingInitiation(
        String pairingCode,
        Instant expiresAt
    ) {}

    public record PairingRequest(
        String pairingCode,
        String deviceId,
        String deviceName,
        String platform,
        String devicePublicKey,
        String deviceFingerprint,
        String externalAddress,
        boolean directConnectionEnabled,
        java.util.List<String> allowedIps
    ) {}

    public record PairingResult(
        String platformPublicKey,
        String platformFingerprint,
        String deviceToken,
        UUID userId
    ) {}

    public static class PairingException extends Exception {
        public PairingException(String message) {
            super(message);
        }
    }
}
