package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.plugin.dto.*;
import com.aiinpocket.n3n.plugin.entity.*;
import com.aiinpocket.n3n.plugin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PluginServiceTest extends BaseServiceTest {

    @Mock private PluginRepository pluginRepository;
    @Mock private PluginVersionRepository pluginVersionRepository;
    @Mock private PluginInstallationRepository pluginInstallationRepository;
    @Mock private PluginRatingRepository pluginRatingRepository;
    @Mock private PluginNodeRegistrar pluginNodeRegistrar;

    private PluginService pluginService;

    private final UUID userId = UUID.randomUUID();
    private final UUID pluginId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pluginService = new PluginService(
                pluginRepository,
                pluginVersionRepository,
                pluginInstallationRepository,
                pluginRatingRepository,
                pluginNodeRegistrar
        );
    }

    // ==================== getCategories ====================

    @Test
    void getCategories_shouldReturnCategories() {
        List<Object[]> categories = new ArrayList<>();
        categories.add(new Object[]{"ai", 5L});
        categories.add(new Object[]{"data", 3L});
        when(pluginRepository.countByCategory()).thenReturn(categories);

        List<PluginCategoryDto> result = pluginService.getCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("ai");
        assertThat(result.get(0).getDisplayName()).isEqualTo("AI & ML");
        assertThat(result.get(0).getCount()).isEqualTo(5L);
        assertThat(result.get(1).getName()).isEqualTo("data");
        assertThat(result.get(1).getCount()).isEqualTo(3L);
    }

    @Test
    void getCategories_shouldReturnEmptyList() {
        when(pluginRepository.countByCategory()).thenReturn(List.of());

        List<PluginCategoryDto> result = pluginService.getCategories();

        assertThat(result).isEmpty();
    }

    // ==================== searchPlugins ====================

    @Test
    void searchPlugins_shouldReturnResults() {
        Plugin plugin = samplePlugin();
        Page<Plugin> page = new PageImpl<>(List.of(plugin));
        when(pluginRepository.searchPlugins(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        mockBatchDependencies(plugin);

        PluginSearchResult result = pluginService.searchPlugins(null, null, null, "popular", 0, 10, userId);

        assertThat(result.getPlugins()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getPageSize()).isEqualTo(10);
    }

    @Test
    void searchPlugins_shouldFilterByCategory() {
        Plugin plugin = samplePlugin();
        Page<Plugin> page = new PageImpl<>(List.of(plugin));
        when(pluginRepository.searchPlugins(eq("ai"), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        mockBatchDependencies(plugin);

        PluginSearchResult result = pluginService.searchPlugins("ai", null, null, null, 0, 10, userId);

        assertThat(result.getPlugins()).hasSize(1);
        verify(pluginRepository).searchPlugins(eq("ai"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void searchPlugins_shouldTreatAllCategoryAsNull() {
        Page<Plugin> page = new PageImpl<>(List.of());
        when(pluginRepository.searchPlugins(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        pluginService.searchPlugins("all", "all", "", null, 0, 10, null);

        verify(pluginRepository).searchPlugins(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void searchPlugins_shouldSortByName() {
        Page<Plugin> page = new PageImpl<>(List.of());
        when(pluginRepository.searchPlugins(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        pluginService.searchPlugins(null, null, null, "name", 0, 10, null);

        verify(pluginRepository).searchPlugins(any(), any(), any(), argThat(pageable ->
                pageable.getSort().getOrderFor("display_name") != null));
    }

    @Test
    void searchPlugins_shouldSortByRecent() {
        Page<Plugin> page = new PageImpl<>(List.of());
        when(pluginRepository.searchPlugins(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        pluginService.searchPlugins(null, null, null, "recent", 0, 10, null);

        verify(pluginRepository).searchPlugins(any(), any(), any(), argThat(pageable ->
                pageable.getSort().getOrderFor("updated_at") != null));
    }

    @Test
    void searchPlugins_withNullUserId_shouldNotFetchInstallations() {
        Page<Plugin> page = new PageImpl<>(List.of());
        when(pluginRepository.searchPlugins(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        pluginService.searchPlugins(null, null, null, null, 0, 10, null);

        verify(pluginInstallationRepository, never()).findByUserId(any());
    }

    @Test
    void searchPlugins_shouldReturnEmptyResults() {
        Page<Plugin> page = new PageImpl<>(List.of());
        when(pluginRepository.searchPlugins(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        PluginSearchResult result = pluginService.searchPlugins(null, null, null, null, 0, 10, null);

        assertThat(result.getPlugins()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0);
    }

    // ==================== getFeaturedPlugins ====================

    @Test
    void getFeaturedPlugins_shouldReturnTop6() {
        Plugin plugin = samplePlugin();
        Page<Plugin> page = new PageImpl<>(List.of(plugin));
        when(pluginRepository.findAll(any(Pageable.class))).thenReturn(page);
        mockBatchDependencies(plugin);

        List<PluginDto> result = pluginService.getFeaturedPlugins(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-plugin");
    }

    @Test
    void getFeaturedPlugins_withNullUserId_shouldWork() {
        Page<Plugin> page = new PageImpl<>(List.of());
        when(pluginRepository.findAll(any(Pageable.class))).thenReturn(page);

        List<PluginDto> result = pluginService.getFeaturedPlugins(null);

        assertThat(result).isEmpty();
    }

    // ==================== getPluginDetail ====================

    @Test
    void getPluginDetail_shouldReturnDetail() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginInstallationRepository.findByUserId(userId)).thenReturn(List.of());
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(version));
        when(pluginVersionRepository.getTotalDownloads(pluginId)).thenReturn(100L);
        when(pluginRatingRepository.getAverageRating(pluginId)).thenReturn(4.5);
        when(pluginRatingRepository.getRatingCount(pluginId)).thenReturn(10L);
        when(pluginVersionRepository.findByPluginIdOrderByPublishedAtDesc(pluginId)).thenReturn(List.of(version));

        PluginDetailDto result = pluginService.getPluginDetail(pluginId, userId);

        assertThat(result.getPlugin().getName()).isEqualTo("test-plugin");
        assertThat(result.getReadme()).contains("Test Plugin");
        assertThat(result.getVersions()).hasSize(1);
        assertThat(result.getCapabilities()).isEqualTo(List.of("http", "file"));
    }

    @Test
    void getPluginDetail_shouldThrowWhenNotFound() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.getPluginDetail(pluginId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(pluginId.toString());
    }

    @Test
    void getPluginDetail_withNoVersions_shouldReturnEmptyCapabilities() {
        Plugin plugin = samplePlugin();
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginInstallationRepository.findByUserId(userId)).thenReturn(List.of());
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.empty());
        when(pluginVersionRepository.getTotalDownloads(pluginId)).thenReturn(null);
        when(pluginRatingRepository.getAverageRating(pluginId)).thenReturn(null);
        when(pluginRatingRepository.getRatingCount(pluginId)).thenReturn(null);
        when(pluginVersionRepository.findByPluginIdOrderByPublishedAtDesc(pluginId)).thenReturn(List.of());

        PluginDetailDto result = pluginService.getPluginDetail(pluginId, userId);

        assertThat(result.getCapabilities()).isEmpty();
        assertThat(result.getConfigSchema()).isEmpty();
        assertThat(result.getNodeDefinitions()).isEmpty();
        assertThat(result.getVersions()).isEmpty();
    }

    // ==================== getInstalledPlugins ====================

    @Test
    void getInstalledPlugins_shouldReturnInstalled() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        PluginInstallation installation = new PluginInstallation();
        installation.setPluginId(pluginId);
        installation.setPluginVersionId(versionId);
        installation.setUserId(userId);
        installation.setPlugin(plugin);
        installation.setPluginVersion(version);

        when(pluginInstallationRepository.findByUserIdWithDetails(userId)).thenReturn(List.of(installation));
        when(pluginVersionRepository.findLatestByPluginIds(List.of(pluginId))).thenReturn(List.of(version));
        List<Object[]> downloadData = new ArrayList<>();
        downloadData.add(new Object[]{pluginId, 50L});
        when(pluginVersionRepository.getTotalDownloadsByPluginIds(List.of(pluginId)))
                .thenReturn(downloadData);
        List<Object[]> ratingData = new ArrayList<>();
        ratingData.add(new Object[]{pluginId, 4.0, 8L});
        when(pluginRatingRepository.getRatingStatsByPluginIds(List.of(pluginId)))
                .thenReturn(ratingData);

        List<PluginDto> result = pluginService.getInstalledPlugins(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsInstalled()).isTrue();
        assertThat(result.get(0).getInstalledVersion()).isEqualTo("1.0.0");
        assertThat(result.get(0).getDownloads()).isEqualTo(50L);
    }

    @Test
    void getInstalledPlugins_shouldReturnEmptyWhenNone() {
        when(pluginInstallationRepository.findByUserIdWithDetails(userId)).thenReturn(List.of());

        List<PluginDto> result = pluginService.getInstalledPlugins(userId);

        assertThat(result).isEmpty();
    }

    // ==================== installPlugin ====================

    @Test
    void installPlugin_shouldInstallLatestVersion() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginInstallationRepository.existsByPluginIdAndUserId(pluginId, userId)).thenReturn(false);
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(version));

        Map<String, Object> result = pluginService.installPlugin(pluginId, userId, null);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("installedVersion")).isEqualTo("1.0.0");
        verify(pluginInstallationRepository).save(any(PluginInstallation.class));
        verify(pluginVersionRepository).incrementDownloadCount(versionId);
        verify(pluginNodeRegistrar).registerPluginNodes(plugin, version, userId);
    }

    @Test
    void installPlugin_shouldInstallSpecificVersion() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        version.setVersion("0.9.0");
        InstallPluginRequest request = new InstallPluginRequest();
        request.setVersion("0.9.0");

        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginInstallationRepository.existsByPluginIdAndUserId(pluginId, userId)).thenReturn(false);
        when(pluginVersionRepository.findByPluginIdAndVersion(pluginId, "0.9.0")).thenReturn(Optional.of(version));

        Map<String, Object> result = pluginService.installPlugin(pluginId, userId, request);

        assertThat(result.get("installedVersion")).isEqualTo("0.9.0");
    }

    @Test
    void installPlugin_shouldThrowWhenAlreadyInstalled() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(samplePlugin()));
        when(pluginInstallationRepository.existsByPluginIdAndUserId(pluginId, userId)).thenReturn(true);

        assertThatThrownBy(() -> pluginService.installPlugin(pluginId, userId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Plugin already installed");
    }

    @Test
    void installPlugin_shouldThrowWhenPluginNotFound() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.installPlugin(pluginId, userId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void installPlugin_shouldThrowWhenVersionNotFound() {
        InstallPluginRequest request = new InstallPluginRequest();
        request.setVersion("99.0.0");
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(samplePlugin()));
        when(pluginInstallationRepository.existsByPluginIdAndUserId(pluginId, userId)).thenReturn(false);
        when(pluginVersionRepository.findByPluginIdAndVersion(pluginId, "99.0.0")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.installPlugin(pluginId, userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99.0.0");
    }

    @Test
    void installPlugin_shouldThrowWhenNoVersionsAvailable() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(samplePlugin()));
        when(pluginInstallationRepository.existsByPluginIdAndUserId(pluginId, userId)).thenReturn(false);
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.installPlugin(pluginId, userId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No versions available for plugin");
    }

    // ==================== uninstallPlugin ====================

    @Test
    void uninstallPlugin_shouldUninstall() {
        Plugin plugin = samplePlugin();
        PluginInstallation installation = new PluginInstallation();
        installation.setPluginId(pluginId);
        installation.setUserId(userId);

        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.of(installation));
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));

        Map<String, Object> result = pluginService.uninstallPlugin(pluginId, userId);

        assertThat(result.get("success")).isEqualTo(true);
        verify(pluginNodeRegistrar).unregisterPluginNodes(plugin, userId);
        verify(pluginInstallationRepository).deleteByPluginIdAndUserId(pluginId, userId);
    }

    @Test
    void uninstallPlugin_shouldThrowWhenNotInstalled() {
        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.uninstallPlugin(pluginId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Plugin not installed");
    }

    @Test
    void uninstallPlugin_shouldThrowWhenPluginNotFound() {
        PluginInstallation installation = new PluginInstallation();
        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.of(installation));
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.uninstallPlugin(pluginId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== updatePlugin ====================

    @Test
    void updatePlugin_shouldUpdateToLatestVersion() {
        UUID oldVersionId = UUID.randomUUID();
        UUID newVersionId = UUID.randomUUID();

        Plugin plugin = samplePlugin();
        PluginInstallation installation = new PluginInstallation();
        installation.setPluginId(pluginId);
        installation.setUserId(userId);
        installation.setPluginVersionId(oldVersionId);

        PluginVersion latestVersion = sampleVersion();
        latestVersion.setId(newVersionId);
        latestVersion.setVersion("2.0.0");

        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.of(installation));
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(latestVersion));

        Map<String, Object> result = pluginService.updatePlugin(pluginId, userId);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("installedVersion")).isEqualTo("2.0.0");
        verify(pluginInstallationRepository).save(installation);
        verify(pluginVersionRepository).incrementDownloadCount(newVersionId);
        verify(pluginNodeRegistrar).unregisterPluginNodes(plugin, userId);
        verify(pluginNodeRegistrar).registerPluginNodes(plugin, latestVersion, userId);
    }

    @Test
    void updatePlugin_shouldReturnAlreadyLatest() {
        Plugin plugin = samplePlugin();
        PluginInstallation installation = new PluginInstallation();
        installation.setPluginVersionId(versionId);

        PluginVersion latestVersion = sampleVersion();

        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.of(installation));
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(latestVersion));

        Map<String, Object> result = pluginService.updatePlugin(pluginId, userId);

        assertThat(result.get("message")).isEqualTo("Already on latest version");
        verify(pluginNodeRegistrar, never()).unregisterPluginNodes(any(), any());
    }

    @Test
    void updatePlugin_shouldThrowWhenNotInstalled() {
        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.updatePlugin(pluginId, userId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePlugin_shouldThrowWhenNoVersions() {
        PluginInstallation installation = new PluginInstallation();
        when(pluginInstallationRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.of(installation));
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(samplePlugin()));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pluginService.updatePlugin(pluginId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No versions available");
    }

    // ==================== ratePlugin ====================

    @Test
    void ratePlugin_shouldCreateNewRating() {
        when(pluginRatingRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.empty());

        Map<String, Object> result = pluginService.ratePlugin(pluginId, userId, 5, "Great!");

        assertThat(result.get("success")).isEqualTo(true);
        verify(pluginRatingRepository).save(argThat(r ->
                r.getRating() == 5 && "Great!".equals(r.getReview())));
    }

    @Test
    void ratePlugin_shouldUpdateExistingRating() {
        PluginRating existing = new PluginRating();
        existing.setPluginId(pluginId);
        existing.setUserId(userId);
        existing.setRating(3);
        when(pluginRatingRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.of(existing));

        pluginService.ratePlugin(pluginId, userId, 4, "Updated");

        verify(pluginRatingRepository).save(argThat(r ->
                r.getRating() == 4 && "Updated".equals(r.getReview())));
    }

    @Test
    void ratePlugin_shouldAllowNullReview() {
        when(pluginRatingRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.empty());

        Map<String, Object> result = pluginService.ratePlugin(pluginId, userId, 3, null);

        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    void ratePlugin_shouldThrowWhenRatingTooLow() {
        assertThatThrownBy(() -> pluginService.ratePlugin(pluginId, userId, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rating must be between 1 and 5");
    }

    @Test
    void ratePlugin_shouldThrowWhenRatingTooHigh() {
        assertThatThrownBy(() -> pluginService.ratePlugin(pluginId, userId, 6, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rating must be between 1 and 5");
    }

    @Test
    void ratePlugin_shouldThrowWhenReviewTooLong() {
        String longReview = "x".repeat(2001);

        assertThatThrownBy(() -> pluginService.ratePlugin(pluginId, userId, 5, longReview))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Review must not exceed 2000 characters");
    }

    @Test
    void ratePlugin_shouldAcceptMaxLengthReview() {
        String maxReview = "x".repeat(2000);
        when(pluginRatingRepository.findByPluginIdAndUserId(pluginId, userId))
                .thenReturn(Optional.empty());

        Map<String, Object> result = pluginService.ratePlugin(pluginId, userId, 5, maxReview);

        assertThat(result.get("success")).isEqualTo(true);
    }

    // ==================== Helper methods ====================

    private Plugin samplePlugin() {
        Plugin p = new Plugin();
        p.setId(pluginId);
        p.setName("test-plugin");
        p.setDisplayName("Test Plugin");
        p.setDescription("A test plugin for unit testing");
        p.setCategory("ai");
        p.setAuthor("Test Author");
        p.setPricing("free");
        p.setTags(List.of("test", "ai"));
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private PluginVersion sampleVersion() {
        PluginVersion v = new PluginVersion();
        v.setId(versionId);
        v.setPluginId(pluginId);
        v.setVersion("1.0.0");
        v.setReleaseNotes("Initial release");
        v.setCapabilities(List.of("http", "file"));
        v.setConfigSchema(Map.of("apiKey", Map.of("type", "string")));
        v.setNodeDefinitions(Map.of("testNode", Map.of("type", "action")));
        v.setDownloadCount(100);
        v.setPublishedAt(LocalDateTime.now());
        return v;
    }

    private void mockBatchDependencies(Plugin plugin) {
        PluginVersion version = sampleVersion();
        lenient().when(pluginInstallationRepository.findByUserId(userId)).thenReturn(List.of());
        lenient().when(pluginVersionRepository.findLatestByPluginIds(List.of(pluginId)))
                .thenReturn(List.of(version));
        List<Object[]> downloadData = new ArrayList<>();
        downloadData.add(new Object[]{pluginId, 100L});
        lenient().when(pluginVersionRepository.getTotalDownloadsByPluginIds(List.of(pluginId)))
                .thenReturn(downloadData);
        List<Object[]> ratingData = new ArrayList<>();
        ratingData.add(new Object[]{pluginId, 4.5, 10L});
        lenient().when(pluginRatingRepository.getRatingStatsByPluginIds(List.of(pluginId)))
                .thenReturn(ratingData);
    }
}
