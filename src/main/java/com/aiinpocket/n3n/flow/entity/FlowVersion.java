package com.aiinpocket.n3n.flow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "flow_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    @Column(nullable = false)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> settings = Map.of();

    /**
     * Pinned data for nodes - allows users to pin test data to nodes
     * so it can be reused across executions without re-running upstream nodes.
     * Key: nodeId, Value: pinned output data
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pinned_data", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> pinnedData = Map.of();

    @Column(nullable = false)
    @Builder.Default
    private String status = "draft";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Version
    @Column(name = "opt_lock_version")
    @Builder.Default
    private Long optLockVersion = 0L;
}
