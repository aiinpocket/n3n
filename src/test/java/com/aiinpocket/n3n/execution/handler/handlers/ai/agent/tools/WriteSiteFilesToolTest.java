package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import com.aiinpocket.n3n.site.dto.SiteFileMeta;
import com.aiinpocket.n3n.site.dto.SiteFileUpsertEntry;
import com.aiinpocket.n3n.site.service.SiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WriteSiteFilesToolTest extends BaseServiceTest {

    @Mock
    private SiteService siteService;

    private WriteSiteFilesTool tool;

    private final UUID userId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new WriteSiteFilesTool(siteService);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Map<String, Object> validParams() {
        return Map.of(
                "siteId", siteId.toString(),
                "files", List.of(Map.of("path", "index.html", "content", "<html></html>")));
    }

    @Test
    @DisplayName("基本屬性")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("write_site_files");
        assertThat(tool.getCategory()).isEqualTo("platform");
    }

    @Test
    @DisplayName("無使用者時 fail-closed")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(validParams(), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(siteService, never()).upsertFiles(any(), any(), anyList());
    }

    @Test
    @DisplayName("以呼叫者身分寫入並回傳更新後檔案清單")
    void writesForOwner() throws Exception {
        when(siteService.upsertFiles(eq(siteId), eq(userId), anyList()))
                .thenReturn(List.of(SiteFileMeta.builder()
                        .path("index.html").contentType("text/html; charset=utf-8")
                        .sizeBytes(13).build()));

        ToolResult result = tool.execute(validParams(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("fileCount", 1);

        ArgumentCaptor<List<SiteFileUpsertEntry>> captor = ArgumentCaptor.captor();
        verify(siteService).upsertFiles(eq(siteId), eq(userId), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getPath()).isEqualTo("index.html");
        assertThat(captor.getValue().get(0).getContent()).isEqualTo("<html></html>");
    }

    @Test
    @DisplayName("路徑穿越由服務層拒絕並轉為工具錯誤")
    void surfacesPathTraversalRejection() throws Exception {
        when(siteService.upsertFiles(eq(siteId), eq(userId), anyList()))
                .thenThrow(new IllegalArgumentException(
                        "File path must not contain . or .. segments: ../evil.html"));

        ToolResult result = tool.execute(
                Map.of("siteId", siteId.toString(),
                        "files", List.of(Map.of("path", "../evil.html", "content", "x"))),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("..");
    }

    @Test
    @DisplayName("大小限制由服務層拒絕並轉為工具錯誤")
    void surfacesSizeLimit() throws Exception {
        when(siteService.upsertFiles(eq(siteId), eq(userId), anyList()))
                .thenThrow(new IllegalArgumentException("Site too large: 999 bytes (max 100)"));

        ToolResult result = tool.execute(validParams(), contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("too large");
    }

    @Test
    @DisplayName("非本人網站（404）不洩漏存在性")
    void notFoundForOtherUsersSite() throws Exception {
        when(siteService.upsertFiles(eq(siteId), eq(userId), anyList()))
                .thenThrow(new ResourceNotFoundException("Site not found: " + siteId));

        ToolResult result = tool.execute(validParams(), contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    @DisplayName("缺少/格式錯誤的參數回傳明確錯誤")
    void validatesParams() throws Exception {
        ToolResult noSiteId = tool.execute(
                Map.of("files", List.of(Map.of("path", "a.html", "content", "x"))),
                contextFor(userId.toString())).get();
        ToolResult badFiles = tool.execute(
                Map.of("siteId", siteId.toString(), "files", List.of(Map.of("path", "a.html"))),
                contextFor(userId.toString())).get();

        assertThat(noSiteId.success()).isFalse();
        assertThat(noSiteId.error()).contains("siteId");
        assertThat(badFiles.success()).isFalse();
        assertThat(badFiles.error()).contains("files");
        verify(siteService, never()).upsertFiles(any(), any(), anyList());
    }
}
