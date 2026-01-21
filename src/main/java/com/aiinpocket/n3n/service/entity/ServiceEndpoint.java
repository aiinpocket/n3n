package com.aiinpocket.n3n.service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "service_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String path;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_params", columnDefinition = "jsonb")
    private Map<String, Object> pathParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_params", columnDefinition = "jsonb")
    private Map<String, Object> queryParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", columnDefinition = "jsonb")
    private Map<String, Object> requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_schema", columnDefinition = "jsonb")
    private Map<String, Object> responseSchema;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
