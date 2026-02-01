package com.aiinpocket.n3n.marketplace.controller;

import com.aiinpocket.n3n.marketplace.entity.Plugin;
import com.aiinpocket.n3n.marketplace.entity.PluginReview;
import com.aiinpocket.n3n.marketplace.entity.PluginVersion;
import com.aiinpocket.n3n.marketplace.service.PluginService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the Plugin Marketplace.
 */
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
public class PluginController {

    private final PluginService pluginService;

    // Browse and Search

    @GetMapping("/plugins")
    public ResponseEntity<Page<PluginDto>> browsePlugins(
            @RequestParam(required = false) Plugin.PluginType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "downloadCount") String sort,
            @RequestParam(defaultValue = "desc") String order) {

        Sort sortOrder = Sort.by(Sort.Direction.fromString(order), sort);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Page<Plugin> plugins;

        if (q != null && !q.isEmpty()) {
            plugins = pluginService.search(q, pageable);
        } else if (type != null) {
            plugins = pluginService.browseByType(type, pageable);
        } else if (category != null) {
            plugins = pluginService.browseByCategory(category, pageable);
        } else {
            plugins = pluginService.browsePlugins(pageable);
        }

        return ResponseEntity.ok(plugins.map(this::toDto));
    }

    @GetMapping("/plugins/featured")
    public ResponseEntity<List<PluginDto>> getFeatured(@RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(pluginService.getFeatured(limit).stream()
            .map(this::toDto)
            .toList());
    }

    @GetMapping("/plugins/popular")
    public ResponseEntity<List<PluginDto>> getPopular(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(pluginService.getPopular(limit).stream()
            .map(this::toDto)
            .toList());
    }

    @GetMapping("/plugins/trending")
    public ResponseEntity<List<PluginDto>> getTrending(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(pluginService.getTrending(limit).stream()
            .map(this::toDto)
            .toList());
    }

    @GetMapping("/plugins/recent")
    public ResponseEntity<List<PluginDto>> getRecent(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(pluginService.getRecentlyUpdated(limit).stream()
            .map(this::toDto)
            .toList());
    }

    @GetMapping("/plugins/top-rated")
    public ResponseEntity<List<PluginDto>> getTopRated(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(pluginService.getTopRated(limit).stream()
            .map(this::toDto)
            .toList());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(pluginService.getCategories());
    }

    // Plugin Details

    @GetMapping("/plugins/{id}")
    public ResponseEntity<PluginDetailDto> getPlugin(@PathVariable UUID id) {
        return pluginService.getPlugin(id)
            .map(this::toDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/plugins/name/{name}")
    public ResponseEntity<PluginDetailDto> getPluginByName(@PathVariable String name) {
        return pluginService.getPluginByName(name)
            .map(this::toDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // Versions

    @GetMapping("/plugins/{id}/versions")
    public ResponseEntity<List<VersionDto>> getVersions(@PathVariable UUID id) {
        List<VersionDto> versions = pluginService.getVersions(id).stream()
            .map(this::toVersionDto)
            .toList();
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/plugins/{id}/versions/latest")
    public ResponseEntity<VersionDto> getLatestVersion(@PathVariable UUID id) {
        return pluginService.getLatestVersion(id)
            .map(this::toVersionDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // Reviews

    @GetMapping("/plugins/{id}/reviews")
    public ResponseEntity<Page<ReviewDto>> getReviews(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<PluginReview> reviews = pluginService.getReviews(id, pageable);

        return ResponseEntity.ok(reviews.map(this::toReviewDto));
    }

    @PostMapping("/plugins/{id}/reviews")
    public ResponseEntity<ReviewDto> addReview(
            @PathVariable UUID id,
            @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        PluginReview review = PluginReview.builder()
            .userId(getUserId(userDetails))
            .userName(userDetails.getUsername())
            .rating(request.rating())
            .title(request.title())
            .content(request.content())
            .version(request.version())
            .build();

        PluginReview saved = pluginService.addReview(id, review);
        return ResponseEntity.ok(toReviewDto(saved));
    }

    // Downloads

    @PostMapping("/plugins/{id}/download")
    public ResponseEntity<DownloadResponse> download(
            @PathVariable UUID id,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String platform) {

        // Record download
        pluginService.recordDownload(id, version);

        // Get version info
        PluginVersion v;
        if (version != null) {
            v = pluginService.getVersions(id).stream()
                .filter(ver -> ver.getVersion().equals(version))
                .findFirst()
                .orElse(null);
        } else {
            v = pluginService.getLatestStableVersion(id).orElse(null);
        }

        if (v == null) {
            return ResponseEntity.notFound().build();
        }

        String downloadUrl;
        if (platform != null) {
            downloadUrl = switch (platform.toLowerCase()) {
                case "macos" -> v.getMacosUrl();
                case "windows" -> v.getWindowsUrl();
                case "linux" -> v.getLinuxUrl();
                default -> v.getPackageUrl();
            };
        } else {
            downloadUrl = v.getPackageUrl();
        }

        return ResponseEntity.ok(new DownloadResponse(
            v.getVersion(),
            downloadUrl,
            v.getPackageChecksum(),
            v.getPackageSize()
        ));
    }

    // Publisher Endpoints

    @PostMapping("/plugins")
    public ResponseEntity<PluginDto> createPlugin(
            @RequestBody CreatePluginRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Plugin plugin = Plugin.builder()
            .name(request.name())
            .displayName(request.displayName())
            .description(request.description())
            .type(request.type())
            .category(request.category())
            .authorId(getUserId(userDetails))
            .authorName(userDetails.getUsername())
            .build();

        Plugin saved = pluginService.createPlugin(plugin);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/plugins/{id}")
    public ResponseEntity<PluginDto> updatePlugin(
            @PathVariable UUID id,
            @RequestBody UpdatePluginRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Verify ownership
        Plugin existing = pluginService.getPlugin(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getAuthorId().equals(getUserId(userDetails))) {
            return ResponseEntity.status(403).build();
        }

        Plugin updates = Plugin.builder()
            .displayName(request.displayName())
            .description(request.description())
            .longDescription(request.longDescription())
            .iconUrl(request.iconUrl())
            .screenshots(request.screenshots() != null ? String.join(",", request.screenshots()) : null)
            .category(request.category())
            .tags(request.tags() != null ? String.join(",", request.tags()) : null)
            .build();

        Plugin saved = pluginService.updatePlugin(id, updates);
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/plugins/{id}/versions")
    public ResponseEntity<VersionDto> addVersion(
            @PathVariable UUID id,
            @RequestBody CreateVersionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Verify ownership
        Plugin existing = pluginService.getPlugin(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getAuthorId().equals(getUserId(userDetails))) {
            return ResponseEntity.status(403).build();
        }

        PluginVersion version = PluginVersion.builder()
            .version(request.version())
            .changelog(request.changelog())
            .macosUrl(request.macosUrl())
            .windowsUrl(request.windowsUrl())
            .linuxUrl(request.linuxUrl())
            .packageUrl(request.packageUrl())
            .packageChecksum(request.checksum())
            .packageSize(request.size())
            .minN3nVersion(request.minN3nVersion())
            .prerelease(request.prerelease())
            .build();

        PluginVersion saved = pluginService.addVersion(id, version);
        return ResponseEntity.ok(toVersionDto(saved));
    }

    @PostMapping("/plugins/{id}/publish")
    public ResponseEntity<PluginDto> publishPlugin(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Plugin existing = pluginService.getPlugin(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getAuthorId().equals(getUserId(userDetails))) {
            return ResponseEntity.status(403).build();
        }

        Plugin published = pluginService.publishPlugin(id);
        return ResponseEntity.ok(toDto(published));
    }

    // Statistics

    @GetMapping("/stats")
    public ResponseEntity<PluginService.MarketplaceStats> getStats() {
        return ResponseEntity.ok(pluginService.getStats());
    }

    // Helper methods

    private UUID getUserId(UserDetails userDetails) {
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes());
        }
    }

    private PluginDto toDto(Plugin plugin) {
        return new PluginDto(
            plugin.getId(),
            plugin.getName(),
            plugin.getDisplayName(),
            plugin.getDescription(),
            plugin.getType(),
            plugin.getIconUrl(),
            plugin.getCategory(),
            parseList(plugin.getTags()),
            plugin.getRatingAvg() != null ? plugin.getRatingAvg().doubleValue() : 0,
            plugin.getRatingCount(),
            plugin.getDownloadCount(),
            plugin.isPricingFree(),
            plugin.getPricingAmount() != null ? plugin.getPricingAmount().doubleValue() : null,
            plugin.getAuthorName(),
            plugin.isAuthorVerified()
        );
    }

    private List<String> parseList(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private PluginDetailDto toDetailDto(Plugin plugin) {
        PluginVersion latest = pluginService.getLatestVersion(plugin.getId()).orElse(null);

        return new PluginDetailDto(
            plugin.getId(),
            plugin.getName(),
            plugin.getDisplayName(),
            plugin.getDescription(),
            plugin.getLongDescription(),
            plugin.getType(),
            plugin.getIconUrl(),
            parseList(plugin.getScreenshots()),
            plugin.getCategory(),
            parseList(plugin.getTags()),
            plugin.getRatingAvg() != null ? plugin.getRatingAvg().doubleValue() : 0,
            plugin.getRatingCount(),
            plugin.getDownloadCount(),
            plugin.isPricingFree(),
            plugin.getPricingAmount() != null ? plugin.getPricingAmount().doubleValue() : null,
            plugin.getPricingCurrency(),
            plugin.getAuthorId(),
            plugin.getAuthorName(),
            plugin.isAuthorVerified(),
            latest != null ? latest.getVersion() : null,
            latest != null ? latest.getChangelog() : null,
            plugin.getMinN3nVersion(),
            parseList(plugin.getPlatforms()),
            plugin.getCreatedAt(),
            plugin.getUpdatedAt(),
            plugin.getPublishedAt()
        );
    }

    private VersionDto toVersionDto(PluginVersion version) {
        return new VersionDto(
            version.getId(),
            version.getVersion(),
            version.getChangelog(),
            version.getMacosUrl(),
            version.getWindowsUrl(),
            version.getLinuxUrl(),
            version.getPackageUrl(),
            version.getPackageChecksum(),
            version.getPackageSize(),
            version.getMinN3nVersion(),
            version.isPrerelease(),
            version.isYanked(),
            version.getDownloadCount(),
            version.getPublishedAt()
        );
    }

    private ReviewDto toReviewDto(PluginReview review) {
        return new ReviewDto(
            review.getId(),
            review.getUserId(),
            review.getUserName(),
            review.getRating(),
            review.getTitle(),
            review.getContent(),
            review.getVersion(),
            review.getHelpfulCount(),
            review.isVerifiedPurchase(),
            review.getCreatedAt()
        );
    }

    // DTOs
    public record PluginDto(
        UUID id,
        String name,
        String displayName,
        String description,
        Plugin.PluginType type,
        String iconUrl,
        String category,
        List<String> tags,
        double rating,
        int ratingCount,
        int downloadCount,
        boolean free,
        Double price,
        String authorName,
        boolean authorVerified
    ) {}

    public record PluginDetailDto(
        UUID id,
        String name,
        String displayName,
        String description,
        String longDescription,
        Plugin.PluginType type,
        String iconUrl,
        List<String> screenshots,
        String category,
        List<String> tags,
        double rating,
        int ratingCount,
        int downloadCount,
        boolean free,
        Double price,
        String currency,
        UUID authorId,
        String authorName,
        boolean authorVerified,
        String latestVersion,
        String changelog,
        String minN3nVersion,
        List<String> platforms,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt,
        java.time.LocalDateTime publishedAt
    ) {}

    public record VersionDto(
        UUID id,
        String version,
        String changelog,
        String macosUrl,
        String windowsUrl,
        String linuxUrl,
        String packageUrl,
        String checksum,
        Long size,
        String minN3nVersion,
        boolean prerelease,
        boolean yanked,
        int downloadCount,
        java.time.LocalDateTime publishedAt
    ) {}

    public record ReviewDto(
        UUID id,
        UUID userId,
        String userName,
        int rating,
        String title,
        String content,
        String version,
        int helpfulCount,
        boolean verifiedPurchase,
        java.time.LocalDateTime createdAt
    ) {}

    public record CreatePluginRequest(
        String name,
        String displayName,
        String description,
        Plugin.PluginType type,
        String category
    ) {}

    public record UpdatePluginRequest(
        String displayName,
        String description,
        String longDescription,
        String iconUrl,
        List<String> screenshots,
        String category,
        List<String> tags
    ) {}

    public record CreateVersionRequest(
        String version,
        String changelog,
        String macosUrl,
        String windowsUrl,
        String linuxUrl,
        String packageUrl,
        String checksum,
        Long size,
        String minN3nVersion,
        boolean prerelease
    ) {}

    public record CreateReviewRequest(
        int rating,
        String title,
        String content,
        String version
    ) {}

    public record DownloadResponse(
        String version,
        String downloadUrl,
        String checksum,
        Long size
    ) {}
}
