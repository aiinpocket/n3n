package com.aiinpocket.n3n.credential.repository;

import com.aiinpocket.n3n.credential.entity.EncryptionKeyMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncryptionKeyMetadataRepository extends JpaRepository<EncryptionKeyMetadata, UUID> {

    /**
     * 取得指定類型的最新版本
     */
    Optional<EncryptionKeyMetadata> findFirstByKeyTypeAndStatusOrderByKeyVersionDesc(String keyType, String status);

    /**
     * 取得指定類型和版本的元資料
     */
    Optional<EncryptionKeyMetadata> findByKeyTypeAndKeyVersion(String keyType, Integer keyVersion);

    /**
     * 取得指定類型的 active 金鑰
     */
    default Optional<EncryptionKeyMetadata> findActiveByKeyType(String keyType) {
        return findFirstByKeyTypeAndStatusOrderByKeyVersionDesc(keyType, EncryptionKeyMetadata.STATUS_ACTIVE);
    }

    /**
     * 檢查是否存在指定類型的 active 金鑰
     */
    boolean existsByKeyTypeAndStatus(String keyType, String status);
}
