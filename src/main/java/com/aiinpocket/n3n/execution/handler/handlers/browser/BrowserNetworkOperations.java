package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;

import java.io.IOException;
import java.util.Map;

/**
 * Handles browser network operations: setUserAgent, setExtraHeaders, clearCache.
 */
final class BrowserNetworkOperations {

    private BrowserNetworkOperations() {}

    @SuppressWarnings("unchecked")
    static NodeExecutionResult execute(
            BrowserNodeHandler.BrowserSession session, String operation,
            NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {
        return switch (operation) {
            case "setUserAgent" -> {
                String userAgent = getStringConfig(context, "userAgent", "");
                if (userAgent.isEmpty()) {
                    yield NodeExecutionResult.failure("User agent is required");
                }
                cdpSender.sendCommand(session, "Network.setUserAgentOverride", Map.of("userAgent", userAgent));
                yield NodeExecutionResult.success(Map.of("success", true, "userAgent", userAgent));
            }
            case "setExtraHeaders" -> {
                Map<String, String> headers = (Map<String, String>) context.getNodeConfig().get("headers");
                if (headers == null || headers.isEmpty()) {
                    yield NodeExecutionResult.failure("Headers are required");
                }
                cdpSender.sendCommand(session, "Network.setExtraHTTPHeaders", Map.of("headers", headers));
                yield NodeExecutionResult.success(Map.of("success", true, "headers", headers));
            }
            case "clearCache" -> {
                cdpSender.sendCommand(session, "Network.clearBrowserCache", Map.of());
                yield NodeExecutionResult.success(Map.of("success", true, "cleared", true));
            }
            default -> NodeExecutionResult.failure("Unknown network operation: " + operation);
        };
    }

    // ===== Config helpers (duplicated from AbstractNodeHandler) =====

    private static String getStringConfig(NodeExecutionContext context, String key, String defaultValue) {
        Object value = context.getNodeConfig().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
