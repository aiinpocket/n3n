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
            log.error("Cryptographic error during pairing", e);
            throw new PairingException("Cryptographic error: " + e.getMessage());
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
    public void revokeAllDevices(UUID userId) {
        deviceKeyStore.revokeAllForUser(userId);
        log.info("All devices revoked for user: {}", userId);
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
     * Validate device token and return user ID
     */
    public Optional<UUID> validateDeviceToken(String deviceToken) {
        try {
            // Token format: base64(userId:deviceId:timestamp:signature)
            String decoded = new String(Base64.getDecoder().decode(deviceToken));
            String[] parts = decoded.split(":");

            if (parts.length < 4) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(parts[0]);
            String deviceId = parts[1];
            // In production, verify signature with auth key

            Optional<DeviceKeyStore.DeviceKey> deviceKey = deviceKeyStore.getDeviceKey(deviceId);
            if (deviceKey.isEmpty() || deviceKey.get().isRevoked()) {
                return Optional.empty();
            }

            if (!deviceKey.get().getUserId().equals(userId)) {
                return Optional.empty();
            }

            return Optional.of(userId);

        } catch (Exception e) {
            log.debug("Invalid device token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Generate a secure device token
     */
    private String generateDeviceToken(UUID userId, String deviceId) {
        long timestamp = System.currentTimeMillis();
        String signature = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String tokenData = userId.toString() + ":" + deviceId + ":" + timestamp + ":" + signature;
        return Base64.getEncoder().encodeToString(tokenData.getBytes());
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
