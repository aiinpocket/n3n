package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.credential.entity.EncryptionKeyMetadata;
import com.aiinpocket.n3n.credential.repository.EncryptionKeyMetadataRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * JWT Secret Provider - 自動產生和管理 JWT 簽名密鑰
 *
 * 優先順序：
 * 1. 環境變數 JWT_SECRET（如果有設定）
 * 2. 資料庫中已儲存的密鑰（首次產生後持久化）
 * 3. 首次啟動時自動產生 256-bit 隨機密鑰
 *
 * 這樣每個系統都有獨立的 JWT 密鑰，不需要手動設定。
 */
@Component
@Slf4j
public class JwtSecretProvider {

    public static final String KEY_TYPE_JWT_SECRET = "JWT_SECRET";
    private static final int SECRET_LENGTH = 32; // 256 bits

    private final EncryptionKeyMetadataRepository metadataRepository;

    @Value("${JWT_SECRET:#{null}}")
    private String envJwtSecret;

    @Getter
    private String jwtSecret;

    @Getter
    private String secretSource;

    public JwtSecretProvider(EncryptionKeyMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    @PostConstruct
    public void init() {
        this.jwtSecret = loadOrGenerateSecret();
        log.info("JWT secret initialized from: {}", secretSource);
    }

    private String loadOrGenerateSecret() {
        // 1. 環境變數優先（用於手動覆蓋或叢集部署）
        if (envJwtSecret != null && !envJwtSecret.isBlank()) {
            secretSource = "environment variable (JWT_SECRET)";
            return envJwtSecret;
        }

        // 2. 從資料庫載入已存在的密鑰
        Optional<EncryptionKeyMetadata> existingKey = metadataRepository
                .findActiveByKeyType(KEY_TYPE_JWT_SECRET);

        if (existingKey.isPresent()) {
            secretSource = "database (persisted)";
            return existingKey.get().getKeyHash(); // 這裡 keyHash 存的是實際的 secret
        }

        // 3. 首次啟動：產生新的隨機密鑰
        String newSecret = generateSecureSecret();

        // 持久化到資料庫
        EncryptionKeyMetadata metadata = EncryptionKeyMetadata.builder()
                .keyType(KEY_TYPE_JWT_SECRET)
                .keyVersion(1)
                .keyHash(newSecret) // 存儲實際的 secret（因為需要可逆讀取）
                .source(EncryptionKeyMetadata.SOURCE_AUTO_GENERATED)
                .status(EncryptionKeyMetadata.STATUS_ACTIVE)
                .build();
        metadataRepository.save(metadata);

        secretSource = "auto-generated (first startup)";
        log.info("Generated new JWT secret for this instance");

        return newSecret;
    }

    /**
     * 產生安全的隨機 Base64 編碼密鑰
     */
    private String generateSecureSecret() {
        byte[] keyBytes = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
