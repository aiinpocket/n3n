package com.aiinpocket.n3n.gateway.security;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store for device encryption keys.
 * Uses Redis for persistence with in-memory cache.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceKeyStore {

    private static final String KEY_PREFIX = "agent:device:";
    private static final String PAIRING_PREFIX = "agent:pairing:";
    private static final Duration PAIRING_TTL = Duration.ofMinutes(5);
    private static final Duration KEY_TTL = Duration.ofDays(365);

    private final RedisTemplate<String, Object> redisTemplate;

    // In-memory cache for hot keys
    private final Map<String, DeviceKey> keyCache = new ConcurrentHashMap<>();

    /**
     * Store a pairing request
     */
    public void storePairing(String pairingCode, PairingRequest request) {
        String key = PAIRING_PREFIX + pairingCode;
        redisTemplate.opsForValue().set(key, request, PAIRING_TTL);
        log.debug("Stored pairing request: {}", pairingCode);
    }

    /**
     * Get and consume a pairing request
     */
    public Optional<PairingRequest> consumePairing(String pairingCode) {
        String key = PAIRING_PREFIX + pairingCode;
        @SuppressWarnings("unchecked")
        PairingRequest request = (PairingRequest) redisTemplate.opsForValue().get(key);
        if (request != null) {
            redisTemplate.delete(key);
            log.debug("Consumed pairing request: {}", pairingCode);
        }
        return Optional.ofNullable(request);
    }

    /**
     * Store device keys after successful pairing
     */
    public void storeDeviceKey(DeviceKey deviceKey) {
        String key = KEY_PREFIX + deviceKey.getDeviceId();
        redisTemplate.opsForValue().set(key, deviceKey, KEY_TTL);
        keyCache.put(deviceKey.getDeviceId(), deviceKey);
        log.info("Stored device key: {}", deviceKey.getDeviceId());
    }

    /**
     * Get device key
     */
    public Optional<DeviceKey> getDeviceKey(String deviceId) {
        // Check cache first
        DeviceKey cached = keyCache.get(deviceId);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Load from Redis
        String key = KEY_PREFIX + deviceId;
        @SuppressWarnings("unchecked")
        DeviceKey deviceKey = (DeviceKey) redisTemplate.opsForValue().get(key);

        if (deviceKey != null) {
            keyCache.put(deviceId, deviceKey);
        }

        return Optional.ofNullable(deviceKey);
    }

    /**
     * Update sequence number (for replay protection)
     */
    public void updateSequence(String deviceId, long sequence) {
        getDeviceKey(deviceId).ifPresent(key -> {
            key.setLastSequence(sequence);
            key.setLastActiveAt(Instant.now());
            storeDeviceKey(key);
        });
    }

    /**
     * Delete device key (unpair)
     */
    public void deleteDeviceKey(String deviceId) {
        String key = KEY_PREFIX + deviceId;
        redisTemplate.delete(key);
        keyCache.remove(deviceId);
        log.info("Deleted device key: {}", deviceId);
    }

    /**
     * Get all device keys for a user
     */
    public List<DeviceKey> getDeviceKeysForUser(UUID userId) {
        // In production, maintain a secondary index userId -> deviceIds
        // For now, scan (not efficient for large datasets)
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        List<DeviceKey> result = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                @SuppressWarnings("unchecked")
                DeviceKey deviceKey = (DeviceKey) redisTemplate.opsForValue().get(key);
                if (deviceKey != null && userId.equals(deviceKey.getUserId())) {
                    result.add(deviceKey);
                }
            }
        }

        return result;
    }

    /**
     * Revoke all device keys for a user
     */
    public void revokeAllForUser(UUID userId) {
        List<DeviceKey> keys = getDeviceKeysForUser(userId);
        for (DeviceKey key : keys) {
            deleteDeviceKey(key.getDeviceId());
        }
        log.info("Revoked {} device keys for user: {}", keys.size(), userId);
    }

    // Data classes

    @Data
    @Builder
    public static class PairingRequest {
        private UUID userId;
        private String pairingSecret;  // Base64 encoded
        private Instant createdAt;
        private Instant expiresAt;
    }

    @Data
    @Builder
    public static class DeviceKey {
        /**
         * Unique device ID
         */
        private String deviceId;

        /**
         * User who owns this device
         */
        private UUID userId;

        /**
         * Device display name
         */
        private String deviceName;

        /**
         * Platform: macos, windows, linux
         */
        private String platform;

        /**
         * Device fingerprint for binding
         */
        private String fingerprint;

        /**
         * Encryption key for client-to-server messages (Base64)
         */
        private String encryptKeyC2S;

        /**
         * Encryption key for server-to-client messages (Base64)
         */
        private String encryptKeyS2C;

        /**
         * Authentication key (Base64)
         */
        private String authKey;

        /**
         * Last used sequence number (for replay protection)
         */
        private long lastSequence;

        /**
         * When the device was paired
         */
        private Instant pairedAt;

        /**
         * Last activity
         */
        private Instant lastActiveAt;

        /**
         * External address for direct connection (optional)
         */
        private String externalAddress;

        /**
         * Whether direct connection is enabled
         */
        private boolean directConnectionEnabled;

        /**
         * Allowed IP addresses for connections (optional)
         */
        private List<String> allowedIps;

        /**
         * Revoked status
         */
        @Builder.Default
        private boolean revoked = false;
    }
}
