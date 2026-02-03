package com.aiinpocket.n3n.marketplace.service;

import com.aiinpocket.n3n.marketplace.entity.Plugin;
import com.aiinpocket.n3n.marketplace.entity.PluginReview;
import com.aiinpocket.n3n.marketplace.entity.PluginVersion;
import com.aiinpocket.n3n.marketplace.repository.PluginRepository;
import com.aiinpocket.n3n.marketplace.repository.PluginReviewRepository;
import com.aiinpocket.n3n.marketplace.repository.PluginVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing plugins in the marketplace.
 */
@Service("marketplacePluginService")
@Slf4j
@Transactional
public class PluginService {

    private final PluginRepository pluginRepository;
    private final PluginVersionRepository versionRepository;
    private final PluginReviewRepository reviewRepository;

    public PluginService(
            @Qualifier("marketplacePluginRepository") PluginRepository pluginRepository,
            @Qualifier("marketplacePluginVersionRepository") PluginVersionRepository versionRepository,
            @Qualifier("marketplacePluginReviewRepository") PluginReviewRepository reviewRepository) {
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.reviewRepository = reviewRepository;
    }

    // Plugin CRUD

    public Plugin createPlugin(Plugin plugin) {
        if (pluginRepository.existsByName(plugin.getName())) {
            throw new IllegalArgumentException("Plugin name already exists: " + plugin.getName());
        }

        plugin.setCreatedAt(LocalDateTime.now());
        plugin.setUpdatedAt(LocalDateTime.now());

        log.info("Creating plugin: {}", plugin.getName());
        return pluginRepository.save(plugin);
    }

    public Plugin updatePlugin(UUID id, Plugin updates) {
        Plugin plugin = pluginRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + id));

        if (updates.getDisplayName() != null) {
            plugin.setDisplayName(updates.getDisplayName());
        }
        if (updates.getDescription() != null) {
            plugin.setDescription(updates.getDescription());
        }
        if (updates.getLongDescription() != null) {
            plugin.setLongDescription(updates.getLongDescription());
        }
        if (updates.getIconUrl() != null) {
            plugin.setIconUrl(updates.getIconUrl());
        }
        if (updates.getScreenshots() != null) {
            plugin.setScreenshots(updates.getScreenshots());
        }
        if (updates.getCategory() != null) {
            plugin.setCategory(updates.getCategory());
        }
        if (updates.getTags() != null) {
            plugin.setTags(updates.getTags());
        }

        plugin.setUpdatedAt(LocalDateTime.now());

        log.info("Updating plugin: {}", plugin.getName());
        return pluginRepository.save(plugin);
    }

    public void deletePlugin(UUID id) {
        Plugin plugin = pluginRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + id));

        log.info("Deleting plugin: {}", plugin.getName());
        pluginRepository.delete(plugin);
    }

    public Optional<Plugin> getPlugin(UUID id) {
        return pluginRepository.findById(id);
    }

    public Optional<Plugin> getPluginByName(String name) {
        return pluginRepository.findByName(name);
    }

    // Publishing

    public Plugin publishPlugin(UUID id) {
        Plugin plugin = pluginRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + id));

        // Verify there's at least one version
        if (versionRepository.findFirstByPluginIdAndYankedFalseOrderByPublishedAtDesc(id).isEmpty()) {
            throw new IllegalStateException("Cannot publish plugin without a version");
        }

        plugin.setPublished(true);
        plugin.setPublishedAt(LocalDateTime.now());

        log.info("Publishing plugin: {}", plugin.getName());
        return pluginRepository.save(plugin);
    }

    public Plugin unpublishPlugin(UUID id) {
        Plugin plugin = pluginRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + id));

        plugin.setPublished(false);

        log.info("Unpublishing plugin: {}", plugin.getName());
        return pluginRepository.save(plugin);
    }

    // Versions

    public PluginVersion addVersion(UUID pluginId, PluginVersion version) {
        Plugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));

        if (versionRepository.existsByPluginIdAndVersion(pluginId, version.getVersion())) {
            throw new IllegalArgumentException("Version already exists: " + version.getVersion());
        }

        version.setPlugin(plugin);
        version.setPublishedAt(LocalDateTime.now());

        log.info("Adding version {} to plugin: {}", version.getVersion(), plugin.getName());
        return versionRepository.save(version);
    }

    public List<PluginVersion> getVersions(UUID pluginId) {
        return versionRepository.findByPluginIdOrderByPublishedAtDesc(pluginId);
    }

    public Optional<PluginVersion> getLatestVersion(UUID pluginId) {
        return versionRepository.findFirstByPluginIdAndYankedFalseOrderByPublishedAtDesc(pluginId);
    }

    public Optional<PluginVersion> getLatestStableVersion(UUID pluginId) {
        return versionRepository.findFirstByPluginIdAndPrereleaseFalseAndYankedFalseOrderByPublishedAtDesc(pluginId);
    }

    public void yankVersion(UUID pluginId, String versionStr) {
        PluginVersion version = versionRepository.findByPluginIdAndVersion(pluginId, versionStr)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionStr));

        version.setYanked(true);
        versionRepository.save(version);

        log.info("Yanked version {} of plugin {}", versionStr, pluginId);
    }

    // Search and Browse

    @Transactional(readOnly = true)
    public Page<Plugin> browsePlugins(Pageable pageable) {
        return pluginRepository.findByPublishedTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Plugin> browseByType(Plugin.PluginType type, Pageable pageable) {
        return pluginRepository.findByPublishedTrueAndType(type, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Plugin> browseByCategory(String category, Pageable pageable) {
        return pluginRepository.findByPublishedTrueAndCategory(category, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Plugin> search(String query, Pageable pageable) {
        return pluginRepository.search(query, pageable);
    }

    @Transactional(readOnly = true)
    public List<Plugin> getFeatured(int limit) {
        return pluginRepository.findByPublishedTrueAndFeaturedTrueOrderByDownloadCountDesc(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Plugin> getPopular(int limit) {
        return pluginRepository.findPopular(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Plugin> getTrending(int limit) {
        return pluginRepository.findTrending(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Plugin> getRecentlyUpdated(int limit) {
        return pluginRepository.findRecentlyUpdated(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Plugin> getTopRated(int limit) {
        return pluginRepository.findTopRated(5, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return pluginRepository.findAllCategories();
    }

    // Reviews

    public PluginReview addReview(UUID pluginId, PluginReview review) {
        Plugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));

        if (reviewRepository.existsByPluginIdAndUserId(pluginId, review.getUserId())) {
            throw new IllegalArgumentException("User has already reviewed this plugin");
        }

        review.setPlugin(plugin);
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());

        PluginReview saved = reviewRepository.save(review);

        // Update plugin rating
        updatePluginRating(pluginId);

        log.info("Review added for plugin: {} by user: {}", plugin.getName(), review.getUserId());
        return saved;
    }

    public PluginReview updateReview(UUID reviewId, PluginReview updates) {
        PluginReview review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (updates.getRating() > 0) {
            review.setRating(updates.getRating());
        }
        if (updates.getTitle() != null) {
            review.setTitle(updates.getTitle());
        }
        if (updates.getContent() != null) {
            review.setContent(updates.getContent());
        }

        review.setUpdatedAt(LocalDateTime.now());

        PluginReview saved = reviewRepository.save(review);

        // Update plugin rating
        updatePluginRating(review.getPlugin().getId());

        return saved;
    }

    public void deleteReview(UUID reviewId) {
        PluginReview review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        UUID pluginId = review.getPlugin().getId();
        reviewRepository.delete(review);

        // Update plugin rating
        updatePluginRating(pluginId);
    }

    @Transactional(readOnly = true)
    public Page<PluginReview> getReviews(UUID pluginId, Pageable pageable) {
        return reviewRepository.findByPluginIdOrderByCreatedAtDesc(pluginId, pageable);
    }

    private void updatePluginRating(UUID pluginId) {
        Double avgRating = reviewRepository.getAverageRating(pluginId);
        long count = reviewRepository.countByPluginId(pluginId);

        Plugin plugin = pluginRepository.findById(pluginId).orElse(null);
        if (plugin != null) {
            plugin.setRatingAvg(avgRating != null ?
                BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO);
            plugin.setRatingCount((int) count);
            pluginRepository.save(plugin);
        }
    }

    // Downloads

    public void recordDownload(UUID pluginId, String version) {
        Plugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));

        plugin.setDownloadCount(plugin.getDownloadCount() + 1);
        plugin.setWeeklyDownloads(plugin.getWeeklyDownloads() + 1);
        pluginRepository.save(plugin);

        if (version != null) {
            versionRepository.findByPluginIdAndVersion(pluginId, version)
                .ifPresent(v -> {
                    v.setDownloadCount(v.getDownloadCount() + 1);
                    versionRepository.save(v);
                });
        }

        log.debug("Download recorded for plugin: {} version: {}", plugin.getName(), version);
    }

    // Statistics

    @Transactional(readOnly = true)
    public MarketplaceStats getStats() {
        return new MarketplaceStats(
            pluginRepository.count(),
            pluginRepository.countByPublishedTrueAndType(Plugin.PluginType.LOCAL_AGENT),
            pluginRepository.countByPublishedTrueAndType(Plugin.PluginType.SKILL),
            pluginRepository.countByPublishedTrueAndType(Plugin.PluginType.NODE),
            pluginRepository.countByPublishedTrueAndType(Plugin.PluginType.THEME),
            pluginRepository.countByPublishedTrueAndType(Plugin.PluginType.INTEGRATION)
        );
    }

    public record MarketplaceStats(
        long totalPlugins,
        long localAgents,
        long skills,
        long nodes,
        long themes,
        long integrations
    ) {}
}
