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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListSitesToolTest extends BaseServiceTest {

    @Mock
    private SiteService siteService;

    private ListSitesTool tool;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new ListSitesTool(siteService, new SiteDomains(""));
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Site siteNamed(String name, String slug) {
        return Site.builder()
                .id(UUID.randomUUID()).ownerId(userId)
                .slug(slug).name(name).isPublished(true).build();
    }

    @Test
    @DisplayName("基本屬性")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("list_sites");
        assertThat(tool.getCategory()).isEqualTo("platform");
    }

    @Test
    @DisplayName("無使用者時 fail-closed")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(Map.of(), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(siteService, never()).list(any());
    }

    @Test
    @DisplayName("僅列出呼叫者自己的網站，含 url")
    void listsOwnSites() throws Exception {
        when(siteService.list(userId)).thenReturn(List.of(
                siteNamed("Portfolio", "portfolio-ab12"),
                siteNamed("Landing", "landing-cd34")));

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("/sites/portfolio-ab12/");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sites = (List<Map<String, Object>>) result.data().get("sites");
        assertThat(sites).hasSize(2);
        assertThat(sites.get(0)).containsEntry("slug", "portfolio-ab12");
    }

    @Test
    @DisplayName("最多回傳 20 個")
    void capsAtTwenty() throws Exception {
        List<Site> many = IntStream.range(0, 30)
                .mapToObj(i -> siteNamed("Site " + i, "site-" + i + "-abcd"))
                .toList();
        when(siteService.list(userId)).thenReturn(many);

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sites = (List<Map<String, Object>>) result.data().get("sites");
        assertThat(sites).hasSize(20);
    }

    @Test
    @DisplayName("沒有網站時給出可行動的提示")
    void emptyListHint() throws Exception {
        when(siteService.list(userId)).thenReturn(List.of());

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("create_site");
    }
}
