package com.aiinpocket.n3n.plugin.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "plugin_installations")
public class PluginInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plugin_id", nullable = false)
    private UUID pluginId;

    @Column(name = "plugin_version_id", nullable = false)
    private UUID pluginVersionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "installed_at", nullable = false)
    private LocalDateTime installedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plugin_id", insertable = false, updatable = false)
    private Plugin plugin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plugin_version_id", insertable = false, updatable = false)
    private PluginVersion pluginVersion;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
