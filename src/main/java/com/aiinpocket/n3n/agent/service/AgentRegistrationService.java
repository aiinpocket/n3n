package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.entity.AgentRegistration;
import com.aiinpocket.n3n.agent.entity.AgentRegistration.AgentStatus;
import com.aiinpocket.n3n.agent.repository.AgentRegistrationRepository;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.gateway.security.AgentCrypto;
import com.aiinpocket.n3n.gateway.security.DeviceKeyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 註冊管理服務
 * 處理一次性 Token 產生、Agent 註冊、封鎖等功能
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentRegistrationService {

    private final AgentRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final GatewaySettingsService gatewaySettingsService;
    private final AgentCrypto agentCrypto;
    private final DeviceKeyStore deviceKeyStore;

    /**
     * 產生新的註冊 Token
     * 回傳 Token 明文（只顯示一次）和 Agent Config
     */
    @Transactional
    public TokenGenerationResult generateToken(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Generate UUID for agent and random token
        String agentId = UUID.randomUUID().toString();
        String token = generateSecureToken();
        String tokenHash = hashToken(token);

        // Create registration record
        AgentRegistration registration = AgentRegistration.builder()
            .user(user)
            .registrationTokenHash(tokenHash)
            .status(AgentStatus.PENDING)
            .build();

        registrationRepository.save(registration);

        log.info("Generated registration token for user {}: agentId={}", userId, agentId);

        // Generate agent config
        GatewaySettingsService.AgentConfig config =
            gatewaySettingsService.generateAgentConfig(token, agentId);

        return new TokenGenerationResult(
            registration.getId(),
            agentId,
            token,  // Only returned once
            config
        );
    }

    /**
     * 使用 Token 註冊 Agent
     * 這是公開的端點，由 Agent 呼叫
     */
    @Transactional
    public RegistrationResult registerAgent(RegistrationRequest request) throws RegistrationException {
        // Hash the provided token
        String tokenHash = hashToken(request.token());

        // Find registration by token hash
        AgentRegistration registration = registrationRepository
            .findByRegistrationTokenHash(tokenHash)
            .orElseThrow(() -> new RegistrationException("Invalid or expired registration token"));

        // Check if already registered
        if (registration.getStatus() == AgentStatus.REGISTERED) {
            throw new RegistrationException("Token has already been used");
        }

        // Check if blocked
        if (registration.getStatus() == AgentStatus.BLOCKED) {
            throw new RegistrationException("This registration has been blocked");
        }

        try {
            // Generate platform key pair
            KeyPair platformKeyPair = agentCrypto.generateKeyPair();

            // Parse device public key
            var devicePublicKey = agentCrypto.parsePublicKey(request.devicePublicKey());

            // Derive shared secret
            byte[] sharedSecret = agentCrypto.deriveSharedSecret(
                platformKeyPair.getPrivate(),
                devicePublicKey
            );

            // Derive encryption keys
            byte[] salt = (request.deviceId() + registration.getUser().getId().toString())
                .getBytes(StandardCharsets.UTF_8);
            byte[] info = "n3n-agent-v1".getBytes(StandardCharsets.UTF_8);
            AgentCrypto.DerivedKeys derivedKeys = agentCrypto.deriveKeys(sharedSecret, salt, info);

            // Compute fingerprints
            String platformFingerprint = agentCrypto.computeFingerprint(
                platformKeyPair.getPublic().getEncoded()
            );

            // Generate device token
            String deviceToken = generateDeviceToken(registration.getUser().getId(), request.deviceId());

            // Store device keys in Redis
            Instant now = Instant.now();
            DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(request.deviceId())
                .userId(registration.getUser().getId())
                .deviceName(request.deviceName())
                .platform(request.platform())
                .fingerprint(request.deviceFingerprint())
                .encryptKeyC2S(Base64.getEncoder().encodeToString(derivedKeys.encryptKeyClientToServer()))
                .encryptKeyS2C(Base64.getEncoder().encodeToString(derivedKeys.encryptKeyServerToClient()))
                .authKey(Base64.getEncoder().encodeToString(derivedKeys.authKey()))
                .lastSequence(agentCrypto.generateInitialSequence())
                .pairedAt(now)
                .lastActiveAt(now)
                .revoked(false)
                .build();

            deviceKeyStore.storeDeviceKey(deviceKey);

            // Update registration
            registration.markRegistered(
                request.deviceId(),
                request.deviceName(),
                request.platform(),
                request.deviceFingerprint()
            );
            registrationRepository.save(registration);

            log.info("Agent registered: deviceId={}, userId={}",
                request.deviceId(), registration.getUser().getId());

            return new RegistrationResult(
                true,
                agentCrypto.encodePublicKey(platformKeyPair.getPublic()),
                platformFingerprint,
                deviceToken
            );

        } catch (GeneralSecurityException e) {
            log.error("Cryptographic error during registration", e);
            throw new RegistrationException("Cryptographic error: " + e.getMessage());
        }
    }

    /**
     * 取得使用者的所有 Agent 註冊
     */
    @Transactional(readOnly = true)
    public List<AgentRegistration> getRegistrations(UUID userId) {
        return registrationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 封鎖 Agent
     */
    @Transactional
    public void blockAgent(UUID userId, UUID registrationId, String reason) {
        AgentRegistration registration = registrationRepository
            .findByIdAndUserId(registrationId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        registration.block(reason);
        registrationRepository.save(registration);

        // Also revoke in Redis if already registered
        if (registration.getDeviceId() != null) {
            deviceKeyStore.getDeviceKey(registration.getDeviceId())
                .ifPresent(key -> {
                    key.setRevoked(true);
                    deviceKeyStore.storeDeviceKey(key);
                });
        }

        log.info("Agent blocked: id={}, reason={}", registrationId, reason);
    }

    /**
     * 解除封鎖 Agent
     */
    @Transactional
    public void unblockAgent(UUID userId, UUID registrationId) {
        AgentRegistration registration = registrationRepository
            .findByIdAndUserId(registrationId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        registration.unblock();
        registrationRepository.save(registration);

        // Also unrevoke in Redis if registered
        if (registration.getDeviceId() != null) {
            deviceKeyStore.getDeviceKey(registration.getDeviceId())
                .ifPresent(key -> {
                    key.setRevoked(false);
                    deviceKeyStore.storeDeviceKey(key);
                });
        }

        log.info("Agent unblocked: id={}", registrationId);
    }

    /**
     * 刪除 Agent 註冊
     */
    @Transactional
    public void deleteRegistration(UUID userId, UUID registrationId) {
        AgentRegistration registration = registrationRepository
            .findByIdAndUserId(registrationId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        // Delete from Redis if registered
        if (registration.getDeviceId() != null) {
            deviceKeyStore.deleteDeviceKey(registration.getDeviceId());
        }

        registrationRepository.delete(registration);
        log.info("Agent registration deleted: id={}", registrationId);
    }

    /**
     * 檢查 Device ID 是否被封鎖
     */
    @Transactional(readOnly = true)
    public boolean isDeviceBlocked(String deviceId) {
        return registrationRepository.findByDeviceId(deviceId)
            .map(r -> r.getStatus() == AgentStatus.BLOCKED)
            .orElse(false);
    }

    /**
     * 更新 Agent 最後活動時間
     */
    @Transactional
    public void updateLastSeen(String deviceId) {
        registrationRepository.updateLastSeenByDeviceId(deviceId);
    }

    // Helper methods

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String generateDeviceToken(UUID userId, String deviceId) {
        long timestamp = System.currentTimeMillis();
        String signature = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String tokenData = userId.toString() + ":" + deviceId + ":" + timestamp + ":" + signature;
        return Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
    }

    // DTOs

    public record TokenGenerationResult(
        UUID registrationId,
        String agentId,
        String token,
        GatewaySettingsService.AgentConfig config
    ) {}

    public record RegistrationRequest(
        String token,
        String deviceId,
        String deviceName,
        String platform,
        String devicePublicKey,
        String deviceFingerprint
    ) {}

    public record RegistrationResult(
        boolean success,
        String platformPublicKey,
        String platformFingerprint,
        String deviceToken
    ) {}

    public static class RegistrationException extends Exception {
        public RegistrationException(String message) {
            super(message);
        }
    }
}
