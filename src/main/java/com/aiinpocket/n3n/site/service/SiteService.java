package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.site.dto.SiteFileMeta;
import com.aiinpocket.n3n.site.dto.SiteFileUpsertEntry;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.repository.SiteFileRepository;
import com.aiinpocket.n3n.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * AI Site Builder 核心服務：網站 CRUD 與檔案 upsert。
 * 所有讀寫皆以 ownerId 隔離；檔案路徑經 SitePaths 淨化，
 * 數量與大小限制由 n3n.sites.* 設定控制。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private static final int MAX_SLUG_ATTEMPTS = 5;

    private final SiteRepository siteRepository;
    private final SiteFileRepository siteFileRepository;

    @Value("${n3n.sites.max-files:200}")
    private int maxFilesPerSite;

    @Value("${n3n.sites.max-file-bytes:5242880}")
    private long maxFileBytes;

    @Value("${n3n.sites.max-site-bytes:20971520}")
    private long maxSiteBytes;

    @Value("${n3n.sites.max-sites-per-user:50}")
    private int maxSitesPerUser;

    // ---------- Site CRUD ----------

    @Transactional
    public Site create(UUID ownerId, String name, String description) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Site ownerId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Site name is required");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("Site name too long (max 200 chars)");
        }
        if (siteRepository.countByOwnerId(ownerId) >= maxSitesPerUser) {
            throw new IllegalArgumentException("Site limit reached (max " + maxSitesPerUser + " sites)");
        }

        String slug = generateUniqueSlug(name);
        Site site = Site.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .slug(slug)
                .name(name.trim())
                .description(description)
                .isPublished(true)
                .build();
        site = siteRepository.save(site);
        log.info("Site created: id={}, slug={}, owner={}", site.getId(), slug, ownerId);
        return site;
    }

    @Transactional(readOnly = true)
    public List<Site> list(UUID ownerId) {
        return siteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    /**
     * 取得使用者擁有的網站；非擁有者一律回 404（不洩漏存在性）。
     */
    @Transactional(readOnly = true)
    public Site getOwned(UUID id, UUID ownerId) {
        return siteRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + id));
    }

    @Transactional
    public Site update(UUID id, UUID ownerId, String name, String description, Boolean isPublished) {
        Site site = getOwned(id, ownerId);
        if (name != null && !name.isBlank()) {
            if (name.length() > 200) {
                throw new IllegalArgumentException("Site name too long (max 200 chars)");
            }
            site.setName(name.trim());
        }
        if (description != null) {
            site.setDescription(description);
        }
        if (isPublished != null) {
            site.setPublished(isPublished);
        }
        return siteRepository.save(site);
    }

    @Transactional
    public void delete(UUID id, UUID ownerId) {
        Site site = getOwned(id, ownerId);
        siteFileRepository.deleteBySiteId(site.getId());
        siteRepository.delete(site);
        log.info("Site deleted: id={}, slug={}, owner={}", id, site.getSlug(), ownerId);
    }

    // ---------- Files ----------

    @Transactional(readOnly = true)
    public List<SiteFileMeta> listFiles(UUID siteId, UUID ownerId) {
        Site site = getOwned(siteId, ownerId);
        return siteFileRepository.findBySiteIdOrderByPathAsc(site.getId())
                .stream().map(SiteFileMeta::from).toList();
    }

    @Transactional(readOnly = true)
    public SiteFile getFile(UUID siteId, UUID ownerId, String path) {
        Site site = getOwned(siteId, ownerId);
        String sanitized = SitePaths.sanitize(path);
        return siteFileRepository.findBySiteIdAndPath(site.getId(), sanitized)
                .orElseThrow(() -> new ResourceNotFoundException("Site file not found: " + sanitized));
    }

    /**
     * 批次 upsert 檔案（同 path 覆寫、新 path 建立），含擁有者檢查與
     * 路徑淨化、副檔名白名單、單檔/全站大小與檔案數限制。
     */
    @Transactional
    public List<SiteFileMeta> upsertFiles(UUID siteId, UUID ownerId, List<SiteFileUpsertEntry> entries) {
        Site site = getOwned(siteId, ownerId);
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        // 先淨化與解碼全部項目（同一批內同 path 後者覆蓋前者）
        Map<String, PreparedFile> prepared = new LinkedHashMap<>();
        for (SiteFileUpsertEntry entry : entries) {
            PreparedFile file = prepare(entry);
            prepared.put(file.path(), file);
        }

        Map<String, SiteFile> existing = new HashMap<>();
        for (SiteFile file : siteFileRepository.findBySiteIdOrderByPathAsc(site.getId())) {
            existing.put(file.getPath(), file);
        }

        enforceLimits(prepared, existing);

        for (PreparedFile file : prepared.values()) {
            SiteFile target = existing.get(file.path());
            if (target == null) {
                target = SiteFile.builder()
                        .id(UUID.randomUUID())
                        .siteId(site.getId())
                        .path(file.path())
                        .build();
            }
            target.setContentType(file.contentType());
            target.setData(file.data());
            target.setSizeBytes(file.data().length);
            existing.put(file.path(), siteFileRepository.save(target));
        }

        // 觸碰網站 updatedAt
        siteRepository.save(site);

        return existing.values().stream()
                .sorted((a, b) -> a.getPath().compareTo(b.getPath()))
                .map(SiteFileMeta::from)
                .toList();
    }

    /**
     * 整批取代站台檔案（zip 上傳用）：先驗證全部項目，全數合法後才
     * 刪除既有檔案並寫入新檔——同一交易內完成，失敗即回滾。
     */
    @Transactional
    public List<SiteFileMeta> replaceFiles(UUID siteId, UUID ownerId, List<SiteFileUpsertEntry> entries) {
        Site site = getOwned(siteId, ownerId);
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        Map<String, PreparedFile> prepared = new LinkedHashMap<>();
        for (SiteFileUpsertEntry entry : entries) {
            PreparedFile file = prepare(entry);
            prepared.put(file.path(), file);
        }
        enforceLimits(prepared, Map.of());

        siteFileRepository.deleteBySiteId(site.getId());
        List<SiteFileMeta> result = prepared.values().stream()
                .map(file -> siteFileRepository.save(SiteFile.builder()
                        .id(UUID.randomUUID())
                        .siteId(site.getId())
                        .path(file.path())
                        .contentType(file.contentType())
                        .data(file.data())
                        .sizeBytes(file.data().length)
                        .build()))
                .sorted((a, b) -> a.getPath().compareTo(b.getPath()))
                .map(SiteFileMeta::from)
                .toList();

        // 觸碰網站 updatedAt
        siteRepository.save(site);
        log.info("Site files replaced: site={}, files={}", siteId, result.size());
        return result;
    }

    @Transactional
    public void deleteFile(UUID siteId, UUID ownerId, String path) {
        Site site = getOwned(siteId, ownerId);
        String sanitized = SitePaths.sanitize(path);
        int deleted = siteFileRepository.deleteBySiteIdAndPath(site.getId(), sanitized);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Site file not found: " + sanitized);
        }
    }

    // ---------- Public serving ----------

    /**
     * 公開讀取：僅回傳已發佈網站的檔案。找不到時回空（由 controller 決定 fallback）。
     */
    @Transactional(readOnly = true)
    public Optional<SiteFile> findPublicFile(String slug, String path) {
        if (slug == null || !SiteSlugs.SLUG_PATTERN.matcher(slug).matches()) {
            return Optional.empty();
        }
        return siteRepository.findBySlug(slug)
                .filter(Site::isPublished)
                .flatMap(site -> siteFileRepository.findBySiteIdAndPath(site.getId(), path));
    }

    /**
     * 公開讀取（Host 路由用）：站台已由 HostSiteFilter 解析，僅檢查發佈狀態。
     */
    @Transactional(readOnly = true)
    public Optional<SiteFile> findPublicFile(Site site, String path) {
        if (site == null || !site.isPublished()) {
            return Optional.empty();
        }
        return siteFileRepository.findBySiteIdAndPath(site.getId(), path);
    }

    @Transactional(readOnly = true)
    public long fileCount(UUID siteId) {
        return siteFileRepository.countBySiteId(siteId);
    }

    @Transactional(readOnly = true)
    public long totalSizeBytes(UUID siteId) {
        return siteFileRepository.sumSizeBytesBySiteId(siteId);
    }

    // ---------- Internals ----------

    private record PreparedFile(String path, String contentType, byte[] data) {
    }

    private PreparedFile prepare(SiteFileUpsertEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("File entry is required");
        }
        String path = SitePaths.sanitize(entry.getPath());
        byte[] data = decodeContent(entry, path);
        if (data.length > maxFileBytes) {
            throw new IllegalArgumentException(
                    "File too large: " + path + " (" + data.length + " bytes, max " + maxFileBytes + ")");
        }
        String contentType = entry.getContentType() != null && !entry.getContentType().isBlank()
                ? validateContentType(entry.getContentType(), path)
                : SitePaths.inferContentType(path);
        return new PreparedFile(path, contentType, data);
    }

    private byte[] decodeContent(SiteFileUpsertEntry entry, String path) {
        if (entry.getContentBase64() != null && !entry.getContentBase64().isBlank()) {
            try {
                return Base64.getDecoder().decode(entry.getContentBase64());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 content for file: " + path);
            }
        }
        if (entry.getContent() != null) {
            return entry.getContent().getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("File content is required: " + path);
    }

    /**
     * 呼叫者指定 content type 時，仍以副檔名白名單為準（防止 text/html 偽裝），
     * 只允許與推斷值主類型一致的自訂值，否則採用推斷值。
     */
    private String validateContentType(String requested, String path) {
        String inferred = SitePaths.inferContentType(path);
        String requestedMajor = requested.split("/")[0].trim().toLowerCase();
        String inferredMajor = inferred.split("/")[0];
        if (requested.length() <= 100 && requestedMajor.equals(inferredMajor)) {
            return requested.trim();
        }
        return inferred;
    }

    private void enforceLimits(Map<String, PreparedFile> prepared, Map<String, SiteFile> existing) {
        long newTotal = 0;
        int newCount = 0;
        for (SiteFile file : existing.values()) {
            if (!prepared.containsKey(file.getPath())) {
                newTotal += file.getSizeBytes();
                newCount++;
            }
        }
        for (PreparedFile file : prepared.values()) {
            newTotal += file.data().length;
            newCount++;
        }
        if (newCount > maxFilesPerSite) {
            throw new IllegalArgumentException(
                    "Too many files: " + newCount + " (max " + maxFilesPerSite + " per site)");
        }
        if (newTotal > maxSiteBytes) {
            throw new IllegalArgumentException(
                    "Site too large: " + newTotal + " bytes (max " + maxSiteBytes + ")");
        }
    }

    private String generateUniqueSlug(String name) {
        for (int i = 0; i < MAX_SLUG_ATTEMPTS; i++) {
            String candidate = SiteSlugs.generate(name);
            try {
                SiteSlugs.validate(candidate);
            } catch (IllegalArgumentException e) {
                continue; // 極少見（隨機尾碼撞到保留字不可能，但 base 過短等情況重試）
            }
            if (!siteRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique site slug, please retry");
    }
}
