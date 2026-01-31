package com.aiinpocket.n3n.skill.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Skill entity representing a reusable automation capability.
 * Skills are essentially pre-built APIs that execute without AI at runtime.
 */
@Entity
@Table(name = "skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    @Column(nullable = false)
    private String category;

    private String icon;

    @Column(name = "is_builtin")
    @Builder.Default
    private Boolean isBuiltin = false;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    /**
     * Implementation type: 'java', 'http', 'script'
     */
    @Column(name = "implementation_type", nullable = false)
    private String implementationType;

    /**
     * Implementation configuration (depends on type)
     */
    @Column(name = "implementation_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> implementationConfig = Map.of();

    /**
     * JSON Schema for skill input
     */
    @Column(name = "input_schema", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> inputSchema;

    /**
     * JSON Schema for skill output
     */
    @Column(name = "output_schema", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> outputSchema;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false)
    @Builder.Default
    private String visibility = "private";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
