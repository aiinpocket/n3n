package com.aiinpocket.n3n.credential.entity;

import com.aiinpocket.n3n.common.constant.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false)
    @Builder.Default
    private String visibility = Status.Visibility.PRIVATE;

    @Column(name = "encrypted_data", nullable = false)
    private byte[] encryptedData;

    @Column(name = "encryption_iv", nullable = false)
    private byte[] encryptionIv;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    /**
     * 加密使用的 key 版本
     */
    @Column(name = "key_version")
    @Builder.Default
    private Integer keyVersion = 1;

    /**
     * 金鑰狀態：active=正常, mismatched=key不匹配, migrating=遷移中
     */
    @Column(name = "key_status")
    @Builder.Default
    private String keyStatus = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 檢查金鑰是否匹配
     */
    public boolean isKeyMatched() {
        return "active".equals(keyStatus);
    }

    /**
     * 檢查是否正在遷移
     */
    public boolean isMigrating() {
        return "migrating".equals(keyStatus);
    }
}
