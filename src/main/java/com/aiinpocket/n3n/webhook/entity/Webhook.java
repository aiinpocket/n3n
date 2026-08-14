package com.aiinpocket.n3n.webhook.entity;

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
@Table(name = "webhooks", indexes = {
        @Index(name = "idx_webhook_flow", columnList = "flow_id"),
        @Index(name = "idx_webhook_created_by", columnList = "created_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    @Column(nullable = false, length = 255)
    private String name;

    /**
     * 使用者命名空間（隨機短碼，非帳號衍生）。觸發網址為 /webhook/{ns}/{path}，
     * 唯一性為 (ns, path, method)——不同使用者可各自使用相同的 path。
     * 舊資料 ns 為 null，沿用全域唯一的 /webhook/{path} 路徑。
     */
    @Column(length = 16)
    private String ns;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String method = "POST";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "auth_type", length = 50)
    private String authType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", columnDefinition = "jsonb")
    private Map<String, Object> authConfig;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
