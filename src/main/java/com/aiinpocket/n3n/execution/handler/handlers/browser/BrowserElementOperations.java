package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;

import java.io.IOException;
import java.util.Map;

/**
 * Handles browser element interaction operations: click, type, hover, get text, etc.
 */
final class BrowserElementOperations {

    private BrowserElementOperations() {}

    static NodeExecutionResult execute(
            BrowserNodeHandler.BrowserSession session, String operation,
            NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {
        return switch (operation) {
            case "click" -> clickElement(session, context, cdpSender);
            case "type" -> typeText(session, context, cdpSender);
            case "clear" -> clearElement(session, context, cdpSender);
            case "select" -> selectOption(session, context, cdpSender);
            case "check" -> checkElement(session, context, true, cdpSender);
            case "uncheck" -> checkElement(session, context, false, cdpSender);
            case "hover" -> hoverElement(session, context, cdpSender);
            case "focus" -> focusElement(session, context, cdpSender);
            case "getText" -> getElementText(session, context, cdpSender);
            case "getAttribute" -> getElementAttribute(session, context, cdpSender);
            case "exists" -> elementExists(session, context, cdpSender);
            default -> NodeExecutionResult.failure("Unknown element operation: " + operation);
        };
    }

    private static NodeExecutionResult clickElement(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("document.querySelector('%s')?.click()", selector.replace("'", "\\'"));
        evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "clicked", true));
    }

    private static NodeExecutionResult typeText(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        String text = getStringConfig(context, "text", "");
        int delay = getIntConfig(context, "delay", 0);

        if (selector.isEmpty() || text.isEmpty()) {
            return NodeExecutionResult.failure("Selector and text are required");
        }

        // Focus the element first
        String focusScript = String.format("document.querySelector('%s')?.focus()", selector.replace("'", "\\'"));
        evaluateExpression(session, focusScript, cdpSender);

        // Type characters
        for (char c : text.toCharArray()) {
            Map<String, Object> keyParams = Map.of(
                "type", "keyDown",
                "text", String.valueOf(c)
            );
            cdpSender.sendCommand(session, "Input.dispatchKeyEvent", keyParams);

            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return NodeExecutionResult.success(Map.of("success", true, "typed", text));
    }

    private static NodeExecutionResult clearElement(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("""
            const el = document.querySelector('%s');
            if (el) {
                el.value = '';
                el.dispatchEvent(new Event('input', { bubbles: true }));
            }
            """, selector.replace("'", "\\'"));
        evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "cleared", true));
    }

    private static NodeExecutionResult selectOption(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        String value = getStringConfig(context, "value", "");

        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("""
            const el = document.querySelector('%s');
            if (el) {
                el.value = '%s';
                el.dispatchEvent(new Event('change', { bubbles: true }));
            }
            """, selector.replace("'", "\\'"), value.replace("'", "\\'"));
        evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "selected", value));
    }

    private static NodeExecutionResult checkElement(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            boolean check, BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("""
            const el = document.querySelector('%s');
            if (el && el.checked !== %b) {
                el.click();
            }
            """, selector.replace("'", "\\'"), check);
        evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "checked", check));
    }

    private static NodeExecutionResult hoverElement(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("""
            const el = document.querySelector('%s');
            if (el) {
                el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
                el.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
            }
            """, selector.replace("'", "\\'"));
        evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "hovered", true));
    }

    private static NodeExecutionResult focusElement(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("document.querySelector('%s')?.focus()", selector.replace("'", "\\'"));
        evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "focused", true));
    }

    private static NodeExecutionResult getElementText(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("document.querySelector('%s')?.textContent || ''", selector.replace("'", "\\'"));
        Map<String, Object> result = evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "text", result.get("value")));
    }

    private static NodeExecutionResult getElementAttribute(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        String attribute = getStringConfig(context, "attribute", "");

        if (selector.isEmpty() || attribute.isEmpty()) {
            return NodeExecutionResult.failure("Selector and attribute are required");
        }

        String script = String.format("document.querySelector('%s')?.getAttribute('%s')",
            selector.replace("'", "\\'"), attribute.replace("'", "\\'"));
        Map<String, Object> result = evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "value", result.get("value")));
    }

    private static NodeExecutionResult elementExists(
            BrowserNodeHandler.BrowserSession session, NodeExecutionContext context,
            BrowserNodeHandler.CdpCommandSender cdpSender) throws IOException {

        String selector = getStringConfig(context, "selector", "");
        if (selector.isEmpty()) {
            return NodeExecutionResult.failure("Selector is required");
        }

        String script = String.format("document.querySelector('%s') !== null", selector.replace("'", "\\'"));
        Map<String, Object> result = evaluateExpression(session, script, cdpSender);

        return NodeExecutionResult.success(Map.of("success", true, "exists", result.get("value")));
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
}
