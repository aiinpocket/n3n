package com.aiinpocket.n3n.plugin.controller;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.plugin.dto.*;
import com.aiinpocket.n3n.plugin.service.PluginService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomToolsControllerTest {

    @Mock
    private PluginService pluginService;

    // Cannot use @InjectMocks due to @Qualifier constructor
    private CustomToolsController controller() {
        return new CustomToolsController(pluginService);
    }

    // ===== Helpers =====

    private User mockUser(UUID userId) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        return user;
    }

    private PluginCategoryDto sampleCategory(String name) {
        return PluginCategoryDto.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .displayName(name.substring(0, 1).toUpperCase() + name.substring(1))
                .description("Description for " + name)
                .icon("icon-" + name)
                .count(5L)
                .build();
    }

    private PluginDto samplePlugin(String name) {
        return PluginDto.builder()
                .id(UUID.randomUUID())
                .name(name)
                .displayName(name)
                .description("A " + name + " plugin")
                .category("action")
                .version("1.0.0")
                .isInstalled(false)
                .build();
    }

    // ===== GET /categories =====

    @Test
    void getCategories_shouldReturnAllCategories() {
        var categories = List.of(sampleCategory("action"), sampleCategory("trigger"));
        when(pluginService.getCategories()).thenReturn(categories);

        var response = controller().getCategories();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    // ===== GET /plugins =====

    @Test
    void searchPlugins_shouldReturnResults() {
        UUID userId = UUID.randomUUID();
        var result = PluginSearchResult.builder()
                .plugins(List.of(samplePlugin("httpRequest")))
                .total(1)
                .page(0)
                .pageSize(20)
                .totalPages(1)
                .build();

        when(pluginService.searchPlugins(eq("action"), isNull(), eq("http"), eq("popular"), eq(0), eq(20), eq(userId)))
                .thenReturn(result);

        var response = controller().searchPlugins("action", null, "http", "popular", 0, 20, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotal()).isEqualTo(1);
    }

    @Test
    void searchPlugins_shouldHandleNullUser() {
        var result = PluginSearchResult.builder()
                .plugins(List.of())
                .total(0)
                .page(0)
                .pageSize(20)
                .totalPages(0)
                .build();

        when(pluginService.searchPlugins(isNull(), isNull(), isNull(), eq("popular"), eq(0), eq(20), isNull()))
                .thenReturn(result);

        var response = controller().searchPlugins(null, null, null, "popular", 0, 20, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ===== GET /plugins/featured =====

    @Test
    void getFeaturedPlugins_shouldReturnPlugins() {
        UUID userId = UUID.randomUUID();
        var plugins = List.of(samplePlugin("featured1"), samplePlugin("featured2"));
        when(pluginService.getFeaturedPlugins(userId)).thenReturn(plugins);

        var response = controller().getFeaturedPlugins(mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void getFeaturedPlugins_shouldHandleNullUser() {
        when(pluginService.getFeaturedPlugins(null)).thenReturn(List.of());

        var response = controller().getFeaturedPlugins(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ===== GET /plugins/installed =====

    @Test
    void getInstalledPlugins_shouldReturnUserPlugins() {
        UUID userId = UUID.randomUUID();
        var plugins = List.of(samplePlugin("installed1"));
        when(pluginService.getInstalledPlugins(userId)).thenReturn(plugins);

        var response = controller().getInstalledPlugins(mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getInstalledPlugins_shouldReturnEmptyWhenNoUser() {
        var response = controller().getInstalledPlugins(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(pluginService, never()).getInstalledPlugins(any());
    }

    // ===== GET /plugins/{id} =====

    @Test
    void getPluginDetail_shouldReturnDetail() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var detail = PluginDetailDto.builder()
                .plugin(samplePlugin("test-plugin"))
                .readme("# Test Plugin")
                .changelog("v1.0.0 initial release")
                .capabilities(List.of("http"))
                .build();

        when(pluginService.getPluginDetail(pluginId, userId)).thenReturn(detail);

        var response = controller().getPluginDetail(pluginId, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getReadme()).isEqualTo("# Test Plugin");
    }

    // ===== POST /plugins/{id}/install =====

    @Test
    void installPlugin_shouldReturn201OnSuccess() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pluginService.installPlugin(eq(pluginId), eq(userId), any()))
                .thenReturn(Map.of("success", true, "message", "Installed"));

        var response = controller().installPlugin(pluginId, null, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void installPlugin_shouldReturn401WhenNoUser() {
        var response = controller().installPlugin(UUID.randomUUID(), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void installPlugin_shouldReturnBadRequestOnIllegalState() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pluginService.installPlugin(eq(pluginId), eq(userId), any()))
                .thenThrow(new IllegalStateException("Already installed"));

        var response = controller().installPlugin(pluginId, null, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void installPlugin_shouldReturnBadRequestOnIllegalArgument() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pluginService.installPlugin(eq(pluginId), eq(userId), any()))
                .thenThrow(new IllegalArgumentException("Invalid"));

        var response = controller().installPlugin(pluginId, null, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== DELETE /plugins/{id}/uninstall =====

    @Test
    void uninstallPlugin_shouldReturn204() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var response = controller().uninstallPlugin(pluginId, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pluginService).uninstallPlugin(pluginId, userId);
    }

    @Test
    void uninstallPlugin_shouldReturn401WhenNoUser() {
        var response = controller().uninstallPlugin(UUID.randomUUID(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== POST /plugins/{id}/update =====

    @Test
    void updatePlugin_shouldReturnOkOnSuccess() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pluginService.updatePlugin(pluginId, userId))
                .thenReturn(Map.of("success", true, "message", "Updated"));

        var response = controller().updatePlugin(pluginId, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatePlugin_shouldReturn401WhenNoUser() {
        var response = controller().updatePlugin(UUID.randomUUID(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updatePlugin_shouldReturnBadRequestOnError() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pluginService.updatePlugin(pluginId, userId))
                .thenThrow(new IllegalStateException("Not installed"));

        var response = controller().updatePlugin(pluginId, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== POST /plugins/{id}/rate =====

    @Test
    void ratePlugin_shouldSucceed() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(pluginService.ratePlugin(pluginId, userId, 5, "Great!"))
                .thenReturn(Map.of("success", true));

        Map<String, Object> body = Map.of("rating", 5, "review", "Great!");
        var response = controller().ratePlugin(pluginId, body, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ratePlugin_shouldReturn401WhenNoUser() {
        var response = controller().ratePlugin(UUID.randomUUID(), Map.of("rating", 5), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ratePlugin_shouldRejectNonNumericRating() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> body = Map.of("rating", "five");
        var response = controller().ratePlugin(pluginId, body, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ratePlugin_shouldRejectOutOfRangeRating() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> body = Map.of("rating", 0);
        var response = controller().ratePlugin(pluginId, body, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        body = Map.of("rating", 6);
        response = controller().ratePlugin(pluginId, body, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ratePlugin_shouldRejectTooLongReview() {
        UUID pluginId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String longReview = "x".repeat(2001);
        Map<String, Object> body = new HashMap<>();
        body.put("rating", 5);
        body.put("review", longReview);

        var response = controller().ratePlugin(pluginId, body, mockUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
