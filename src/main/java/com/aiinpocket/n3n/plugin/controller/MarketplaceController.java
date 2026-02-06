package com.aiinpocket.n3n.plugin.controller;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.plugin.dto.*;
import com.aiinpocket.n3n.plugin.service.PluginService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@Tag(name = "Marketplace", description = "Plugin marketplace")
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
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String pricing,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false, defaultValue = "popular") String sortBy,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
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
            @RequestBody(required = false) InstallPluginRequest request,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
            ));
        }

        try {
            Map<String, Object> result = pluginService.installPlugin(id, user.getId(), request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Uninstall a plugin.
     */
    @DeleteMapping("/plugins/{id}/uninstall")
    public ResponseEntity<Map<String, Object>> uninstallPlugin(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
            ));
        }

        try {
            Map<String, Object> result = pluginService.uninstallPlugin(id, user.getId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
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
                    "message", e.getMessage()
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
            int rating = ((Number) body.get("rating")).intValue();
            String review = (String) body.get("review");
            Map<String, Object> result = pluginService.ratePlugin(id, user.getId(), rating, review);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
