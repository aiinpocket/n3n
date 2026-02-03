package com.aiinpocket.n3n.marketplace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Plugin entity for the marketplace.
 */
@Entity(name = "MarketplacePlugin")
@Table(name = "marketplace_plugins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plugin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Plugin type: local-agent, skill, node, theme, integration
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PluginType type;

    /**
     * Unique plugin name (slug)
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Display name
     */
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    /**
     * Short description
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Long description (Markdown)
     */
    @Column(name = "long_description", columnDefinition = "TEXT")
    private String longDescription;

    /**
     * Icon URL
     */
    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    /**
     * Screenshots (JSON array of URLs)
     */
    @Column(columnDefinition = "TEXT")
    private String screenshots;

    /**
     * Author user ID
     */
    @Column(name = "author_id")
    private UUID authorId;

    /**
     * Author display name
     */
    @Column(name = "author_name", length = 200)
    private String authorName;

    /**
     * Whether author is verified
     */
    @Column(name = "author_verified")
    @Builder.Default
    private boolean authorVerified = false;

    /**
     * Category
     */
    @Column(length = 50)
    private String category;

    /**
     * Tags (comma-separated or JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String tags;

    /**
     * Average rating (0-5)
     */
    @Column(name = "rating_avg", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    /**
     * Number of ratings
     */
    @Column(name = "rating_count")
    @Builder.Default
    private int ratingCount = 0;

    /**
     * Total download count
     */
    @Column(name = "download_count")
    @Builder.Default
    private int downloadCount = 0;

    /**
     * Weekly download count
     */
    @Column(name = "weekly_downloads")
    @Builder.Default
    private int weeklyDownloads = 0;

    /**
     * Whether plugin is free
     */
    @Column(name = "pricing_free")
    @Builder.Default
    private boolean pricingFree = true;

    /**
     * Price amount (if not free)
     */
    @Column(name = "pricing_amount", precision = 10, scale = 2)
    private BigDecimal pricingAmount;

    /**
     * Currency code
     */
    @Column(name = "pricing_currency", length = 3)
    private String pricingCurrency;

    /**
     * Minimum n3n version required
     */
    @Column(name = "min_n3n_version", length = 50)
    private String minN3nVersion;

    /**
     * Supported platforms (for local-agent type, comma-separated)
     */
    @Column(columnDefinition = "TEXT")
    private String platforms;

    /**
     * Whether plugin is published
     */
    @Builder.Default
    private boolean published = false;

    /**
     * Whether plugin is featured
     */
    @Builder.Default
    private boolean featured = false;

    /**
     * Whether plugin is deprecated
     */
    @Builder.Default
    private boolean deprecated = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Published timestamp
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "plugin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PluginVersion> versions;

    public enum PluginType {
        LOCAL_AGENT,
        SKILL,
        NODE,
        THEME,
        INTEGRATION
    }
}
