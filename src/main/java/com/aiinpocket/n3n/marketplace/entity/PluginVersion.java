package com.aiinpocket.n3n.marketplace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plugin version entity for tracking releases.
 */
@Entity(name = "MarketplacePluginVersion")
@Table(name = "marketplace_plugin_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"plugin_id", "version"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plugin_id", nullable = false)
    private Plugin plugin;

    /**
     * Version string (semver)
     */
    @Column(nullable = false, length = 50)
    private String version;

    /**
     * Changelog for this version
     */
    @Column(columnDefinition = "TEXT")
    private String changelog;

    /**
     * macOS download URL (for local-agent)
     */
    @Column(name = "macos_url", length = 500)
    private String macosUrl;

    /**
     * Windows download URL (for local-agent)
     */
    @Column(name = "windows_url", length = 500)
    private String windowsUrl;

    /**
     * Linux download URL (for local-agent)
     */
    @Column(name = "linux_url", length = 500)
    private String linuxUrl;

    /**
     * Package URL (for skill/node/theme)
     */
    @Column(name = "package_url", length = 500)
    private String packageUrl;

    /**
     * Package checksum (SHA256)
     */
    @Column(name = "package_checksum", length = 64)
    private String packageChecksum;

    /**
     * Package size in bytes
     */
    @Column(name = "package_size")
    private Long packageSize;

    /**
     * Minimum n3n version required
     */
    @Column(name = "min_n3n_version", length = 50)
    private String minN3nVersion;

    /**
     * Download count for this version
     */
    @Column(name = "download_count")
    @Builder.Default
    private int downloadCount = 0;

    /**
     * Whether this is a pre-release
     */
    @Builder.Default
    private boolean prerelease = false;

    /**
     * Whether this version is yanked (hidden)
     */
    @Builder.Default
    private boolean yanked = false;

    @CreationTimestamp
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
