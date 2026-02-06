package com.aiinpocket.n3n.marketplace.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.marketplace.entity.Plugin;
import com.aiinpocket.n3n.marketplace.entity.Plugin.PluginType;
import com.aiinpocket.n3n.marketplace.entity.PluginReview;
import com.aiinpocket.n3n.marketplace.entity.PluginVersion;
import com.aiinpocket.n3n.marketplace.repository.PluginRepository;
import com.aiinpocket.n3n.marketplace.repository.PluginReviewRepository;
import com.aiinpocket.n3n.marketplace.repository.PluginVersionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PluginServiceTest extends BaseServiceTest {

    @Mock
    private PluginRepository pluginRepository;

    @Mock
    private PluginVersionRepository versionRepository;

    @Mock
    private PluginReviewRepository reviewRepository;

    @InjectMocks
    private PluginService pluginService;

    // ========== Create Plugin Tests ==========

    @Test
    void createPlugin_validPlugin_createsSuccessfully() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");

        when(pluginRepository.existsByName("my-plugin")).thenReturn(false);
        when(pluginRepository.save(any(Plugin.class))).thenAnswer(invocation -> {
            Plugin p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // When
        Plugin result = pluginService.createPlugin(plugin);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("my-plugin");
        assertThat(result.getCreatedAt()).isNotNull();
        verify(pluginRepository).save(any(Plugin.class));
    }

    @Test
    void createPlugin_duplicateName_throwsException() {
        // Given
        Plugin plugin = createTestPlugin("existing-plugin");
        when(pluginRepository.existsByName("existing-plugin")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> pluginService.createPlugin(plugin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ========== Update Plugin Tests ==========

    @Test
    void updatePlugin_existingPlugin_updatesFields() {
        // Given
        Plugin existing = createTestPlugin("my-plugin");
        existing.setId(UUID.randomUUID());

        Plugin updates = new Plugin();
        updates.setDisplayName("Updated Plugin");
        updates.setDescription("Updated description");
        updates.setCategory("updated-category");

        when(pluginRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(pluginRepository.save(any(Plugin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Plugin result = pluginService.updatePlugin(existing.getId(), updates);

        // Then
        assertThat(result.getDisplayName()).isEqualTo("Updated Plugin");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getCategory()).isEqualTo("updated-category");
    }

    @Test
    void updatePlugin_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(pluginRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> pluginService.updatePlugin(id, new Plugin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plugin not found");
    }

    @Test
    void updatePlugin_nullFields_doesNotOverwrite() {
        // Given
        Plugin existing = createTestPlugin("my-plugin");
        existing.setId(UUID.randomUUID());
        existing.setDisplayName("Original Name");
        existing.setDescription("Original Description");

        Plugin updates = new Plugin();
        updates.setCategory("new-category");
        // displayName and description are null

        when(pluginRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(pluginRepository.save(any(Plugin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Plugin result = pluginService.updatePlugin(existing.getId(), updates);

        // Then
        assertThat(result.getDisplayName()).isEqualTo("Original Name");
        assertThat(result.getDescription()).isEqualTo("Original Description");
        assertThat(result.getCategory()).isEqualTo("new-category");
    }

    // ========== Delete Plugin Tests ==========

    @Test
    void deletePlugin_existingPlugin_deletes() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));

        // When
        pluginService.deletePlugin(plugin.getId());

        // Then
        verify(pluginRepository).delete(plugin);
    }

    @Test
    void deletePlugin_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(pluginRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> pluginService.deletePlugin(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plugin not found");
    }

    // ========== Publish/Unpublish Tests ==========

    @Test
    void publishPlugin_withVersion_publishes() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());
        PluginVersion version = createTestVersion(plugin, "1.0.0");

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(versionRepository.findFirstByPluginIdAndYankedFalseOrderByPublishedAtDesc(plugin.getId()))
                .thenReturn(Optional.of(version));
        when(pluginRepository.save(any(Plugin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Plugin result = pluginService.publishPlugin(plugin.getId());

        // Then
        assertThat(result.isPublished()).isTrue();
        assertThat(result.getPublishedAt()).isNotNull();
    }

    @Test
    void publishPlugin_withoutVersion_throwsException() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(versionRepository.findFirstByPluginIdAndYankedFalseOrderByPublishedAtDesc(plugin.getId()))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> pluginService.publishPlugin(plugin.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without a version");
    }

    @Test
    void unpublishPlugin_existingPlugin_unpublishes() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());
        plugin.setPublished(true);

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(pluginRepository.save(any(Plugin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Plugin result = pluginService.unpublishPlugin(plugin.getId());

        // Then
        assertThat(result.isPublished()).isFalse();
    }

    // ========== Version Management Tests ==========

    @Test
    void addVersion_newVersion_createsSuccessfully() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());
        PluginVersion version = new PluginVersion();
        version.setVersion("1.0.0");

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(versionRepository.existsByPluginIdAndVersion(plugin.getId(), "1.0.0")).thenReturn(false);
        when(versionRepository.save(any(PluginVersion.class))).thenAnswer(invocation -> {
            PluginVersion v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        // When
        PluginVersion result = pluginService.addVersion(plugin.getId(), version);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPlugin()).isEqualTo(plugin);
        verify(versionRepository).save(any(PluginVersion.class));
    }

    @Test
    void addVersion_duplicateVersion_throwsException() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());
        PluginVersion version = new PluginVersion();
        version.setVersion("1.0.0");

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(versionRepository.existsByPluginIdAndVersion(plugin.getId(), "1.0.0")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> pluginService.addVersion(plugin.getId(), version))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version already exists");
    }

    @Test
    void getVersions_existingPlugin_returnsVersionList() {
        // Given
        UUID pluginId = UUID.randomUUID();
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(pluginId);
        PluginVersion v1 = createTestVersion(plugin, "1.0.0");
        PluginVersion v2 = createTestVersion(plugin, "2.0.0");

        when(versionRepository.findByPluginIdOrderByPublishedAtDesc(pluginId)).thenReturn(List.of(v2, v1));

        // When
        List<PluginVersion> result = pluginService.getVersions(pluginId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void yankVersion_existingVersion_setsYanked() {
        // Given
        UUID pluginId = UUID.randomUUID();
        PluginVersion version = new PluginVersion();
        version.setVersion("1.0.0");
        version.setYanked(false);

        when(versionRepository.findByPluginIdAndVersion(pluginId, "1.0.0")).thenReturn(Optional.of(version));
        when(versionRepository.save(any(PluginVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        pluginService.yankVersion(pluginId, "1.0.0");

        // Then
        verify(versionRepository).save(argThat(PluginVersion::isYanked));
    }

    // ========== Search and Browse Tests ==========

    @Test
    void browsePlugins_returnsPublishedPlugins() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setPublished(true);

        when(pluginRepository.findByPublishedTrue(pageable)).thenReturn(new PageImpl<>(List.of(plugin)));

        // When
        Page<Plugin> result = pluginService.browsePlugins(pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void search_withQuery_returnsMatchingPlugins() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Plugin plugin = createTestPlugin("flow-helper");

        when(pluginRepository.search("flow", pageable)).thenReturn(new PageImpl<>(List.of(plugin)));

        // When
        Page<Plugin> result = pluginService.search("flow", pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getFeatured_returnsLimitedResults() {
        // Given
        Plugin plugin = createTestPlugin("featured-plugin");
        when(pluginRepository.findByPublishedTrueAndFeaturedTrueOrderByDownloadCountDesc(any()))
                .thenReturn(List.of(plugin));

        // When
        List<Plugin> result = pluginService.getFeatured(5);

        // Then
        assertThat(result).hasSize(1);
    }

    // ========== Review Tests ==========

    @Test
    void addReview_newReview_createsAndUpdatesRating() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());

        PluginReview review = PluginReview.builder()
                .userId(UUID.randomUUID())
                .rating(5)
                .title("Great plugin")
                .content("Really useful")
                .build();

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(reviewRepository.existsByPluginIdAndUserId(plugin.getId(), review.getUserId())).thenReturn(false);
        when(reviewRepository.save(any(PluginReview.class))).thenAnswer(invocation -> {
            PluginReview r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(reviewRepository.getAverageRating(plugin.getId())).thenReturn(5.0);
        when(reviewRepository.countByPluginId(plugin.getId())).thenReturn(1L);

        // When
        PluginReview result = pluginService.addReview(plugin.getId(), review);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPlugin()).isEqualTo(plugin);
        verify(pluginRepository, times(1)).save(argThat(p ->
                p.getRatingCount() == 1));
    }

    @Test
    void addReview_duplicateReview_throwsException() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());
        UUID userId = UUID.randomUUID();

        PluginReview review = PluginReview.builder()
                .userId(userId)
                .rating(4)
                .build();

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(reviewRepository.existsByPluginIdAndUserId(plugin.getId(), userId)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> pluginService.addReview(plugin.getId(), review))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void deleteReview_existingReview_deletesAndUpdatesRating() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());

        PluginReview review = PluginReview.builder()
                .id(UUID.randomUUID())
                .plugin(plugin)
                .userId(UUID.randomUUID())
                .rating(3)
                .build();

        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.getAverageRating(plugin.getId())).thenReturn(null);
        when(reviewRepository.countByPluginId(plugin.getId())).thenReturn(0L);
        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));

        // When
        pluginService.deleteReview(review.getId());

        // Then
        verify(reviewRepository).delete(review);
        verify(pluginRepository).save(argThat(p ->
                p.getRatingAvg().compareTo(BigDecimal.ZERO) == 0 && p.getRatingCount() == 0));
    }

    // ========== Download Tests ==========

    @Test
    void recordDownload_incrementsCounters() {
        // Given
        Plugin plugin = createTestPlugin("my-plugin");
        plugin.setId(UUID.randomUUID());
        plugin.setDownloadCount(10);
        plugin.setWeeklyDownloads(3);

        PluginVersion version = createTestVersion(plugin, "1.0.0");
        version.setDownloadCount(5);

        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(versionRepository.findByPluginIdAndVersion(plugin.getId(), "1.0.0"))
                .thenReturn(Optional.of(version));
        when(pluginRepository.save(any(Plugin.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any(PluginVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        pluginService.recordDownload(plugin.getId(), "1.0.0");

        // Then
        verify(pluginRepository).save(argThat(p ->
                p.getDownloadCount() == 11 && p.getWeeklyDownloads() == 4));
        verify(versionRepository).save(argThat(v -> v.getDownloadCount() == 6));
    }

    // ========== Statistics Tests ==========

    @Test
    void getStats_returnsCorrectCounts() {
        // Given
        when(pluginRepository.count()).thenReturn(100L);
        when(pluginRepository.countByPublishedTrueAndType(PluginType.LOCAL_AGENT)).thenReturn(20L);
        when(pluginRepository.countByPublishedTrueAndType(PluginType.SKILL)).thenReturn(30L);
        when(pluginRepository.countByPublishedTrueAndType(PluginType.NODE)).thenReturn(25L);
        when(pluginRepository.countByPublishedTrueAndType(PluginType.THEME)).thenReturn(15L);
        when(pluginRepository.countByPublishedTrueAndType(PluginType.INTEGRATION)).thenReturn(10L);

        // When
        PluginService.MarketplaceStats stats = pluginService.getStats();

        // Then
        assertThat(stats.totalPlugins()).isEqualTo(100L);
        assertThat(stats.localAgents()).isEqualTo(20L);
        assertThat(stats.skills()).isEqualTo(30L);
        assertThat(stats.nodes()).isEqualTo(25L);
        assertThat(stats.themes()).isEqualTo(15L);
        assertThat(stats.integrations()).isEqualTo(10L);
    }

    // ========== Helper Methods ==========

    private Plugin createTestPlugin(String name) {
        return Plugin.builder()
                .id(UUID.randomUUID())
                .type(PluginType.SKILL)
                .name(name)
                .displayName("Test Plugin")
                .description("A test plugin")
                .authorId(UUID.randomUUID())
                .authorName("Test Author")
                .category("test")
                .downloadCount(0)
                .weeklyDownloads(0)
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0)
                .published(false)
                .featured(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PluginVersion createTestVersion(Plugin plugin, String versionStr) {
        return PluginVersion.builder()
                .id(UUID.randomUUID())
                .plugin(plugin)
                .version(versionStr)
                .downloadCount(0)
                .prerelease(false)
                .yanked(false)
                .publishedAt(LocalDateTime.now())
                .build();
    }
}
