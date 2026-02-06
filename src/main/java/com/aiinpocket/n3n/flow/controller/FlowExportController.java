package com.aiinpocket.n3n.flow.controller;

import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportPreviewResponse;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportRequest;
import com.aiinpocket.n3n.flow.service.FlowExportService;
import com.aiinpocket.n3n.flow.service.FlowImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Flow 匯出/匯入 API Controller
 */
@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
@Tag(name = "Flow Import/Export", description = "Flow import and export operations")
public class FlowExportController {

    private final FlowExportService exportService;
    private final FlowImportService importService;

    /**
     * 匯出流程
     */
    @GetMapping("/{flowId}/versions/{version}/export")
    public ResponseEntity<FlowExportPackage> exportFlow(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowExportPackage pkg = exportService.exportFlow(flowId, version, userId);

        // 設定下載檔名
        String filename = pkg.getFlow().getName() + "_" + version + ".json";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFilename + "\"")
                .body(pkg);
    }

    /**
     * 預覽匯入
     */
    @PostMapping("/import/preview")
    public ResponseEntity<FlowImportPreviewResponse> previewImport(
            @RequestBody FlowExportPackage pkg,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowImportPreviewResponse preview = importService.previewImport(pkg, userId);
        return ResponseEntity.ok(preview);
    }

    /**
     * 執行匯入
     */
    @PostMapping("/import")
    public ResponseEntity<FlowResponse> importFlow(
            @Valid @RequestBody FlowImportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowResponse response = importService.importFlow(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
