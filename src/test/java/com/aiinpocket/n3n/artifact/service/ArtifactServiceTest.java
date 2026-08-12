package com.aiinpocket.n3n.artifact.service;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.repository.ArtifactRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ArtifactServiceTest extends BaseServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ArtifactRepository artifactRepository;

    private ArtifactService artifactService;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        ArtifactStorageService storageService =
                new ArtifactStorageService(tempDir.toString(), 1); // 1 MB 上限方便測試
        artifactService = new ArtifactService(artifactRepository, storageService);
    }

    private ArtifactMeta meta(String filename, String mimeType) {
        return ArtifactMeta.builder()
                .filename(filename)
                .mimeType(mimeType)
                .flowId(UUID.randomUUID())
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .sourceNodeType("saveArtifact")
                .build();
    }

    // ===== save =====

    @Test
    void save_writesFileAndPersistsRow() {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] data = "hello artifact".getBytes(StandardCharsets.UTF_8);
        Artifact artifact = artifactService.save(ownerId, meta("report.html", "text/html"), data);

        assertThat(artifact.getId()).isNotNull();
        assertThat(artifact.getOwnerId()).isEqualTo(ownerId);
        assertThat(artifact.getFilename()).isEqualTo("report.html");
        assertThat(artifact.getMimeType()).isEqualTo("text/html");
        assertThat(artifact.getSizeBytes()).isEqualTo(data.length);
        assertThat(artifact.getStoragePath())
                .isEqualTo(ownerId + "/" + artifact.getId() + ".html");
        assertThat(tempDir.resolve(artifact.getStoragePath())).exists();
        verify(artifactRepository).save(any(Artifact.class));
    }

    @Test
    void save_pathTraversalFilename_isSanitized() {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] data = "x".getBytes(StandardCharsets.UTF_8);
        Artifact artifact = artifactService.save(
                ownerId, meta("../../../etc/passwd", "text/plain"), data);

        // 檔名去除路徑成分，儲存路徑不含 ".."
        assertThat(artifact.getFilename()).isEqualTo("passwd");
        assertThat(artifact.getStoragePath()).doesNotContain("..");
        Path stored = tempDir.resolve(artifact.getStoragePath()).normalize();
        assertThat(stored).exists();
        assertThat(stored.startsWith(tempDir)).isTrue();
    }

    @Test
    void save_windowsPathSeparators_areSanitized() {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));

        Artifact artifact = artifactService.save(
                ownerId, meta("..\\..\\evil.exe", "application/octet-stream"), new byte[]{1});

        assertThat(artifact.getFilename()).isEqualTo("evil.exe");
        assertThat(artifact.getStoragePath()).doesNotContain("..");
    }

    @Test
    void save_exceedsMaxSize_throwsException() {
        byte[] tooBig = new byte[2 * 1024 * 1024]; // 2 MB > 1 MB 上限

        assertThatThrownBy(() -> artifactService.save(ownerId, meta("big.bin", "application/octet-stream"), tooBig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max file size");
        verify(artifactRepository, never()).save(any());
    }

    @Test
    void save_nullData_throwsException() {
        assertThatThrownBy(() -> artifactService.save(ownerId, meta("a.txt", "text/plain"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void save_dbFailure_cleansUpFile() {
        when(artifactRepository.save(any(Artifact.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> artifactService.save(ownerId, meta("a.txt", "text/plain"), new byte[]{1}))
                .isInstanceOf(RuntimeException.class);

        // 目錄下不應留有孤兒檔案
        Path ownerDir = tempDir.resolve(ownerId.toString());
        assertThat(!Files.exists(ownerDir) || isEmptyDir(ownerDir)).isTrue();
    }

    private static boolean isEmptyDir(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ===== list =====

    @Test
    void list_withoutFilter_delegatesToOwnerQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Artifact> page = new PageImpl<>(List.of());
        when(artifactRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable)).thenReturn(page);

        Page<Artifact> result = artifactService.list(ownerId, null, pageable);

        assertThat(result).isSameAs(page);
        verify(artifactRepository).findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
        verify(artifactRepository, never())
                .findByOwnerIdAndMimeTypeStartingWithOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void list_withMimeTypePrefix_delegatesToFilteredQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Artifact> page = new PageImpl<>(List.of());
        when(artifactRepository.findByOwnerIdAndMimeTypeStartingWithOrderByCreatedAtDesc(
                ownerId, "video/", pageable)).thenReturn(page);

        Page<Artifact> result = artifactService.list(ownerId, "video/", pageable);

        assertThat(result).isSameAs(page);
        verify(artifactRepository)
                .findByOwnerIdAndMimeTypeStartingWithOrderByCreatedAtDesc(ownerId, "video/", pageable);
    }

    // ===== getOwned / openForDownload =====

    @Test
    void getOwned_otherUser_throwsNotFound() {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));
        Artifact artifact = artifactService.save(ownerId, meta("a.txt", "text/plain"), new byte[]{1});

        when(artifactRepository.findByIdAndOwnerId(artifact.getId(), otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> artifactService.getOwned(artifact.getId(), otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Artifact not found");
    }

    @Test
    void openForDownload_owner_returnsReadableResource() throws Exception {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));
        byte[] data = "download me".getBytes(StandardCharsets.UTF_8);
        Artifact artifact = artifactService.save(ownerId, meta("dl.txt", "text/plain"), data);

        when(artifactRepository.findByIdAndOwnerId(artifact.getId(), ownerId))
                .thenReturn(Optional.of(artifact));

        Resource resource = artifactService.openForDownload(artifact.getId(), ownerId);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getInputStream().readAllBytes()).isEqualTo(data);
    }

    @Test
    void openForDownload_otherUser_throwsNotFound() {
        UUID artifactId = UUID.randomUUID();
        when(artifactRepository.findByIdAndOwnerId(artifactId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> artifactService.openForDownload(artifactId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void openForDownload_fileMissing_throwsNotFound() {
        Artifact artifact = Artifact.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .filename("gone.txt")
                .mimeType("text/plain")
                .sizeBytes(1)
                .storagePath(ownerId + "/missing.txt")
                .build();
        when(artifactRepository.findByIdAndOwnerId(artifact.getId(), ownerId))
                .thenReturn(Optional.of(artifact));

        assertThatThrownBy(() -> artifactService.openForDownload(artifact.getId(), ownerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ===== delete =====

    @Test
    void delete_owner_removesRowAndFile() {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));
        Artifact artifact = artifactService.save(ownerId, meta("del.txt", "text/plain"), new byte[]{1});
        Path stored = tempDir.resolve(artifact.getStoragePath());
        assertThat(stored).exists();

        when(artifactRepository.findByIdAndOwnerId(artifact.getId(), ownerId))
                .thenReturn(Optional.of(artifact));

        artifactService.delete(artifact.getId(), ownerId);

        verify(artifactRepository).delete(artifact);
        assertThat(stored).doesNotExist();
    }

    @Test
    void delete_otherUser_throwsNotFoundAndKeepsFile() {
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> inv.getArgument(0));
        Artifact artifact = artifactService.save(ownerId, meta("keep.txt", "text/plain"), new byte[]{1});

        when(artifactRepository.findByIdAndOwnerId(artifact.getId(), otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> artifactService.delete(artifact.getId(), otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(artifactRepository, never()).delete(any(Artifact.class));
        assertThat(tempDir.resolve(artifact.getStoragePath())).exists();
    }

    // ===== totalSizeBytes =====

    @Test
    void totalSizeBytes_delegatesToRepository() {
        when(artifactRepository.sumSizeBytesByOwnerId(ownerId)).thenReturn(12345L);

        assertThat(artifactService.totalSizeBytes(ownerId)).isEqualTo(12345L);
        verify(artifactRepository).sumSizeBytesByOwnerId(eq(ownerId));
    }

    // ===== downloadUrl =====

    @Test
    void downloadUrl_isRelativePath() {
        UUID id = UUID.randomUUID();
        assertThat(ArtifactService.downloadUrl(id)).isEqualTo("/api/artifacts/" + id + "/download");
    }
}
