package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles browser page operations: navigation, screenshot, PDF, script evaluation, etc.
 */
@Slf4j
final class BrowserPageOperations {

    private BrowserPageOperations() {}

    static NodeExecutionResult execute(
            BrowserNodeHandler.BrowserSession session, String operation,
            NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {
        return switch (operation) {
            case "goto" -> navigateTo(session, context, cdpSender);
            case "back" -> goBack(session, cdpSender);
            case "forward" -> goForward(session, cdpSender);
            case "reload" -> reload(session, cdpSender);
            case "getUrl" -> getCurrentUrl(session, cdpSender);
            case "getTitle" -> getTitle(session, cdpSender);
            case "getContent" -> getContent(session, cdpSender);
            case "screenshot" -> takeScreenshot(session, context, cdpSender);
            case "pdf" -> generatePdf(session, context, cdpSender);
            case "evaluate" -> evaluateScript(session, context, cdpSender);
            case "waitForSelector" -> waitForSelector(session, context, cdpSender);
            case "waitForNavigation" -> waitForNavigation(session, context, cdpSender);
            case "scroll" -> scroll(session, context, cdpSender);
            case "setViewport" -> setViewport(session, context, cdpSender);
            default -> NodeExecutionResult.failure("Unknown page operation: " + operation);
        };
    }

    private static NodeExecutionResult navigateTo(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String url = getStringConfig(context, "url", "");
        if (url.isEmpty()) {
            return NodeExecutionResult.failure("URL is required");
        }

        String waitUntil = getStringConfig(context, "waitUntil", "load");

        Map<String, Object> result = cdpSender.sendCommand(session, "Page.navigate", Map.of("url", url));

        if (result.containsKey("error")) {
            return NodeExecutionResult.failure("Navigation failed: " + result.get("error"));
        }

        // Wait for page load
        if (!waitUntil.equals("none")) {
            cdpSender.sendCommand(session, "Page.enable", Map.of());
            try {
                Thread.sleep(500); // Brief wait for navigation to start
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Map<String, Object> output = new HashMap<>();
        output.put("success", true);
        output.put("url", url);
        output.put("frameId", result.get("frameId"));

        log.info("Navigated to: {}", url);
        return NodeExecutionResult.success(output);
    }

    private static NodeExecutionResult goBack(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        Map<String, Object> history = cdpSender.sendCommand(session, "Page.getNavigationHistory", Map.of());
        int currentIndex = ((Number) history.get("currentIndex")).intValue();

        if (currentIndex > 0) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) history.get("entries");
            int entryId = ((Number) entries.get(currentIndex - 1).get("id")).intValue();
            cdpSender.sendCommand(session, "Page.navigateToHistoryEntry", Map.of("entryId", entryId));
        }

        return NodeExecutionResult.success(Map.of("success", true, "action", "back"));
    }

    private static NodeExecutionResult goForward(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        Map<String, Object> history = cdpSender.sendCommand(session, "Page.getNavigationHistory", Map.of());
        int currentIndex = ((Number) history.get("currentIndex")).intValue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) history.get("entries");

        if (currentIndex < entries.size() - 1) {
            int entryId = ((Number) entries.get(currentIndex + 1).get("id")).intValue();
            cdpSender.sendCommand(session, "Page.navigateToHistoryEntry", Map.of("entryId", entryId));
        }

        return NodeExecutionResult.success(Map.of("success", true, "action", "forward"));
    }

    private static NodeExecutionResult reload(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        cdpSender.sendCommand(session, "Page.reload", Map.of());
        return NodeExecutionResult.success(Map.of("success", true, "action", "reload"));
    }

    private static NodeExecutionResult getCurrentUrl(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        Map<String, Object> result = evaluateExpression(session, "window.location.href", cdpSender);
        return NodeExecutionResult.success(Map.of("success", true, "url", result.get("value")));
    }

    private static NodeExecutionResult getTitle(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        Map<String, Object> result = evaluateExpression(session, "document.title", cdpSender);
        return NodeExecutionResult.success(Map.of("success", true, "title", result.get("value")));
    }

    private static NodeExecutionResult getContent(
            BrowserNodeHandler.BrowserSession session,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        Map<String, Object> result = evaluateExpression(session, "document.documentElement.outerHTML", cdpSender);
        return NodeExecutionResult.success(Map.of("success", true, "content", result.get("value")));
    }

    private static NodeExecutionResult takeScreenshot(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        boolean fullPage = getBooleanConfig(context, "fullPage", false);
        String format = getStringConfig(context, "format", "png");
        int quality = getIntConfig(context, "quality", 80);

        Map<String, Object> params = new HashMap<>();
        params.put("format", format);
        if (format.equals("jpeg")) {
            params.put("quality", quality);
        }

        if (fullPage) {
            Map<String, Object> metrics = cdpSender.sendCommand(session, "Page.getLayoutMetrics", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> contentSize = (Map<String, Object>) metrics.get("contentSize");

            params.put("clip", Map.of(
                "x", 0,
                "y", 0,
                "width", contentSize.get("width"),
                "height", contentSize.get("height"),
                "scale", 1
            ));
            params.put("captureBeyondViewport", true);
        }

        Map<String, Object> result = cdpSender.sendCommand(session, "Page.captureScreenshot", params);

        Map<String, Object> output = new HashMap<>();
        output.put("success", true);
        output.put("data", result.get("data")); // base64 encoded
        output.put("format", format);

        log.info("Screenshot captured");
        return NodeExecutionResult.success(output);
    }

    private static NodeExecutionResult generatePdf(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String paperFormat = getStringConfig(context, "paperFormat", "A4");
        boolean landscape = getBooleanConfig(context, "landscape", false);
        boolean printBackground = getBooleanConfig(context, "printBackground", true);

        Map<String, Object> params = new HashMap<>();
        params.put("landscape", landscape);
        params.put("printBackground", printBackground);

        switch (paperFormat) {
            case "Letter" -> {
                params.put("paperWidth", 8.5);
                params.put("paperHeight", 11);
            }
            case "Legal" -> {
                params.put("paperWidth", 8.5);
                params.put("paperHeight", 14);
            }
            case "A3" -> {
                params.put("paperWidth", 11.69);
                params.put("paperHeight", 16.54);
            }
            default -> { // A4
                params.put("paperWidth", 8.27);
                params.put("paperHeight", 11.69);
            }
        }

        Map<String, Object> result = cdpSender.sendCommand(session, "Page.printToPDF", params);

        Map<String, Object> output = new HashMap<>();
        output.put("success", true);
        output.put("data", result.get("data")); // base64 encoded
        output.put("format", "pdf");

        log.info("PDF generated");
        return NodeExecutionResult.success(output);
    }

    private static NodeExecutionResult evaluateScript(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String script = getStringConfig(context, "script", "");
        if (script.isEmpty()) {
            return NodeExecutionResult.failure("Script is required");
        }

        Map<String, Object> result = evaluateExpression(session, script, cdpSender);

        Map<String, Object> output = new HashMap<>();
        output.put("success", true);
        output.put("result", result.get("value"));

        return NodeExecutionResult.success(output);
    }

    private static NodeExecutionResult waitForSelector(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        int timeout = getIntConfig(context, "timeout", 30000);

        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("""
            new Promise((resolve, reject) => {
                const timeout = setTimeout(() => reject(new Error('Timeout')), %d);
                const check = () => {
                    const el = document.querySelector('%s');
                    if (el) {
                        clearTimeout(timeout);
                        resolve(true);
                    } else {
                        requestAnimationFrame(check);
                    }
                };
                check();
            })
            """, timeout, selector.replace("'", "\\'"));

        Map<String, Object> params = Map.of(
            "expression", script,
            "awaitPromise", true,
            "returnByValue", true
        );

        Map<String, Object> result = cdpSender.sendCommand(session, "Runtime.evaluate", params);

        if (result.containsKey("exceptionDetails")) {
            return NodeExecutionResult.failure("Element not found: " + selector);
        }

        return NodeExecutionResult.success(Map.of("success", true, "found", true));
    }

    private static NodeExecutionResult waitForNavigation(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        int timeout = getIntConfig(context, "timeout", 30000);

        // Enable page events
        cdpSender.sendCommand(session, "Page.enable", Map.of());

        // Simple wait
        try {
            Thread.sleep(Math.min(timeout, 5000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return NodeExecutionResult.success(Map.of("success", true));
    }

    private static NodeExecutionResult scroll(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        int x = getIntConfig(context, "x", 0);
        int y = getIntConfig(context, "y", 0);
        String selector = getStringConfig(context, "selector", "");

        String script;
        if (!selector.isEmpty()) {
            script = String.format("document.querySelector('%s')?.scrollIntoView({ behavior: 'smooth' })",
                selector.replace("'", "\\'"));
        } else {
            script = String.format("window.scrollTo({ left: %d, top: %d, behavior: 'smooth' })", x, y);
        }

        evaluateExpression(session, script, cdpSender);
        return NodeExecutionResult.success(Map.of("success", true, "scrolled", true));
    }

    private static NodeExecutionResult setViewport(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        int width = getIntConfig(context, "width", 1280);
        int height = getIntConfig(context, "height", 720);
        double deviceScaleFactor = getDoubleConfig(context, "deviceScaleFactor", 1.0);
        boolean mobile = getBooleanConfig(context, "mobile", false);

        Map<String, Object> params = Map.of(
            "width", width,
            "height", height,
            "deviceScaleFactor", deviceScaleFactor,
            "mobile", mobile
        );

        cdpSender.sendCommand(session, "Emulation.setDeviceMetricsOverride", params);

        return NodeExecutionResult.success(Map.of("success", true, "width", width, "height", height));
    }

    // ===== Expression evaluation helper =====

    private static Map<String, Object> evaluateExpression(
            BrowserNodeHandler.BrowserSession session, String expression,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        Map<String, Object> params = Map.of(
            "expression", expression,
            "returnByValue", true
        );

        Map<String, Object> response = cdpSender.sendCommand(session, "Runtime.evaluate", params);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        return result != null ? result : Map.of();
    }

    // ===== Config helpers (duplicated from AbstractNodeHandler) =====

    private static String getStringConfig(NodeExecutionContext context, String key, String defaultValue) {
        Object value = context.getNodeConfig().get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static int getIntConfig(NodeExecutionContext context, String key, int defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBooleanConfig(NodeExecutionContext context, String key, boolean defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private static double getDoubleConfig(NodeExecutionContext context, String key, double defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
