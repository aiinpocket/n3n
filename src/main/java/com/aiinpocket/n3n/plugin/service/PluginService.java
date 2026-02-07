package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.plugin.dto.*;
import com.aiinpocket.n3n.plugin.entity.*;
import com.aiinpocket.n3n.plugin.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service("pluginPluginService")
public class PluginService {

    private final PluginRepository pluginRepository;
    private final PluginVersionRepository pluginVersionRepository;
    private final PluginInstallationRepository pluginInstallationRepository;
    private final PluginRatingRepository pluginRatingRepository;
    private final PluginNodeRegistrar pluginNodeRegistrar;

    public PluginService(
            @Qualifier("pluginPluginRepository") PluginRepository pluginRepository,
            @Qualifier("pluginPluginVersionRepository") PluginVersionRepository pluginVersionRepository,
            PluginInstallationRepository pluginInstallationRepository,
            PluginRatingRepository pluginRatingRepository,
            PluginNodeRegistrar pluginNodeRegistrar) {
        this.pluginRepository = pluginRepository;
        this.pluginVersionRepository = pluginVersionRepository;
        this.pluginInstallationRepository = pluginInstallationRepository;
        this.pluginRatingRepository = pluginRatingRepository;
        this.pluginNodeRegistrar = pluginNodeRegistrar;
    }

    /**
     * Get all categories with plugin counts.
     */
    public List<PluginCategoryDto> getCategories() {
        List<String> categories = pluginRepository.findAllCategories();
        return categories.stream()
                .map(cat -> {
                    long count = pluginRepository.findByCategory(cat).size();
                    return PluginCategoryDto.fromName(cat, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * Search plugins with filters.
     */
    public PluginSearchResult searchPlugins(
            String category,
            String pricing,
            String query,
            String sortBy,
            int page,
            int pageSize,
            UUID userId) {

        Sort sort = switch (sortBy != null ? sortBy : "popular") {
            case "recent" -> Sort.by(Sort.Direction.DESC, "updated_at");
            case "rating" -> Sort.by(Sort.Direction.DESC, "display_name");
            case "name" -> Sort.by(Sort.Direction.ASC, "display_name");
            default -> Sort.by(Sort.Direction.DESC, "updated_at");
        };

        Pageable pageable = PageRequest.of(page, pageSize, sort);

        Page<Plugin> pluginPage = pluginRepository.searchPlugins(
                category != null && !category.equals("all") ? category : null,
                pricing != null && !pricing.equals("all") ? pricing : null,
                query != null && !query.isBlank() ? query : null,
                pageable);

        // Get installed plugin IDs for this user
        Set<UUID> installedPluginIds = getInstalledPluginIds(userId);

        List<PluginDto> plugins = pluginPage.getContent().stream()
                .map(p -> toPluginDto(p, installedPluginIds, userId))
                .collect(Collectors.toList());

        return PluginSearchResult.builder()
                .plugins(plugins)
                .total(pluginPage.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .totalPages(pluginPage.getTotalPages())
                .build();
    }

    /**
     * Get featured plugins.
     */
    public List<PluginDto> getFeaturedPlugins(UUID userId) {
        // For now, return top 6 by download count
        List<Plugin> plugins = pluginRepository.findAll(
                PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "updatedAt"))
        ).getContent();

        Set<UUID> installedPluginIds = getInstalledPluginIds(userId);

        return plugins.stream()
                .map(p -> toPluginDto(p, installedPluginIds, userId))
                .collect(Collectors.toList());
    }

    /**
     * Get plugin details.
     */
    public PluginDetailDto getPluginDetail(UUID pluginId, UUID userId) {
        Plugin plugin = pluginRepository.findById(pluginId)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin not found: " + pluginId));

        Set<UUID> installedPluginIds = getInstalledPluginIds(userId);
        PluginDto pluginDto = toPluginDto(plugin, installedPluginIds, userId);

        List<PluginVersion> versions = pluginVersionRepository.findByPluginIdOrderByPublishedAtDesc(pluginId);
        PluginVersion latestVersion = versions.isEmpty() ? null : versions.get(0);

        List<PluginVersionDto> versionDtos = versions.stream()
                .map(this::toPluginVersionDto)
                .collect(Collectors.toList());

        return PluginDetailDto.builder()
                .plugin(pluginDto)
                .readme(generateReadme(plugin, latestVersion))
                .changelog(generateChangelog(versions))
                .capabilities(latestVersion != null ? latestVersion.getCapabilities() : List.of())
                .configSchema(latestVersion != null ? latestVersion.getConfigSchema() : Map.of())
                .nodeDefinitions(latestVersion != null ? latestVersion.getNodeDefinitions() : Map.of())
                .versions(versionDtos)
                .build();
    }

    /**
     * Get installed plugins for a user.
     */
    public List<PluginDto> getInstalledPlugins(UUID userId) {
        List<PluginInstallation> installations = pluginInstallationRepository.findByUserIdWithDetails(userId);

        return installations.stream()
                .map(inst -> {
                    Plugin plugin = inst.getPlugin();
                    PluginVersion installedVersion = inst.getPluginVersion();
                    PluginVersion latestVersion = pluginVersionRepository.findLatestByPluginId(plugin.getId())
                            .orElse(installedVersion);

                    Long downloads = pluginVersionRepository.getTotalDownloads(plugin.getId());
                    Double rating = pluginRatingRepository.getAverageRating(plugin.getId());
                    Long ratingCount = pluginRatingRepository.getRatingCount(plugin.getId());

                    return PluginDto.builder()
                            .id(plugin.getId())
                            .name(plugin.getName())
                            .displayName(plugin.getDisplayName())
                            .description(plugin.getDescription())
                            .category(plugin.getCategory())
                            .author(plugin.getAuthor())
                            .iconUrl(plugin.getIconUrl())
                            .pricing(plugin.getPricing())
                            .price(plugin.getPrice())
                            .tags(plugin.getTags())
                            .version(latestVersion != null ? latestVersion.getVersion() : null)
                            .downloads(downloads != null ? downloads : 0L)
                            .rating(rating != null ? rating : 0.0)
                            .ratingCount(ratingCount != null ? ratingCount : 0L)
                            .isInstalled(true)
                            .installedVersion(installedVersion.getVersion())
                            .updatedAt(plugin.getUpdatedAt())
                            .publishedAt(latestVersion != null ? latestVersion.getPublishedAt() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Install a plugin for a user.
     */
    @Transactional
    public Map<String, Object> installPlugin(UUID pluginId, UUID userId, InstallPluginRequest request) {
        Plugin plugin = pluginRepository.findById(pluginId)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin not found: " + pluginId));

        // Check if already installed
        if (pluginInstallationRepository.existsByPluginIdAndUserId(pluginId, userId)) {
            throw new IllegalStateException("Plugin already installed");
        }

        // Get version to install
        PluginVersion version;
        if (request != null && request.getVersion() != null) {
            version = pluginVersionRepository.findByPluginIdAndVersion(pluginId, request.getVersion())
                    .orElseThrow(() -> new IllegalArgumentException("Version not found: " + request.getVersion()));
        } else {
            version = pluginVersionRepository.findLatestByPluginId(pluginId)
                    .orElseThrow(() -> new IllegalStateException("No versions available for plugin"));
        }

        // Create installation
        PluginInstallation installation = new PluginInstallation();
        installation.setPluginId(pluginId);
        installation.setPluginVersionId(version.getId());
        installation.setUserId(userId);
        installation.setConfig(request != null ? request.getConfig() : null);
        pluginInstallationRepository.save(installation);

        // Increment download count
        pluginVersionRepository.incrementDownloadCount(version.getId());

        // Register plugin nodes
        pluginNodeRegistrar.registerPluginNodes(plugin, version, userId);

        log.info("Plugin {} version {} installed for user {}", plugin.getName(), version.getVersion(), userId);

        return Map.of(
                "success", true,
                "message", "Plugin installed successfully",
                "installedVersion", version.getVersion()
        );
    }

    /**
     * Uninstall a plugin for a user.
     */
    @Transactional
    public Map<String, Object> uninstallPlugin(UUID pluginId, UUID userId) {
        PluginInstallation installation = pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not installed"));

        Plugin plugin = pluginRepository.findById(pluginId)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin not found"));

        // Unregister plugin nodes
        pluginNodeRegistrar.unregisterPluginNodes(plugin, userId);

        // Delete installation
        pluginInstallationRepository.deleteByPluginIdAndUserId(pluginId, userId);

        log.info("Plugin {} uninstalled for user {}", plugin.getName(), userId);

        return Map.of(
                "success", true,
                "message", "Plugin uninstalled successfully"
        );
    }

    /**
     * Update a plugin to the latest version.
     */
    @Transactional
    public Map<String, Object> updatePlugin(UUID pluginId, UUID userId) {
        PluginInstallation installation = pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not installed"));

        Plugin plugin = pluginRepository.findById(pluginId)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin not found"));

        PluginVersion latestVersion = pluginVersionRepository.findLatestByPluginId(pluginId)
                .orElseThrow(() -> new IllegalStateException("No versions available"));

        if (latestVersion.getId().equals(installation.getPluginVersionId())) {
            return Map.of(
                    "success", true,
                    "message", "Already on latest version",
                    "installedVersion", latestVersion.getVersion()
            );
        }

        // Update installation
        installation.setPluginVersionId(latestVersion.getId());
        pluginInstallationRepository.save(installation);

        // Increment download count
        pluginVersionRepository.incrementDownloadCount(latestVersion.getId());

        // Re-register plugin nodes with new version
        pluginNodeRegistrar.unregisterPluginNodes(plugin, userId);
        pluginNodeRegistrar.registerPluginNodes(plugin, latestVersion, userId);

        log.info("Plugin {} updated to version {} for user {}", plugin.getName(), latestVersion.getVersion(), userId);

        return Map.of(
                "success", true,
                "message", "Plugin updated successfully",
                "installedVersion", latestVersion.getVersion()
        );
    }

    /**
     * Rate a plugin.
     */
    @Transactional
    public Map<String, Object> ratePlugin(UUID pluginId, UUID userId, int rating, String review) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        PluginRating pluginRating = pluginRatingRepository.findByPluginIdAndUserId(pluginId, userId)
                .orElse(new PluginRating());

        pluginRating.setPluginId(pluginId);
        pluginRating.setUserId(userId);
        pluginRating.setRating(rating);
        pluginRating.setReview(review);

        pluginRatingRepository.save(pluginRating);

        return Map.of(
                "success", true,
                "message", "Rating submitted"
        );
    }

    // Helper methods

    private Set<UUID> getInstalledPluginIds(UUID userId) {
        if (userId == null) return Set.of();
        return pluginInstallationRepository.findByUserId(userId).stream()
                .map(PluginInstallation::getPluginId)
                .collect(Collectors.toSet());
    }

    private PluginDto toPluginDto(Plugin plugin, Set<UUID> installedPluginIds, UUID userId) {
        PluginVersion latestVersion = pluginVersionRepository.findLatestByPluginId(plugin.getId()).orElse(null);
        Long downloads = pluginVersionRepository.getTotalDownloads(plugin.getId());
        Double rating = pluginRatingRepository.getAverageRating(plugin.getId());
        Long ratingCount = pluginRatingRepository.getRatingCount(plugin.getId());

        String installedVersion = null;
        if (installedPluginIds.contains(plugin.getId())) {
            PluginInstallation installation = pluginInstallationRepository.findByPluginIdAndUserId(plugin.getId(), userId)
                    .orElse(null);
            if (installation != null) {
                PluginVersion instVersion = pluginVersionRepository.findById(installation.getPluginVersionId())
                        .orElse(null);
                if (instVersion != null) {
                    installedVersion = instVersion.getVersion();
                }
            }
        }

        return PluginDto.builder()
                .id(plugin.getId())
                .name(plugin.getName())
                .displayName(plugin.getDisplayName())
                .description(plugin.getDescription())
                .category(plugin.getCategory())
                .author(plugin.getAuthor())
                .authorUrl(plugin.getAuthorUrl())
                .repositoryUrl(plugin.getRepositoryUrl())
                .documentationUrl(plugin.getDocumentationUrl())
                .iconUrl(plugin.getIconUrl())
                .pricing(plugin.getPricing())
                .price(plugin.getPrice())
                .tags(plugin.getTags())
                .version(latestVersion != null ? latestVersion.getVersion() : null)
                .downloads(downloads != null ? downloads : 0L)
                .rating(rating != null ? rating : 0.0)
                .ratingCount(ratingCount != null ? ratingCount : 0L)
                .isInstalled(installedPluginIds.contains(plugin.getId()))
                .installedVersion(installedVersion)
                .updatedAt(plugin.getUpdatedAt())
                .publishedAt(latestVersion != null ? latestVersion.getPublishedAt() : null)
                .build();
    }

    private PluginVersionDto toPluginVersionDto(PluginVersion version) {
        return PluginVersionDto.builder()
                .id(version.getId())
                .pluginId(version.getPluginId())
                .version(version.getVersion())
                .releaseNotes(version.getReleaseNotes())
                .minPlatformVersion(version.getMinPlatformVersion())
                .configSchema(version.getConfigSchema())
                .nodeDefinitions(version.getNodeDefinitions())
                .capabilities(version.getCapabilities())
                .dependencies(version.getDependencies())
                .downloadCount(version.getDownloadCount())
                .publishedAt(version.getPublishedAt())
                .build();
    }

    private String generateReadme(Plugin plugin, PluginVersion version) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(plugin.getDisplayName()).append("\n\n");
        sb.append(plugin.getDescription()).append("\n\n");

        if (version != null && version.getCapabilities() != null && !version.getCapabilities().isEmpty()) {
            sb.append("## Required Capabilities\n\n");
            for (String cap : version.getCapabilities()) {
                sb.append("- ").append(cap).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Author\n\n").append(plugin.getAuthor()).append("\n");

        return sb.toString();
    }

    private String generateChangelog(List<PluginVersion> versions) {
        StringBuilder sb = new StringBuilder();
        for (PluginVersion version : versions) {
            sb.append("## v").append(version.getVersion()).append("\n");
            sb.append("*").append(version.getPublishedAt().toLocalDate()).append("*\n\n");
            if (version.getReleaseNotes() != null && !version.getReleaseNotes().isBlank()) {
                sb.append(version.getReleaseNotes()).append("\n\n");
            }
        }
        return sb.toString();
    }
}
