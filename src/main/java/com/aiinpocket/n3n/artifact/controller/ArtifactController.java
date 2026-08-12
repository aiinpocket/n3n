package com.aiinpocket.n3n.artifact.controller;

import com.aiinpocket.n3n.artifact.dto.ArtifactListResponse;
import com.aiinpocket.n3n.artifact.dto.ArtifactResponse;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 使用者 artifact（節點產出檔案）REST API：列表、下載、預覽、刪除。
 */
@RestController
@RequestMapping("/api/artifacts")
@RequiredArgsConstructor
@Tag(name = "Artifacts", description = "Generated file (artifact) library")
public class ArtifactController {

    private final ArtifactService artifactService;

    /**
     * 分頁列出使用者的 artifacts（新到舊）。
     * type 為 MIME type 前綴過濾，例如 video / audio / image / text。
     */
    @GetMapping
    public ResponseEntity<ArtifactListResponse> listArtifacts(
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        Page<Artifact> page = artifactService.list(userId, toMimeTypePrefix(type), pageable);
        ArtifactListResponse response = ArtifactListResponse.builder()
                .items(page.getContent().stream().map(ArtifactResponse::from).toList())
                .total(page.getTotalElements())
                .totalSizeBytes(artifactService.totalSizeBytes(userId))
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * 下載檔案（Content-Disposition: attachment，檔名以 RFC 5987 編碼支援非 ASCII）。
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return streamArtifact(id, userDetails, true);
    }

    /**
     * 瀏覽器內預覽（Content-Disposition: inline），適用於圖片/音訊/影片。
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<Resource> rawArtifact(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return streamArtifact(id, userDetails, false);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArtifact(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        artifactService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> streamArtifact(UUID id, UserDetails userDetails, boolean attachment) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Artifact artifact = artifactService.getOwned(id, userId);
        Resource resource = artifactService.openResource(artifact);

        ContentDisposition disposition = (attachment
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(artifact.getFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(parseMediaType(artifact.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(artifact.getSizeBytes())
                .body(resource);
    }

    private static MediaType parseMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 將 type 參數轉為 MIME type 前綴："video" → "video/"；已含 "/" 者原樣使用。
     */
    private static String toMimeTypePrefix(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.contains("/") ? type : type + "/";
    }
}
