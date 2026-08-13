package com.aiinpocket.n3n.site.controller;

import com.aiinpocket.n3n.site.dto.*;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.service.SiteDomainService;
import com.aiinpocket.n3n.site.service.SiteDomains;
import com.aiinpocket.n3n.site.service.SiteService;
import com.aiinpocket.n3n.site.service.SiteZipService;
import com.aiinpocket.n3n.site.service.VercelDeployService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Site Builder 管理 API（皆需登入、僅能操作自己的網站）。
 * 公開瀏覽走 PublicSiteController 的 /sites/{slug}/**。
 */
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Tag(name = "Sites", description = "AI Site Builder - hosted static sites")
public class SiteController {

    private final SiteService siteService;
    private final VercelDeployService vercelDeployService;
    private final SiteDomains siteDomains;
    private final SiteDomainService siteDomainService;
    private final SiteZipService siteZipService;

    @GetMapping
    public ResponseEntity<List<SiteResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userId(userDetails);
        List<SiteResponse> sites = siteService.list(userId).stream()
                .map(site -> toResponse(site,
                        siteService.fileCount(site.getId()),
                        siteService.totalSizeBytes(site.getId())))
                .toList();
        return ResponseEntity.ok(sites);
    }

    @PostMapping
    public ResponseEntity<SiteResponse> create(
            @Valid @RequestBody SiteCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userId(userDetails);
        try {
            Site site = siteService.create(userId, request.getName(), request.getDescription());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(site, 0, 0));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiteDetailResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userId(userDetails);
        Site site = siteService.getOwned(id, userId);
        List<SiteFileMeta> files = siteService.listFiles(id, userId);
        long totalSize = files.stream().mapToLong(SiteFileMeta::sizeBytes).sum();
        return ResponseEntity.ok(SiteDetailResponse.builder()
                .site(toResponse(site, files.size(), totalSize))
                .files(files)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SiteResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SiteUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userId(userDetails);
        try {
            Site site = siteService.update(id, userId,
                    request.getName(), request.getDescription(), request.getIsPublished());
            return ResponseEntity.ok(toResponse(site,
                    siteService.fileCount(id), siteService.totalSizeBytes(id)));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        siteService.delete(id, userId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/files")
    public ResponseEntity<List<SiteFileMeta>> upsertFiles(
            @PathVariable UUID id,
            @Valid @RequestBody SiteFilesUpsertRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(
                    siteService.upsertFiles(id, userId(userDetails), request.getFiles()));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        }
    }

    @DeleteMapping("/{id}/files")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID id,
            @RequestParam String path,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            siteService.deleteFile(id, userId(userDetails), path);
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 取得單一檔案內容（UTF-8 文字），供前端快速編輯。
     */
    @GetMapping("/{id}/files/content")
    public ResponseEntity<Map<String, Object>> fileContent(
            @PathVariable UUID id,
            @RequestParam String path,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            SiteFile file = siteService.getFile(id, userId(userDetails), path);
            return ResponseEntity.ok(Map.of(
                    "path", file.getPath(),
                    "contentType", file.getContentType() == null ? "" : file.getContentType(),
                    "sizeBytes", file.getSizeBytes(),
                    "content", new String(file.getData(), StandardCharsets.UTF_8)
            ));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        }
    }

    /**
     * 實驗性：以使用者自己的 API token 部署到外部平台（v1 僅 vercel）。
     */
    @PostMapping("/{id}/deploy")
    public ResponseEntity<SiteDeployResponse> deploy(
            @PathVariable UUID id,
            @Valid @RequestBody SiteDeployRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (!"vercel".equalsIgnoreCase(request.getProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported deploy provider: " + request.getProvider() + " (only \"vercel\" in v1)");
        }
        try {
            return ResponseEntity.ok(vercelDeployService.deployToVercel(
                    id, userId(userDetails), request.getCredentialId()));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /**
     * 上傳整包 zip：全部驗證通過後一次取代站台檔案（zip-slip / zip bomb 防禦見 SiteZipService）。
     */
    @PostMapping("/{id}/upload")
    public ResponseEntity<List<SiteFileMeta>> uploadZip(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A .zip file is required");
        }
        try (InputStream input = file.getInputStream()) {
            List<SiteFileUpsertEntry> entries = siteZipService.parse(input);
            return ResponseEntity.ok(siteService.replaceFiles(id, userId(userDetails), entries));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid zip file");
        }
    }

    // ---------- 自訂網域 ----------

    @GetMapping("/{id}/custom-domain")
    public ResponseEntity<SiteCustomDomainResponse> getCustomDomain(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Site site = siteService.getOwned(id, userId(userDetails));
        return ResponseEntity.ok(customDomainResponse(site));
    }

    @PutMapping("/{id}/custom-domain")
    public ResponseEntity<SiteCustomDomainResponse> setCustomDomain(
            @PathVariable UUID id,
            @Valid @RequestBody SiteCustomDomainRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Site site = siteDomainService.setCustomDomain(id, userId(userDetails), request.getDomain());
            return ResponseEntity.ok(customDomainResponse(site));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/custom-domain/verify")
    public ResponseEntity<SiteCustomDomainResponse> verifyCustomDomain(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Site site = siteDomainService.verifyCustomDomain(id, userId(userDetails));
            return ResponseEntity.ok(customDomainResponse(site));
        } catch (IllegalArgumentException e) {
            throw badRequest(e);
        } catch (IllegalStateException e) {
            // DNS 基礎設施錯誤（非「記錄不存在」）
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @DeleteMapping("/{id}/custom-domain")
    public ResponseEntity<Void> removeCustomDomain(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        siteDomainService.removeCustomDomain(id, userId(userDetails));
        return ResponseEntity.noContent().build();
    }

    private SiteCustomDomainResponse customDomainResponse(Site site) {
        return SiteCustomDomainResponse.builder()
                .domain(site.getCustomDomain())
                .verified(site.isCustomDomainVerified())
                .records(siteDomainService.dnsRecords(site))
                .build();
    }

    private SiteResponse toResponse(Site site, long fileCount, long totalSizeBytes) {
        return SiteResponse.from(site, fileCount, totalSizeBytes,
                siteDomains.publicUrl(site.getSlug()));
    }

    private static UUID userId(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }

    private static ResponseStatusException badRequest(IllegalArgumentException e) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
