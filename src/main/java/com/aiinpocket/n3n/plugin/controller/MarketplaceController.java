package com.aiinpocket.n3n.plugin.controller;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.plugin.dto.*;
import com.aiinpocket.n3n.plugin.service.PluginService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@Tag(name = "Custom Tools", description = "Custom Docker tools - pull additional container tools from Docker Hub")
public class MarketplaceController {

    private final PluginService pluginService;

    public MarketplaceController(@Qualifier("pluginPluginService") PluginService pluginService) {
        this.pluginService = pluginService;
    }

    /**
     * Get all plugin categories.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<PluginCategoryDto>> getCategories() {
        return ResponseEntity.ok(pluginService.getCategories());
    }

    /**
     * Search plugins with filters.
     */
    @GetMapping("/plugins")
    public ResponseEntity<PluginSearchResult> searchPlugins(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 100) String category,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 50) String pricing,
            @RequestParam(required = false, name = "q") @jakarta.validation.constraints.Size(max = 500) String query,
            @RequestParam(required = false, defaultValue = "popular") @Pattern(regexp = "^(popular|rating|recent|trending)$", message = "sortBy must be one of: popular, rating, recent, trending") String sortBy,
            @RequestParam(required = false, defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal User user) {

        UUID userId = user != null ? user.getId() : null;
        return ResponseEntity.ok(pluginService.searchPlugins(
                category, pricing, query, sortBy, page, pageSize, userId));
    }

    /**
     * Get featured plugins.
     */
    @GetMapping("/plugins/featured")
    public ResponseEntity<List<PluginDto>> getFeaturedPlugins(
            @AuthenticationPrincipal User user) {

        UUID userId = user != null ? user.getId() : null;
        return ResponseEntity.ok(pluginService.getFeaturedPlugins(userId));
    }

    /**
     * Get installed plugins for current user.
     */
    @GetMapping("/plugins/installed")
    public ResponseEntity<List<PluginDto>> getInstalledPlugins(
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(pluginService.getInstalledPlugins(user.getId()));
    }

    /**
     * Get plugin details.
     */
    @GetMapping("/plugins/{id}")
    public ResponseEntity<PluginDetailDto> getPluginDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        UUID userId = user != null ? user.getId() : null;
        return ResponseEntity.ok(pluginService.getPluginDetail(id, userId));
    }

    /**
     * Install a plugin.
     */
    @PostMapping("/plugins/{id}/install")
    public ResponseEntity<Map<String, Object>> installPlugin(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) InstallPluginRequest request,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
            ));
        }

        try {
            Map<String, Object> result = pluginService.installPlugin(id, user.getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Plugin installation failed"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid plugin request"
            ));
        }
    }

    /**
     * Uninstall a plugin.
     */
    @DeleteMapping("/plugins/{id}/uninstall")
    public ResponseEntity<Void> uninstallPlugin(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        pluginService.uninstallPlugin(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Update a plugin to the latest version.
     */
    @PostMapping("/plugins/{id}/update")
    public ResponseEntity<Map<String, Object>> updatePlugin(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
            ));
        }

        try {
            Map<String, Object> result = pluginService.updatePlugin(id, user.getId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Plugin update failed"
            ));
        }
    }

    /**
     * Rate a plugin.
     */
    @PostMapping("/plugins/{id}/rate")
    public ResponseEntity<Map<String, Object>> ratePlugin(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
            ));
        }

        try {
            Object ratingObj = body.get("rating");
            if (!(ratingObj instanceof Number)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Rating must be a number"
                ));
            }
            int rating = ((Number) ratingObj).intValue();
            if (rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Rating must be between 1 and 5"
                ));
            }
            String review = body.get("review") instanceof String s ? s : null;
            if (review != null && review.length() > 5000) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Review text is too long"
                ));
            }
            Map<String, Object> result = pluginService.ratePlugin(id, user.getId(), rating, review);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid rating request"
            ));
        }
    }
}
