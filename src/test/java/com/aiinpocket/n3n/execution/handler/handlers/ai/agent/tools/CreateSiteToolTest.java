package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.service.SiteDomains;
import com.aiinpocket.n3n.site.service.SiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateSiteToolTest extends BaseServiceTest {

    @Mock
    private SiteService siteService;

    private CreateSiteTool tool;

    private final UUID userId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new CreateSiteTool(siteService, new SiteDomains(""));
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    @Test
    @DisplayName("基本屬性")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("create_site");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getParametersSchema()).containsKey("required");
    }

    @Test
    @DisplayName("無使用者時 fail-closed")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(Map.of("name", "My Site"), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(siteService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("以呼叫者身分建立網站並回傳 url")
    void createsForOwner() throws Exception {
        Site site = Site.builder()
                .id(siteId).ownerId(userId).slug("my-site-ab12")
                .name("My Site").isPublished(true).build();
        when(siteService.create(eq(userId), eq("My Site"), any())).thenReturn(site);

        ToolResult result = tool.execute(
                Map.of("name", "My Site", "description", "demo"),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("siteId", siteId.toString());
        assertThat(result.data()).containsEntry("slug", "my-site-ab12");
        assertThat(result.data()).containsEntry("url", "/sites/my-site-ab12/");
    }

    @Test
    @DisplayName("缺少 name 時回傳明確錯誤")
    void requiresName() throws Exception {
        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("name");
        verify(siteService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("服務層限制錯誤原樣轉為工具錯誤")
    void surfacesServiceErrors() throws Exception {
        when(siteService.create(eq(userId), any(), any()))
                .thenThrow(new IllegalArgumentException("Site limit reached (max 50 sites)"));

        ToolResult result = tool.execute(
                Map.of("name", "One More"), contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("limit");
    }
}
