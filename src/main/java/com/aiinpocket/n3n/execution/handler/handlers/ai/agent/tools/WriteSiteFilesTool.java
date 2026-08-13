package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.aiinpocket.n3n.site.dto.SiteFileMeta;
import com.aiinpocket.n3n.site.dto.SiteFileUpsertEntry;
import com.aiinpocket.n3n.site.service.SiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool：把多個文字檔寫入自己的網站（同 path 覆寫）。
 * 路徑淨化、副檔名白名單、大小/數量限制皆由 SiteService 強制。
 * v1 僅支援 UTF-8 文字內容（不支援二進位）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WriteSiteFilesTool implements AgentNodeTool {

    private final SiteService siteService;

    @Override
    public String getId() {
        return "write_site_files";
    }

    @Override
    public String getName() {
        return "Write Site Files";
    }

    @Override
    public String getDescription() {
        return """
                Writes text files (HTML/CSS/JS/JSON/SVG/TXT) into one of the user's
                hosted sites — existing paths are overwritten, new paths are created.
                Generate complete, self-contained static sites: always include an
                index.html at the root, reference assets with relative paths
                (e.g. "assets/style.css"), and keep everything for one project in the
                same site. Binary files are not supported by this tool.
                Returns the updated file list.

                Parameters:
                - siteId: UUID of the site (from create_site or list_sites) (required)
                - files: Array of {path, content} objects (required)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "siteId", Map.of(
                                "type", "string",
                                "description", "UUID of the site to write into"
                        ),
                        "files", Map.of(
                                "type", "array",
                                "description", "Files to write (existing paths are overwritten)",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "path", Map.of(
                                                        "type", "string",
                                                        "description", "Relative path, e.g. \"index.html\" or \"assets/style.css\""
                                                ),
                                                "content", Map.of(
                                                        "type", "string",
                                                        "description", "UTF-8 text content of the file"
                                                )
                                        ),
                                        "required", List.of("path", "content")
                                )
                        )
                ),
                "required", List.of("siteId", "files")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> doExecute(parameters, context));
    }

    private ToolResult doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        UUID userId = ToolSecurity.parseUserId(context);
        if (userId == null) {
            return ToolResult.failure("No authenticated user in execution context");
        }

        UUID siteId = ToolSecurity.parseUuid(parameters.get("siteId") instanceof String s ? s : null);
        if (siteId == null) {
            return ToolResult.failure("Invalid or missing siteId (must be a UUID)");
        }

        List<SiteFileUpsertEntry> entries = parseFiles(parameters.get("files"));
        if (entries == null || entries.isEmpty()) {
            return ToolResult.failure("Missing or invalid parameter: files (array of {path, content})");
        }

        try {
            List<SiteFileMeta> updated = siteService.upsertFiles(siteId, userId, entries);
            StringBuilder listing = new StringBuilder();
            for (SiteFileMeta meta : updated) {
                listing.append("- ").append(meta.path())
                        .append(" (").append(meta.sizeBytes()).append(" bytes)\n");
            }
            return ToolResult.success(
                    "Wrote " + entries.size() + " file(s). Site now has "
                            + updated.size() + " file(s):\n" + listing,
                    Map.of(
                            "siteId", siteId.toString(),
                            "fileCount", updated.size(),
                            "files", updated.stream().map(m -> Map.of(
                                    "path", m.path(),
                                    "sizeBytes", m.sizeBytes())).toList()
                    ));
        } catch (ResourceNotFoundException e) {
            return ToolResult.failure("Site not found: " + siteId);
        } catch (IllegalArgumentException e) {
            return ToolResult.failure("Cannot write site files: " + e.getMessage());
        } catch (Exception e) {
            log.error("write_site_files tool failed for user {} site {}", userId, siteId, e);
            return ToolResult.failure("Failed to write site files");
        }
    }

    private List<SiteFileUpsertEntry> parseFiles(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<SiteFileUpsertEntry> entries = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                return null;
            }
            String path = map.get("path") instanceof String p ? p : null;
            String content = map.get("content") instanceof String c ? c : null;
            if (path == null || content == null) {
                return null;
            }
            entries.add(SiteFileUpsertEntry.builder().path(path).content(content).build());
        }
        return entries;
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
