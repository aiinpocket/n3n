package com.aiinpocket.n3n.service.entity;

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
@Table(name = "external_services")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    @Builder.Default
    private String protocol = "REST";

    @Column(name = "schema_url")
    private String schemaUrl;

    @Column(name = "auth_type")
    private String authType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", columnDefinition = "jsonb")
    private Map<String, Object> authConfig;

    /**
     * 關聯的認證資訊 ID
     * 使用此認證呼叫外部服務 API
     */
    @Column(name = "credential_id")
    private UUID credentialId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health_check", columnDefinition = "jsonb")
    private Map<String, Object> healthCheck;

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;
}
