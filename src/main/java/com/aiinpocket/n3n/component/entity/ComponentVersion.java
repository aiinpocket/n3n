package com.aiinpocket.n3n.component.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "component_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String image;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interface_def", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> interfaceDef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema", columnDefinition = "jsonb")
    private Map<String, Object> configSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> resources = Map.of("memory", "256Mi", "cpu", "200m");

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health_check", columnDefinition = "jsonb")
    private Map<String, Object> healthCheck;

    @Column(nullable = false)
    @Builder.Default
    private String status = "disabled";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
