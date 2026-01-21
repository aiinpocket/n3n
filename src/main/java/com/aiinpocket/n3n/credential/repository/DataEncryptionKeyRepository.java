package com.aiinpocket.n3n.credential.repository;

import com.aiinpocket.n3n.credential.entity.DataEncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataEncryptionKeyRepository extends JpaRepository<DataEncryptionKey, UUID> {

    /**
     * 取得目前啟用的 DEK（用於加密新資料）
     */
    @Query("SELECT d FROM DataEncryptionKey d WHERE d.purpose = :purpose AND d.status = 'ACTIVE' " +
           "AND (d.workspaceId = :workspaceId OR d.workspaceId IS NULL) " +
           "ORDER BY d.keyVersion DESC LIMIT 1")
    Optional<DataEncryptionKey> findActiveKey(String purpose, UUID workspaceId);

    /**
     * 取得全域啟用的 DEK
     */
    @Query("SELECT d FROM DataEncryptionKey d WHERE d.purpose = :purpose AND d.status = 'ACTIVE' " +
           "AND d.workspaceId IS NULL ORDER BY d.keyVersion DESC LIMIT 1")
    Optional<DataEncryptionKey> findActiveGlobalKey(String purpose);

    /**
     * 取得特定版本的 DEK（用於解密舊資料）
     */
    Optional<DataEncryptionKey> findByPurposeAndKeyVersion(String purpose, Integer keyVersion);

    /**
     * 取得所有可用於解密的 DEK
     */
    @Query("SELECT d FROM DataEncryptionKey d WHERE d.purpose = :purpose " +
           "AND d.status IN ('ACTIVE', 'DECRYPT_ONLY') ORDER BY d.keyVersion DESC")
    List<DataEncryptionKey> findDecryptableKeys(String purpose);

    /**
     * 取得最高版本號
     */
    @Query("SELECT COALESCE(MAX(d.keyVersion), 0) FROM DataEncryptionKey d WHERE d.purpose = :purpose")
    Integer findMaxVersion(String purpose);
}
