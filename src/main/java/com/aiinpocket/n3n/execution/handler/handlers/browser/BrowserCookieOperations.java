package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles browser cookie operations: get, set, delete, clear.
 */
final class BrowserCookieOperations {

    private BrowserCookieOperations() {}

    static NodeExecutionResult execute(
            BrowserNodeHandler.BrowserSession session, String operation,
            NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {
        return switch (operation) {
            case "get" -> getCookies(session, context, cdpSender);
            case "set" -> setCookie(session, context, cdpSender);
            case "delete" -> deleteCookie(session, context, cdpSender);
            case "clear" -> clearCookies(session, cdpSender);
            default -> NodeExecutionResult.failure("Unknown cookie operation: " + operation);
        };
    }

    private static NodeExecutionResult getCookies(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String name = getStringConfig(context, "name", "");

        Map<String, Object> result = cdpSender.sendCommand(session, "Network.getCookies", Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cookies = (List<Map<String, Object>>) result.get("cookies");

        if (!name.isEmpty()) {
            cookies = cookies.stream()
                .filter(c -> name.equals(c.get("name")))
                .toList();
        }

        return NodeExecutionResult.success(Map.of("success", true, "cookies", cookies));
    }

    private static NodeExecutionResult setCookie(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String name = getStringConfig(context, "name", "");
        String value = getStringConfig(context, "value", "");
        String domain = getStringConfig(context, "domain", "");
        String path = getStringConfig(context, "path", "/");

        if (name.isEmpty() || value.isEmpty()) {
            return NodeExecutionResult.failure("Name and value are required");
        }

        Map<String, Object> cookie = new HashMap<>();
        cookie.put("name", name);
        cookie.put("value", value);
        cookie.put("path", path);

        if (!domain.isEmpty()) {
            cookie.put("domain", domain);
        }

        cdpSender.sendCommand(session, "Network.setCookie", cookie);

        return NodeExecutionResult.success(Map.of("success", true, "set", true));
    }

    private static NodeExecutionResult deleteCookie(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String name = getStringConfig(context, "name", "");
        if (name.isEmpty()) {
            return NodeExecutionResult.failure("Cookie name is required");
        }

        cdpSender.sendCommand(session, "Network.deleteCookies", Map.of("name", name));

        return NodeExecutionResult.success(Map.of("success", true, "deleted", true));
    }

    private static NodeExecutionResult clearCookies(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        cdpSender.sendCommand(session, "Network.clearBrowserCookies", Map.of());
        return NodeExecutionResult.success(Map.of("success", true, "cleared", true));
    }

    // ===== Config helpers (duplicated from AbstractNodeHandler) =====

    private static String getStringConfig(NodeExecutionContext context, String key, String defaultValue) {
        Object value = context.getNodeConfig().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
