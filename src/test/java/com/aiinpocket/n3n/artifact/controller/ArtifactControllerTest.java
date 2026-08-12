package com.aiinpocket.n3n.artifact.controller;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private ArtifactService artifactService;

    @InjectMocks
    private ArtifactController artifactController;

    private UserDetails testUser(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private Artifact sampleArtifact(UUID ownerId) {
        return Artifact.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .filename("影片檔.mp4")
                .mimeType("video/mp4")
                .sizeBytes(1024)
                .sourceNodeType("falAi")
                .storagePath(ownerId + "/x.mp4")
                .createdAt(Instant.now())
                .build();
    }

    // ===== listArtifacts (GET /api/artifacts) =====

    @Test
    void listArtifacts_returnsItemsWithTotals() {
        UUID userId = UUID.randomUUID();
        Artifact artifact = sampleArtifact(userId);
        Page<Artifact> page = new PageImpl<>(List.of(artifact));
        when(artifactService.list(eq(userId), isNull(), any(Pageable.class))).thenReturn(page);
        when(artifactService.totalSizeBytes(userId)).thenReturn(1024L);

        var result = artifactController.listArtifacts(null, Pageable.unpaged(), testUser(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotal()).isEqualTo(1);
        assertThat(result.getBody().getTotalSizeBytes()).isEqualTo(1024L);
        assertThat(result.getBody().getItems()).hasSize(1);
        assertThat(result.getBody().getItems().get(0).getFilename()).isEqualTo("影片檔.mp4");
        assertThat(result.getBody().getItems().get(0).getSourceNodeType()).isEqualTo("falAi");
    }

    @Test
    void listArtifacts_typeFilter_convertsToMimePrefix() {
        UUID userId = UUID.randomUUID();
        when(artifactService.list(eq(userId), eq("video/"), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(artifactService.totalSizeBytes(userId)).thenReturn(0L);

        var result = artifactController.listArtifacts("video", Pageable.unpaged(), testUser(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(artifactService).list(eq(userId), eq("video/"), any(Pageable.class));
    }

    @Test
    void listArtifacts_fullMimePrefix_isPassedThrough() {
        UUID userId = UUID.randomUUID();
        when(artifactService.list(eq(userId), eq("application/json"), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(artifactService.totalSizeBytes(userId)).thenReturn(0L);

        artifactController.listArtifacts("application/json", Pageable.unpaged(), testUser(userId));

        verify(artifactService).list(eq(userId), eq("application/json"), any(Pageable.class));
    }

    // ===== downloadArtifact (GET /api/artifacts/{id}/download) =====

    @Test
    void downloadArtifact_owner_streamsAttachment() throws Exception {
        UUID userId = UUID.randomUUID();
        Artifact artifact = sampleArtifact(userId);
        Path file = Files.writeString(tempDir.resolve("x.mp4"), "video-bytes");
        when(artifactService.getOwned(artifact.getId(), userId)).thenReturn(artifact);
        when(artifactService.openResource(artifact)).thenReturn(new FileSystemResource(file));

        var result = artifactController.downloadArtifact(artifact.getId(), testUser(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("video/mp4"));
        String disposition = result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment");
        // 非 ASCII 檔名以 RFC 5987 filename* 編碼
        assertThat(disposition).contains("filename*=UTF-8''");
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void downloadArtifact_otherUser_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        when(artifactService.getOwned(artifactId, userId))
                .thenThrow(new ResourceNotFoundException("Artifact not found: " + artifactId));

        assertThatThrownBy(() -> artifactController.downloadArtifact(artifactId, testUser(userId)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(artifactService, never()).openResource(any());
    }

    // ===== rawArtifact (GET /api/artifacts/{id}/raw) =====

    @Test
    void rawArtifact_owner_streamsInline() throws Exception {
        UUID userId = UUID.randomUUID();
        Artifact artifact = sampleArtifact(userId);
        Path file = Files.writeString(tempDir.resolve("x.mp4"), "video-bytes");
        when(artifactService.getOwned(artifact.getId(), userId)).thenReturn(artifact);
        when(artifactService.openResource(artifact)).thenReturn(new FileSystemResource(file));

        var result = artifactController.rawArtifact(artifact.getId(), testUser(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        String disposition = result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("inline");
    }

    // ===== deleteArtifact (DELETE /api/artifacts/{id}) =====

    @Test
    void deleteArtifact_owner_returnsNoContent() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        doNothing().when(artifactService).delete(artifactId, userId);

        var result = artifactController.deleteArtifact(artifactId, testUser(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(artifactService).delete(artifactId, userId);
    }

    @Test
    void deleteArtifact_otherUser_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Artifact not found: " + artifactId))
                .when(artifactService).delete(artifactId, userId);

        assertThatThrownBy(() -> artifactController.deleteArtifact(artifactId, testUser(userId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== userId scoping =====

    @Test
    void endpoints_extractUserIdFromUserDetails() {
        UUID userId = UUID.randomUUID();
        when(artifactService.list(eq(userId), isNull(), any(Pageable.class))).thenReturn(Page.empty());
        when(artifactService.totalSizeBytes(userId)).thenReturn(0L);

        artifactController.listArtifacts(null, Pageable.unpaged(), testUser(userId));

        verify(artifactService).list(eq(userId), isNull(), any(Pageable.class));
        verify(artifactService).totalSizeBytes(eq(userId));
    }
}
