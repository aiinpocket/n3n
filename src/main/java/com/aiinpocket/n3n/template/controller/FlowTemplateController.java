package com.aiinpocket.n3n.template.controller;

import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.template.dto.CreateTemplateRequest;
import com.aiinpocket.n3n.template.dto.TemplateResponse;
import com.aiinpocket.n3n.template.service.FlowTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Tag(name = "Flow Templates", description = "Flow templates")
public class FlowTemplateController {

    private final FlowTemplateService templateService;

    @GetMapping
    public ResponseEntity<Page<TemplateResponse>> listTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(templateService.searchTemplates(search, pageable));
        }
        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(templateService.listTemplatesByCategory(category, pageable));
        }
        return ResponseEntity.ok(templateService.listTemplates(pageable));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> listCategories() {
        return ResponseEntity.ok(templateService.listCategories());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TemplateResponse>> listMyTemplates(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(templateService.listMyTemplates(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(templateService.createTemplate(request, userId));
    }

    @PostMapping("/from-flow/{flowId}/version/{version}")
    public ResponseEntity<TemplateResponse> createTemplateFromFlow(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(templateService.createTemplateFromFlow(flowId, version, request, userId));
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<FlowResponse> createFlowFromTemplate(
            @PathVariable UUID id,
            @RequestParam String flowName,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(templateService.createFlowFromTemplate(id, flowName, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        templateService.deleteTemplate(id, userId);
        return ResponseEntity.noContent().build();
    }
}
