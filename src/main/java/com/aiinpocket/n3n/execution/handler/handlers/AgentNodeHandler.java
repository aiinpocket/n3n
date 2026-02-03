package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import com.aiinpocket.n3n.gateway.node.NodeInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handler for Agent nodes.
 * Invokes capabilities on connected local agents (macOS, Windows, etc.).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentNodeHandler extends AbstractNodeHandler {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_TIMEOUT_SECONDS = 300;

    private final NodeInvoker nodeInvoker;

    @Override
    public String getType() {
        return "agent";
    }

    @Override
    public String getDisplayName() {
        return "Agent";
    }

    @Override
    public String getDescription() {
        return "Execute capabilities on connected desktop agents (macOS, Windows).";
    }

    @Override
    public String getCategory() {
        return "Agent";
    }

    @Override
    public String getIcon() {
        return "desktop";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String capability = getStringConfig(context, "capability", "");
        String platform = getStringConfig(context, "platform", "");
        int timeout = getIntConfig(context, "timeout", DEFAULT_TIMEOUT_SECONDS);

        if (capability.isEmpty()) {
            return NodeExecutionResult.failure("Capability is required");
        }

        // Get user ID from execution context
        UUID userId = context.getUserId();
        if (userId == null) {
            return NodeExecutionResult.failure("User ID is required for agent invocation");
        }

        // Cap timeout
        if (timeout > MAX_TIMEOUT_SECONDS) {
            timeout = MAX_TIMEOUT_SECONDS;
        }

        // Build arguments
        Map<String, Object> args = new HashMap<>();
        Object argsConfig = context.getNodeConfig().get("args");
        if (argsConfig instanceof Map) {
            args.putAll((Map<String, Object>) argsConfig);
        }

        // Merge input data into args if specified
        if (getBooleanConfig(context, "mergeInput", false) && context.getInputData() != null) {
            for (Map.Entry<String, Object> entry : context.getInputData().entrySet()) {
                if (entry.getValue() instanceof Map) {
                    args.putAll((Map<String, Object>) entry.getValue());
                }
            }
        }

        try {
            log.debug("Invoking agent capability {} for user {} with args: {}", capability, userId, args);

            NodeInvoker.InvokeResult result;

            if (platform != null && !platform.isEmpty()) {
                // Invoke on specific platform
                result = nodeInvoker.invokeOnPlatform(userId, platform, capability, args)
                    .get(timeout, TimeUnit.SECONDS);
            } else {
                // Invoke on any available node with the capability
                result = nodeInvoker.invokeForUser(userId, capability, args)
                    .get(timeout, TimeUnit.SECONDS);
            }

            if (result.success()) {
                return NodeExecutionResult.success(result.data());
            } else {
                return NodeExecutionResult.failure(
                    "Agent error: " + result.errorCode() + " - " + result.errorMessage()
                );
            }

        } catch (TimeoutException e) {
            log.warn("Agent invocation timed out after {}s", timeout);
            return NodeExecutionResult.failure("Agent invocation timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeExecutionResult.failure("Agent invocation interrupted");
        } catch (ExecutionException e) {
            log.error("Agent invocation failed", e);
            return NodeExecutionResult.failure("Agent invocation failed: " + e.getCause().getMessage());
        }
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object capability = config.get("capability");
        if (capability == null || capability.toString().trim().isEmpty()) {
            return ValidationResult.invalid("capability", "Capability is required");
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("capability"),
            "properties", Map.of(
                "capability", Map.of(
                    "type", "string",
                    "title", "Capability",
                    "description", "The agent capability to invoke (e.g., shell.execute, notes, filesystem)",
                    "enum", List.of(
                        "shell.execute",
                        "filesystem",
                        "notes",
                        "clipboard",
                        "notification",
                        "screenshot"
                    )
                ),
                "platform", Map.of(
                    "type", "string",
                    "title", "Platform",
                    "description", "Target platform (leave empty for any available)",
                    "enum", List.of("", "macos", "windows")
                ),
                "args", Map.of(
                    "type", "object",
                    "title", "Arguments",
                    "description", "Arguments to pass to the capability",
                    "additionalProperties", true
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "title", "Timeout (seconds)",
                    "default", 60,
                    "minimum", 1,
                    "maximum", 300
                ),
                "mergeInput", Map.of(
                    "type", "boolean",
                    "title", "Merge Input Data",
                    "default", false,
                    "description", "Merge input data from previous nodes into args"
                )
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
}
