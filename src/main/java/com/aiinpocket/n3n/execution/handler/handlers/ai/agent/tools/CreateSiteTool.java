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
 * Agent tool：建立一個平台託管的靜態網站，回傳 siteId 與公開 URL。
 * 建立後用 write_site_files 寫入檔案。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateSiteTool implements AgentNodeTool {

    private final SiteService siteService;
    private final SiteDomains siteDomains;

    @Override
    public String getId() {
        return "create_site";
    }

    @Override
    public String getName() {
        return "Create Site";
    }

    @Override
    public String getDescription() {
        return """
                Creates a new static website hosted on this platform, instantly reachable
                at a shareable URL. Use this when the user asks you to build a web page,
                site, landing page, portfolio, demo, etc. After creating, use
                write_site_files to add the files. Generate complete self-contained
                static sites: always include index.html at the site root, use relative
                paths for all assets (e.g. "assets/style.css", not "/assets/style.css"),
                and keep everything for one project in one site.
                Returns siteId, slug, and the public url.

                Parameters:
                - name: Human-readable site name (required)
                - description: Short description of the site (optional)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "Human-readable site name, e.g. \"My Portfolio\""
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "Short description of what the site is"
                        )
                ),
                "required", List.of("name")
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

        String name = parameters.get("name") instanceof String n && !n.isBlank() ? n.trim() : null;
        if (name == null) {
            return ToolResult.failure("Missing required parameter: name");
        }
        String description = parameters.get("description") instanceof String d && !d.isBlank() ? d : null;

        try {
            Site site = siteService.create(userId, name, description);
            String url = siteDomains.publicUrl(site.getSlug());
            return ToolResult.success(
                    "Site created: " + site.getName()
                            + " (siteId: " + site.getId() + ", slug: " + site.getSlug() + ")\n"
                            + "Public URL: " + url + "\n"
                            + "Now add files with write_site_files (include index.html).",
                    Map.of(
                            "siteId", site.getId().toString(),
                            "slug", site.getSlug(),
                            "url", url
                    ));
        } catch (IllegalArgumentException e) {
            return ToolResult.failure("Cannot create site: " + e.getMessage());
        } catch (Exception e) {
            log.error("create_site tool failed for user {}", userId, e);
            return ToolResult.failure("Failed to create site");
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
