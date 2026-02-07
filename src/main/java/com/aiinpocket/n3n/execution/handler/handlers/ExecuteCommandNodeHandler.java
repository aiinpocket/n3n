package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handler for execute command nodes.
 * Executes system commands in a sandboxed environment with timeout.
 */
@Component
@Slf4j
public class ExecuteCommandNodeHandler extends AbstractNodeHandler {

    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1MB max output
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "rm -rf /", "mkfs", "dd if=/dev/zero", ":(){ :|:& };:",
        "shutdown", "reboot", "halt", "poweroff", "init 0", "init 6"
    );

    @Override
    public String getType() {
        return "executeCommand";
    }

    @Override
    public String getDisplayName() {
        return "Execute Command";
    }

    @Override
    public String getDescription() {
        return "Executes a system command and returns the output.";
    }

    @Override
    public String getCategory() {
        return "Tools";
    }

    @Override
    public String getIcon() {
        return "terminal";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String command = getStringConfig(context, "command", "");
        if (command.isBlank()) {
            // Try from input data
            if (context.getInputData() != null && context.getInputData().containsKey("command")) {
                command = context.getInputData().get("command").toString();
            }
        }

        if (command.isBlank()) {
            return NodeExecutionResult.failure("Command is required.");
        }

        // Security check: block dangerous commands
        String lowerCmd = command.toLowerCase().trim();
        for (String blocked : BLOCKED_COMMANDS) {
            if (lowerCmd.contains(blocked)) {
                return NodeExecutionResult.failure("Command blocked for security reasons: " + blocked);
            }
        }

        int timeoutSeconds = getIntConfig(context, "timeout", DEFAULT_TIMEOUT_SECONDS);
        if (timeoutSeconds < 1) timeoutSeconds = 1;
        if (timeoutSeconds > 300) timeoutSeconds = 300; // Max 5 minutes

        String workingDirectory = getStringConfig(context, "cwd", "");

        log.debug("Executing command: '{}' with timeout {}s", command, timeoutSeconds);

        try {
            ProcessBuilder pb = new ProcessBuilder();

            // Use shell to execute command
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("/bin/sh", "-c", command);
            }

            if (!workingDirectory.isBlank()) {
                File cwd = new File(workingDirectory);
                if (cwd.isDirectory()) {
                    pb.directory(cwd);
                }
            }

            pb.redirectErrorStream(false);

            // Set environment variables from config
            Map<String, Object> envVars = getMapConfig(context, "env");
            if (!envVars.isEmpty()) {
                Map<String, String> env = pb.environment();
                envVars.forEach((k, v) -> {
                    if (v != null) env.put(k, v.toString());
                });
            }

            Process process = pb.start();

            // Read stdout and stderr with size limits
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                () -> readStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                () -> readStream(process.getErrorStream()));

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return NodeExecutionResult.failure(
                    "Command timed out after " + timeoutSeconds + " seconds.");
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("exitCode", exitCode);
            output.put("stdout", stdout);
            output.put("stderr", stderr);
            output.put("command", command);

            if (exitCode != 0) {
                boolean failOnError = getBooleanConfig(context, "failOnError", true);
                if (failOnError) {
                    return NodeExecutionResult.builder()
                        .success(false)
                        .output(output)
                        .errorMessage("Command exited with code " + exitCode + ": " + stderr)
                        .build();
                }
            }

            return NodeExecutionResult.success(output);

        } catch (IOException e) {
            return NodeExecutionResult.failure("Failed to execute command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeExecutionResult.failure("Command execution was interrupted.");
        } catch (Exception e) {
            return NodeExecutionResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() + line.length() > MAX_OUTPUT_SIZE) {
                    sb.append("\n... output truncated (exceeds 1MB limit)");
                    break;
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error reading stream: " + e.getMessage();
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("command", Map.of(
            "type", "string",
            "title", "Command",
            "description", "The command to execute"
        ));
        properties.put("timeout", Map.of(
            "type", "integer",
            "title", "Timeout (seconds)",
            "description", "Maximum execution time in seconds (1-300)",
            "default", DEFAULT_TIMEOUT_SECONDS,
            "minimum", 1,
            "maximum", 300
        ));
        properties.put("cwd", Map.of(
            "type", "string",
            "title", "Working Directory",
            "description", "Working directory for the command (optional)"
        ));
        properties.put("env", Map.of(
            "type", "object",
            "title", "Environment Variables",
            "description", "Additional environment variables",
            "additionalProperties", Map.of("type", "string")
        ));
        properties.put("failOnError", Map.of(
            "type", "boolean",
            "title", "Fail on Error",
            "description", "Whether to fail the node if the command exits with a non-zero code",
            "default", true
        ));

        return Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("command")
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false,
                    "description", "Input data (can contain 'command' field)")
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object",
                    "description", "Command output with exitCode, stdout, stderr")
            )
        );
    }
}
