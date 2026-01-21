package com.aiinpocket.n3n.credential.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "credential_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    private String icon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> fieldsSchema;

    @Column(name = "test_endpoint")
    private String testEndpoint;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
