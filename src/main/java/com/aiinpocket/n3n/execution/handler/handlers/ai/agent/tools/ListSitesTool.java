package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.service.SiteDomains;
import com.aiinpocket.n3n.site.service.SiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool：列出使用者自己的託管網站（最多 20 個，依更新時間新到舊）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ListSitesTool implements AgentNodeTool {

    private static final int MAX_RESULTS = 20;

    private final SiteService siteService;
    private final SiteDomains siteDomains;

    @Override
    public String getId() {
        return "list_sites";
    }

    @Override
    public String getName() {
        return "List Sites";
    }

    @Override
    public String getDescription() {
        return """
                Lists the user's own hosted static sites (up to 20, newest first),
                with siteId, slug, name, public url and published state. Use this
                before write_site_files when the user refers to an existing site.

                Parameters: none
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> doExecute(context));
    }

    private ToolResult doExecute(ToolExecutionContext context) {
        UUID userId = ToolSecurity.parseUserId(context);
        if (userId == null) {
            return ToolResult.failure("No authenticated user in execution context");
        }

        try {
            List<Site> sites = siteService.list(userId).stream()
                    .limit(MAX_RESULTS)
                    .toList();

            if (sites.isEmpty()) {
                return ToolResult.success("The user has no sites yet. Use create_site to make one.",
                        Map.of("sites", List.of()));
            }

            StringBuilder listing = new StringBuilder("Sites (" + sites.size() + "):\n");
            List<Map<String, Object>> data = sites.stream().map(site -> {
                String url = siteDomains.publicUrl(site.getSlug());
                listing.append("- ").append(site.getName())
                        .append(" (siteId: ").append(site.getId())
                        .append(", url: ").append(url)
                        .append(site.isPublished() ? "" : ", unpublished")
                        .append(")\n");
                return Map.<String, Object>of(
                        "siteId", site.getId().toString(),
                        "slug", site.getSlug(),
                        "name", site.getName(),
                        "url", url,
                        "isPublished", site.isPublished()
                );
            }).toList();

            return ToolResult.success(listing.toString(), Map.of("sites", data));
        } catch (Exception e) {
            log.error("list_sites tool failed for user {}", userId, e);
            return ToolResult.failure("Failed to list sites");
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
