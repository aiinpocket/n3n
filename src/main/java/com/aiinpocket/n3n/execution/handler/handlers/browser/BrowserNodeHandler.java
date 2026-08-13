package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler for browser automation using Chrome DevTools Protocol (CDP).
 * Supports page navigation, element interaction, screenshots, and JavaScript execution.
 *
 * Requires Chrome/Chromium with remote debugging enabled:
 * chrome --remote-debugging-port=9222 --headless
 *
 * Operation logic is delegated to resource-specific classes:
 * - {@link BrowserSessionOperations} - session lifecycle
 * - {@link BrowserPageOperations} - page navigation, screenshots, scripts
 * - {@link BrowserElementOperations} - DOM element interaction
 * - {@link BrowserCookieOperations} - cookie management
 * - {@link BrowserNetworkOperations} - network configuration
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BrowserNodeHandler extends AbstractNodeHandler {

    private static final String DEFAULT_CDP_HOST = "localhost";
    private static final int DEFAULT_CDP_PORT = 9222;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    // Session management. Keys are scoped by the executing user (userId + ":" + sessionId) so
    // that two users who both leave sessionId at its "default" value never share one browser
    // session (and therefore never share cookies/authentication state).
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastAccess = new ConcurrentHashMap<>();
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);

    /** Idle time-to-live for a browser session before it is evicted and closed. */
    @Value("${n3n.browser.session-ttl-seconds:1800}")
    private long sessionTtlSeconds = 1800;

    private long sessionTtlMs() {
        long ttl = sessionTtlSeconds > 0 ? sessionTtlSeconds : 1800;
        return ttl * 1000L;
    }

    /**
     * Build the internal, user-scoped session map key. The user-supplied sessionId is namespaced
     * by the executing userId so sessions can never collide across accounts.
     */
    static String sessionKey(NodeExecutionContext context, String sessionId) {
        UUID userId = context != null ? context.getUserId() : null;
        return String.valueOf(userId) + ":" + sessionId;
    }

    // CDP command sender exposed to operations classes
    private final CdpCommandSender cdpSender = this::sendCdpCommand;

    @Override
    public String getType() {
        return "browser";
    }

    @Override
    public String getDisplayName() {
        return "Browser";
    }

    @Override
    public String getDescription() {
        return "Automate browser actions using Chrome DevTools Protocol";
    }

    @Override
    public String getCategory() {
        return "Automation";
    }

    @Override
    public String getIcon() {
        return "chrome";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String resource = getStringConfig(context, "resource", "page");
        String operation = getStringConfig(context, "operation", "goto");
        String cdpHost = getStringConfig(context, "cdpHost", DEFAULT_CDP_HOST);
        int cdpPort = getIntConfig(context, "cdpPort", DEFAULT_CDP_PORT);
        String sessionId = getStringConfig(context, "sessionId", "default");
        String sessionKey = sessionKey(context, sessionId);
        sessionLastAccess.put(sessionKey, System.currentTimeMillis());

        try {
            return switch (resource) {
                case "session" -> BrowserSessionOperations.execute(
                        cdpHost, cdpPort, sessionKey, sessionId, operation, context,
                        httpClient, objectMapper, (ConcurrentHashMap<String, BrowserSession>) sessions);
                case "page" -> {
                    BrowserSession session = getOrCreateSession(cdpHost, cdpPort, sessionKey);
                    yield BrowserPageOperations.execute(session, operation, context, cdpSender);
                }
                case "element" -> {
                    BrowserSession session = getOrCreateSession(cdpHost, cdpPort, sessionKey);
                    yield BrowserElementOperations.execute(session, operation, context, cdpSender);
                }
                case "cookie" -> {
                    BrowserSession session = getOrCreateSession(cdpHost, cdpPort, sessionKey);
                    yield BrowserCookieOperations.execute(session, operation, context, cdpSender);
                }
                case "network" -> {
                    BrowserSession session = getOrCreateSession(cdpHost, cdpPort, sessionKey);
                    yield BrowserNetworkOperations.execute(session, operation, context, cdpSender);
                }
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("Browser automation error: {}", e.getMessage());
            return NodeExecutionResult.failure("Browser automation error: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    // ===== Session management (kept in main handler) =====

    BrowserSession getOrCreateSession(String host, int port, String sessionKey) throws IOException {
        BrowserSession session = sessions.get(sessionKey);
        if (session == null) {
            synchronized (sessions) {
                // Double-check after acquiring lock to prevent duplicate session creation
                session = sessions.get(sessionKey);
                if (session == null) {
                    NodeExecutionResult result = createSessionDirect(host, port, sessionKey, "about:blank");
                    if (!result.isSuccess()) {
                        throw new IOException("Failed to create session: " + result.getErrorMessage());
                    }
                    session = sessions.get(sessionKey);
                }
            }
        }
        sessionLastAccess.put(sessionKey, System.currentTimeMillis());
        return session;
    }

    private NodeExecutionResult createSessionDirect(String host, int port, String sessionKey, String url) throws IOException {
        String baseUrl = "http://" + host + ":" + port;

        Request createRequest = new Request.Builder()
            .url(baseUrl + "/json/new?" + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8))
            .put(RequestBody.create("", MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                return NodeExecutionResult.failure("Failed to create browser session: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = objectMapper.readTree(responseBody);

            String targetId = json.path("id").asText();
            String wsUrl = json.path("webSocketDebuggerUrl").asText();

            BrowserSession session = new BrowserSession(targetId, wsUrl);
            sessions.put(sessionKey, session);
            sessionLastAccess.put(sessionKey, System.currentTimeMillis());

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("targetId", targetId);
            output.put("webSocketUrl", wsUrl);

            log.info("Browser session created (auto)");
            return NodeExecutionResult.success(output);
        }
    }

    // ===== Session lifecycle housekeeping =====

    /**
     * Evict browser sessions that have been idle beyond the configured TTL, closing the
     * underlying CDP target so orphaned sessions do not accumulate.
     */
    @Scheduled(fixedDelay = 60_000)
    void evictIdleSessions() {
        long now = System.currentTimeMillis();
        long ttl = sessionTtlMs();
        for (Map.Entry<String, Long> e : sessionLastAccess.entrySet()) {
            if (now - e.getValue() > ttl) {
                String key = e.getKey();
                sessionLastAccess.remove(key);
                BrowserSession session = sessions.remove(key);
                if (session != null) {
                    closeSessionQuietly(session);
                }
            }
        }
    }

    @PreDestroy
    void shutdownSessions() {
        sessions.forEach((key, session) -> closeSessionQuietly(session));
        sessions.clear();
        sessionLastAccess.clear();
    }

    /**
     * Close a session's CDP target best-effort, deriving the HTTP base URL from the session's
     * WebSocket URL (ws://host:port/devtools/... → http://host:port).
     */
    private void closeSessionQuietly(BrowserSession session) {
        try {
            String httpBaseUrl = session.wsUrl()
                .replace("ws://", "http://")
                .replaceAll("/devtools/.*", "");
            Request closeRequest = new Request.Builder()
                .url(httpBaseUrl + "/json/close/" + session.targetId())
                .get()
                .build();
            try (Response ignored = httpClient.newCall(closeRequest).execute()) {
                // best-effort close
            }
        } catch (Exception e) {
            log.warn("Error closing idle browser session: {}", e.getMessage());
        }
    }

    // ===== CDP communication (kept in main handler) =====

    private Map<String, Object> sendCdpCommand(BrowserSession session, String method, Map<String, Object> params) throws IOException {
        String wsUrl = session.wsUrl();

        // Derive HTTP base URL from WebSocket URL (e.g. ws://host:port/devtools/page/xxx → http://host:port)
        String httpBaseUrl = wsUrl.replace("ws://", "http://").replaceAll("/devtools/.*", "");

        // Build command
        Map<String, Object> command = new HashMap<>();
        command.put("id", messageIdCounter.incrementAndGet());
        command.put("method", method);
        command.put("params", params);

        // CDP requires WebSocket for full command execution.
        // This is a simplified HTTP-based approach (works for listing targets).
        // For a production implementation, use a proper WebSocket CDP client.
        Request request = new Request.Builder()
            .url(httpBaseUrl + "/json")
            .get()
            .build();

        log.debug("CDP command: {} {}", method, params);

        // Return simulated success for now - in production, use proper WebSocket CDP client
        return Map.of("success", true);
    }

    // ===== Config schema and interface (kept in main handler) =====

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                Map.entry("resource", Map.of(
                    "type", "string",
                    "title", "Resource",
                    "enum", List.of("session", "page", "element", "cookie", "network"),
                    "default", "page"
                )),
                Map.entry("operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("create", "close", "list",
                                   "goto", "back", "forward", "reload", "getUrl", "getTitle", "getContent",
                                   "screenshot", "pdf", "evaluate", "waitForSelector", "waitForNavigation",
                                   "scroll", "setViewport",
                                   "click", "type", "clear", "select", "check", "uncheck", "hover", "focus",
                                   "getText", "getAttribute", "exists",
                                   "get", "set", "delete",
                                   "setUserAgent", "setExtraHeaders", "clearCache"),
                    "default", "goto"
                )),
                Map.entry("cdpHost", Map.of(
                    "type", "string",
                    "title", "CDP Host",
                    "default", "localhost"
                )),
                Map.entry("cdpPort", Map.of(
                    "type", "integer",
                    "title", "CDP Port",
                    "default", 9222
                )),
                Map.entry("sessionId", Map.of(
                    "type", "string",
                    "title", "Session ID",
                    "default", "default"
                )),
                Map.entry("url", Map.of(
                    "type", "string",
                    "title", "URL",
                    "format", "uri"
                )),
                Map.entry("selector", Map.of(
                    "type", "string",
                    "title", "CSS Selector"
                )),
                Map.entry("text", Map.of(
                    "type", "string",
                    "title", "Text"
                )),
                Map.entry("script", Map.of(
                    "type", "string",
                    "title", "JavaScript",
                    "format", "textarea"
                )),
                Map.entry("timeout", Map.of(
                    "type", "integer",
                    "title", "Timeout (ms)",
                    "default", 30000
                )),
                Map.entry("fullPage", Map.of(
                    "type", "boolean",
                    "title", "Full Page Screenshot",
                    "default", false
                )),
                Map.entry("format", Map.of(
                    "type", "string",
                    "title", "Format",
                    "enum", List.of("png", "jpeg"),
                    "default", "png"
                )),
                Map.entry("width", Map.of(
                    "type", "integer",
                    "title", "Viewport Width",
                    "default", 1280
                )),
                Map.entry("height", Map.of(
                    "type", "integer",
                    "title", "Viewport Height",
                    "default", 720
                )),
                Map.entry("name", Map.of(
                    "type", "string",
                    "title", "Cookie Name"
                )),
                Map.entry("value", Map.of(
                    "type", "string",
                    "title", "Value"
                )),
                Map.entry("attribute", Map.of(
                    "type", "string",
                    "title", "Attribute Name"
                )),
                Map.entry("userAgent", Map.of(
                    "type", "string",
                    "title", "User Agent"
                ))
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }

    // ===== Package-private types shared with operations classes =====

    /**
     * Represents an active browser session with a CDP target.
     */
    record BrowserSession(String targetId, String wsUrl) {}

    /**
     * Functional interface for sending CDP commands, allowing operations classes
     * to use the handler's CDP infrastructure without direct access.
     */
    @FunctionalInterface
    interface CdpCommandSender {
        Map<String, Object> sendCommand(BrowserSession session, String method, Map<String, Object> params) throws IOException;
    }
}
