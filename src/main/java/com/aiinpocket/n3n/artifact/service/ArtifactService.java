package com.aiinpocket.n3n.artifact.service;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.repository.ArtifactRepository;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 使用者 artifact（節點產出檔案）服務：儲存、列表、下載、刪除。
 * 所有讀取/刪除操作皆以 ownerId 隔離，避免跨使用者存取。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final ArtifactStorageService storageService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    /**
     * 儲存 byte[] 內容為 artifact。
     */
    public Artifact save(UUID ownerId, ArtifactMeta meta, byte[] data) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Artifact ownerId is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("Artifact data is required");
        }

        UUID artifactId = UUID.randomUUID();
        String filename = ArtifactStorageService.sanitizeFilename(meta.getFilename());
        String storagePath = storageService.buildStoragePath(ownerId, artifactId, filename);

        try {
            long size = storageService.store(storagePath, data);
            return persist(artifactId, ownerId, meta, filename, storagePath, size, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store artifact file", e);
        }
    }

    /**
     * 從遠端 URL 串流下載並存為 artifact（用於 fal.ai 等會過期的暫時 URL）。
     * MIME type 優先採用遠端回應的 Content-Type，其次為 meta 指定值。
     */
    public Artifact saveFromUrl(UUID ownerId, ArtifactMeta meta, String url) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Artifact ownerId is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Artifact source URL is required");
        }

        UUID artifactId = UUID.randomUUID();
        String filename = ArtifactStorageService.sanitizeFilename(meta.getFilename());
        String storagePath = storageService.buildStoragePath(ownerId, artifactId, filename);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Artifact source fetch failed (HTTP " + response.code() + ")");
            }
            String contentType = response.header("Content-Type");
            long size = storageService.store(storagePath, response.body().byteStream());
            return persist(artifactId, ownerId, meta, filename, storagePath, size, contentType);
        } catch (IOException e) {
            storageService.delete(storagePath);
            throw new UncheckedIOException("Failed to save artifact from URL", e);
        }
    }

    private Artifact persist(UUID artifactId, UUID ownerId, ArtifactMeta meta,
                             String filename, String storagePath, long size, String contentTypeOverride) {
        String mimeType = contentTypeOverride != null && !contentTypeOverride.isBlank()
                ? contentTypeOverride
                : meta.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }

        Artifact artifact = Artifact.builder()
                .id(artifactId)
                .ownerId(ownerId)
                .flowId(meta.getFlowId())
                .executionId(meta.getExecutionId())
                .nodeId(meta.getNodeId())
                .sourceNodeType(meta.getSourceNodeType())
                .filename(filename)
                .mimeType(mimeType)
                .sizeBytes(size)
                .storagePath(storagePath)
                .build();

        try {
            return artifactRepository.save(artifact);
        } catch (RuntimeException e) {
            // DB 寫入失敗時清除孤兒檔案
            storageService.delete(storagePath);
            throw e;
        }
    }

    /**
     * 列出使用者的 artifacts（新到舊），可依 MIME type 前綴過濾（如 "video/"）。
     */
    @Transactional(readOnly = true)
    public Page<Artifact> list(UUID ownerId, String mimeTypePrefix, Pageable pageable) {
        if (mimeTypePrefix != null && !mimeTypePrefix.isBlank()) {
            return artifactRepository.findByOwnerIdAndMimeTypeStartingWithOrderByCreatedAtDesc(
                    ownerId, mimeTypePrefix, pageable);
        }
        return artifactRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
    }

    /**
     * 取得使用者擁有的 artifact；非擁有者一律回 404（不洩漏存在性）。
     */
    @Transactional(readOnly = true)
    public Artifact getOwned(UUID id, UUID ownerId) {
        return artifactRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Artifact not found: " + id));
    }

    /**
     * 把 artifact 轉為「AI 助手對話產物」：脫離臨時 execution（一次性生成走
     * NodeProbeService，probeId 沒有對應的 execution row，掛著會被孤兒清理回收），
     * executionId 設為 null 後即永久保存於作品庫。
     */
    @Transactional
    public Artifact claimForAssistant(UUID ownerId, UUID artifactId) {
        Artifact artifact = getOwned(artifactId, ownerId);
        artifact.setExecutionId(null);
        artifact.setNodeId(null);
        artifact.setSourceNodeType("aiAssistant");
        return artifactRepository.save(artifact);
    }

    /**
     * 開啟檔案供下載，含擁有者檢查。
     */
    @Transactional(readOnly = true)
    public Resource openForDownload(UUID id, UUID ownerId) {
        Artifact artifact = getOwned(id, ownerId);
        return openResource(artifact);
    }

    /**
     * 以已載入的 entity 開啟檔案（避免重複查詢）。
     */
    public Resource openResource(Artifact artifact) {
        Path path = storageService.resolve(artifact.getStoragePath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Artifact file is missing: " + artifact.getId());
        }
        return new FileSystemResource(path);
    }

    /**
     * 刪除 artifact（DB row + 檔案），含擁有者檢查。
     */
    @Transactional
    public void delete(UUID id, UUID ownerId) {
        Artifact artifact = getOwned(id, ownerId);
        artifactRepository.delete(artifact);
        storageService.delete(artifact.getStoragePath());
    }

    /**
     * 清理某次執行的 artifacts（保留策略用）：pinned 的保留，其餘刪 DB row + 檔案。
     * @return 刪除的檔案數
     */
    @Transactional
    public int deleteUnpinnedByExecution(UUID executionId) {
        int deleted = 0;
        for (Artifact artifact : artifactRepository.findByExecutionId(executionId)) {
            if (Boolean.TRUE.equals(artifact.getPinned())) {
                continue;
            }
            artifactRepository.delete(artifact);
            storageService.delete(artifact.getStoragePath());
            deleted++;
        }
        return deleted;
    }

    /**
     * 將某次執行的所有 artifacts 標記為永久/取消永久（跟隨執行的 pin 狀態）。
     */
    @Transactional
    public void setPinnedByExecution(UUID executionId, boolean pinned) {
        for (Artifact artifact : artifactRepository.findByExecutionId(executionId)) {
            artifact.setPinned(pinned);
            artifactRepository.save(artifact);
        }
    }

    /**
     * 清理孤兒 artifacts（掛在已刪除的 execution 或試打 probeId 下、逾期未 pinned）。
     * @return 刪除數量
     */
    @Transactional
    public int cleanupOrphaned(java.time.Instant cutoff, int limit) {
        int deleted = 0;
        for (Artifact artifact : artifactRepository.findOrphanedByMissingExecution(cutoff, limit)) {
            artifactRepository.delete(artifact);
            storageService.delete(artifact.getStoragePath());
            deleted++;
        }
        return deleted;
    }

    /**
     * 使用者所有 artifacts 的總大小（bytes），用於用量統計。
     */
    @Transactional(readOnly = true)
    public long totalSizeBytes(UUID ownerId) {
        return artifactRepository.sumSizeBytesByOwnerId(ownerId);
    }

    /**
     * 產生相對下載路徑（供節點輸出使用）。
     */
    public static String downloadUrl(UUID artifactId) {
        return "/api/artifacts/" + artifactId + "/download";
    }
}
