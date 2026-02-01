package com.aiinpocket.n3n.plugin.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "plugin_versions")
public class PluginVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plugin_id", nullable = false)
    private UUID pluginId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "min_platform_version", length = 50)
    private String minPlatformVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema", columnDefinition = "jsonb")
    private Map<String, Object> configSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "node_definitions", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> nodeDefinitions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
    private List<String> capabilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> dependencies;

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(length = 128)
    private String checksum;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plugin_id", insertable = false, updatable = false)
    private Plugin plugin;
}
