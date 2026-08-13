package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.site.dto.SiteFileMeta;
import com.aiinpocket.n3n.site.dto.SiteFileUpsertEntry;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.repository.SiteFileRepository;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.site.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteServiceTest extends BaseServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SiteFileRepository siteFileRepository;

    @Mock
    private HostedAppRepository hostedAppRepository;

    private SiteService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SiteService(siteRepository, siteFileRepository, hostedAppRepository);
        ReflectionTestUtils.setField(service, "maxFilesPerSite", 3);
        ReflectionTestUtils.setField(service, "maxFileBytes", 100L);
        ReflectionTestUtils.setField(service, "maxSiteBytes", 200L);
        ReflectionTestUtils.setField(service, "maxSitesPerUser", 5);
    }

    private Site ownedSite() {
        return Site.builder()
                .id(siteId)
                .ownerId(ownerId)
                .slug("my-site-ab12")
                .name("My Site")
                .isPublished(true)
                .build();
    }

    private void stubOwnedSite() {
        when(siteRepository.findByIdAndOwnerId(siteId, ownerId))
                .thenReturn(Optional.of(ownedSite()));
    }

    // ---------- slug ----------

    @Test
    @DisplayName("建立網站：slug 由名稱產生且符合格式")
    void createGeneratesValidSlug() {
        when(siteRepository.countByOwnerId(ownerId)).thenReturn(0L);
        when(siteRepository.existsBySlug(anyString())).thenReturn(false);
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        Site site = service.create(ownerId, "Hello World! 測試", "desc");

        assertThat(site.getSlug()).matches("^[a-z0-9-]{3,64}$");
        assertThat(site.getSlug()).startsWith("hello-world-");
        assertThat(site.isPublished()).isTrue();
    }

    @Test
    @DisplayName("slug 與小應用碰撞：hosted_apps 已占用的 slug 不可用（雙向命名空間互斥）")
    void slugCollidingWithHostedAppIsRejected() {
        when(siteRepository.countByOwnerId(ownerId)).thenReturn(0L);
        when(siteRepository.existsBySlug(anyString())).thenReturn(false);
        // 小應用側永遠回「已存在」→ 所有候選 slug 都被判定碰撞
        when(hostedAppRepository.existsBySlug(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(ownerId, "Hello World", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique site slug");
        verify(siteRepository, never()).save(any(Site.class));
    }

    @Test
    @DisplayName("建立網站時有查過小應用命名空間")
    void slugChecksHostedAppNamespace() {
        when(siteRepository.countByOwnerId(ownerId)).thenReturn(0L);
        when(siteRepository.existsBySlug(anyString())).thenReturn(false);
        when(hostedAppRepository.existsBySlug(anyString())).thenReturn(false);
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        Site site = service.create(ownerId, "Hello World", null);

        verify(hostedAppRepository).existsBySlug(site.getSlug());
    }

    @Test
    @DisplayName("純中文名稱：slug base 退回 site")
    void createWithNonAsciiName() {
        when(siteRepository.countByOwnerId(ownerId)).thenReturn(0L);
        when(siteRepository.existsBySlug(anyString())).thenReturn(false);
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        Site site = service.create(ownerId, "我的作品集", null);

        assertThat(site.getSlug()).matches("^site-[a-z0-9]{4}$");
    }

    @Test
    @DisplayName("保留字不能當 slug")
    void reservedSlugsRejected() {
        for (String reserved : List.of("api", "assets", "login", "admin", "sites", "share")) {
            assertThatThrownBy(() -> SiteSlugs.validate(reserved))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
        }
        // "ws" 不足 3 字元，先被格式規則擋下（同樣無法成為 slug）
        assertThatThrownBy(() -> SiteSlugs.validate("ws"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("slug 格式驗證：非法字元與長度")
    void slugFormatValidation() {
        assertThatThrownBy(() -> SiteSlugs.validate("UPPER-case")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteSlugs.validate("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteSlugs.validate("a".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteSlugs.validate("bad_slug")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SiteSlugs.validate("good-slug-123")).isEqualTo("good-slug-123");
    }

    @Test
    @DisplayName("超過網站數量上限時拒絕建立")
    void createRejectsWhenSiteLimitReached() {
        when(siteRepository.countByOwnerId(ownerId)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(ownerId, "One More", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        verify(siteRepository, never()).save(any());
    }

    // ---------- 擁有者隔離 ----------

    @Test
    @DisplayName("非擁有者存取一律 404（不洩漏存在性）")
    void ownerCheckFailsClosed() {
        when(siteRepository.findByIdAndOwnerId(siteId, otherUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(siteId, otherUser))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.upsertFiles(siteId, otherUser,
                List.of(SiteFileUpsertEntry.builder().path("index.html").content("<html></html>").build())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(siteId, otherUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- 路徑淨化 ----------

    @Test
    @DisplayName("路徑穿越與危險路徑一律拒絕")
    void pathTraversalRejected() {
        stubOwnedSite();
        List<String> evil = List.of(
                "../../../etc/passwd",
                "a/../secret.html",
                "/absolute.html",
                "a\\b.html",
                "nul\0l.html",
                "..",
                "assets/./x.js"
        );
        for (String path : evil) {
            assertThatThrownBy(() -> service.upsertFiles(siteId, ownerId,
                    List.of(SiteFileUpsertEntry.builder().path(path).content("x").build())))
                    .as("path: " + path)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(siteFileRepository, never()).save(any());
    }

    @Test
    @DisplayName("副檔名白名單：不支援的類型拒絕")
    void extensionWhitelistEnforced() {
        stubOwnedSite();
        for (String path : List.of("shell.sh", "app.exe", "page.php", "noextension")) {
            assertThatThrownBy(() -> service.upsertFiles(siteId, ownerId,
                    List.of(SiteFileUpsertEntry.builder().path(path).content("x").build())))
                    .as("path: " + path)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("extension");
        }
    }

    @Test
    @DisplayName("合法路徑正規化並依副檔名推斷 content type")
    void validPathsAccepted() {
        stubOwnedSite();
        when(siteFileRepository.findBySiteIdOrderByPathAsc(siteId)).thenReturn(List.of());
        when(siteFileRepository.save(any(SiteFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SiteFileMeta> result = service.upsertFiles(siteId, ownerId, List.of(
                SiteFileUpsertEntry.builder().path("index.html").content("<html></html>").build(),
                SiteFileUpsertEntry.builder().path("assets//style.css").content("body{}").build()
        ));

        assertThat(result).extracting(SiteFileMeta::path)
                .containsExactly("assets/style.css", "index.html");
        assertThat(result).filteredOn(m -> m.path().equals("index.html"))
                .first().extracting(SiteFileMeta::contentType)
                .asString().contains("text/html");
    }

    // ---------- 大小/數量限制 ----------

    @Test
    @DisplayName("單檔超過大小上限時拒絕")
    void fileSizeLimitEnforced() {
        stubOwnedSite();

        assertThatThrownBy(() -> service.upsertFiles(siteId, ownerId, List.of(
                SiteFileUpsertEntry.builder().path("big.txt").content("x".repeat(101)).build())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("超過每站檔案數上限時拒絕")
    void fileCountLimitEnforced() {
        stubOwnedSite();
        when(siteFileRepository.findBySiteIdOrderByPathAsc(siteId)).thenReturn(List.of());

        List<SiteFileUpsertEntry> tooMany = List.of(
                SiteFileUpsertEntry.builder().path("a.txt").content("1").build(),
                SiteFileUpsertEntry.builder().path("b.txt").content("2").build(),
                SiteFileUpsertEntry.builder().path("c.txt").content("3").build(),
                SiteFileUpsertEntry.builder().path("d.txt").content("4").build());

        assertThatThrownBy(() -> service.upsertFiles(siteId, ownerId, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many files");
    }

    @Test
    @DisplayName("全站總大小含既有檔案一併計算")
    void siteSizeLimitCountsExistingFiles() {
        stubOwnedSite();
        SiteFile existing = SiteFile.builder()
                .id(UUID.randomUUID()).siteId(siteId).path("old.txt")
                .data(new byte[150]).sizeBytes(150).build();
        when(siteFileRepository.findBySiteIdOrderByPathAsc(siteId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.upsertFiles(siteId, ownerId, List.of(
                SiteFileUpsertEntry.builder().path("new.txt").content("x".repeat(60)).build())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Site too large");
    }

    @Test
    @DisplayName("覆寫既有檔案時以新大小計算（不重複累計）")
    void overwriteReplacesSizeInBudget() {
        stubOwnedSite();
        SiteFile existing = SiteFile.builder()
                .id(UUID.randomUUID()).siteId(siteId).path("old.txt")
                .data(new byte[90]).sizeBytes(90).build();
        when(siteFileRepository.findBySiteIdOrderByPathAsc(siteId)).thenReturn(List.of(existing));
        when(siteFileRepository.save(any(SiteFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        // 90(舊) 被 80(新) 取代 → 總量 80 < 200，通過
        List<SiteFileMeta> result = service.upsertFiles(siteId, ownerId, List.of(
                SiteFileUpsertEntry.builder().path("old.txt").content("y".repeat(80)).build()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sizeBytes()).isEqualTo(80);
    }

    // ---------- 整批取代（zip 上傳） ----------

    @Test
    @DisplayName("replaceFiles：全數驗證通過後刪除既有檔案並寫入新檔")
    void replaceFilesDeletesThenSaves() {
        stubOwnedSite();
        when(siteFileRepository.save(any(SiteFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SiteFileMeta> result = service.replaceFiles(siteId, ownerId, List.of(
                SiteFileUpsertEntry.builder().path("index.html").content("<html></html>").build(),
                SiteFileUpsertEntry.builder().path("style.css").content("body{}").build()));

        verify(siteFileRepository).deleteBySiteId(siteId);
        assertThat(result).extracting(SiteFileMeta::path)
                .containsExactly("index.html", "style.css");
    }

    @Test
    @DisplayName("replaceFiles：任一檔案不合法時不刪除任何既有檔案")
    void replaceFilesRejectsWithoutDeleting() {
        stubOwnedSite();

        assertThatThrownBy(() -> service.replaceFiles(siteId, ownerId, List.of(
                SiteFileUpsertEntry.builder().path("index.html").content("<html></html>").build(),
                SiteFileUpsertEntry.builder().path("../evil.html").content("x").build())))
                .isInstanceOf(IllegalArgumentException.class);

        verify(siteFileRepository, never()).deleteBySiteId(any());
        verify(siteFileRepository, never()).save(any());
    }

    // ---------- 公開讀取 ----------

    @Test
    @DisplayName("未發佈網站的檔案不對外提供")
    void unpublishedSiteNotServed() {
        Site unpublished = ownedSite();
        unpublished.setPublished(false);
        lenient().when(siteRepository.findBySlug("my-site-ab12")).thenReturn(Optional.of(unpublished));

        assertThat(service.findPublicFile("my-site-ab12", "index.html")).isEmpty();
    }

    @Test
    @DisplayName("非法 slug 直接回空，不查資料庫")
    void invalidSlugShortCircuits() {
        assertThat(service.findPublicFile("Bad_Slug!", "index.html")).isEmpty();
        verify(siteRepository, never()).findBySlug(anyString());
    }
}
