package com.aiinpocket.n3n.flow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流程匯入記錄
 */
@Entity
@Table(name = "flow_imports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 匯入產生的 Flow ID
     */
    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    /**
     * 匯出包版本
     */
    @Column(name = "package_version", nullable = false)
    private String packageVersion;

    /**
     * 匯出包 SHA-256 checksum
     */
    @Column(name = "package_checksum", nullable = false)
    private String packageChecksum;

    /**
     * 原始流程名稱
     */
    @Column(name = "original_flow_name")
    private String originalFlowName;

    /**
     * 來源系統識別
     */
    @Column(name = "source_system")
    private String sourceSystem;

    @CreationTimestamp
    @Column(name = "imported_at", updatable = false)
    private Instant importedAt;

    /**
     * 匯入者 ID
     */
    @Column(name = "imported_by", nullable = false)
    private UUID importedBy;

    /**
     * 匯入時缺少的元件清單
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_components", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> missingComponents = List.of();

    /**
     * 憑證 ID 映射（原始 ID -> 新 ID）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credential_mappings", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> credentialMappings = Map.of();

    /**
     * 匯入狀態：pending, resolved, partial, failed
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "resolved";

    // 常量定義
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_FAILED = "failed";
}
