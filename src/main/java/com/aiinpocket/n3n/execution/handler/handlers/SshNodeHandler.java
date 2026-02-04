package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handler for SSH node.
 *
 * Executes commands on remote servers via SSH.
 * Supports password and private key authentication.
 *
 * Config options:
 * - host: SSH server hostname or IP
 * - port: SSH port (default: 22)
 * - username: SSH username
 * - authType: "password" or "privateKey"
 * - credentialId: UUID of the credential to use
 * - command: Command to execute
 * - timeout: Command timeout in seconds (default: 60)
 * - failOnError: Whether to fail on non-zero exit code (default: true)
 * - environment: Environment variables to set
 */
@Component
@Slf4j
public class SshNodeHandler extends AbstractNodeHandler {

    private static final int DEFAULT_PORT = 22;
    private static final int DEFAULT_TIMEOUT = 60;
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds

    @Override
    public String getType() {
        return "ssh";
    }

    @Override
    public String getDisplayName() {
        return "SSH";
    }

    @Override
    public String getDescription() {
        return "Execute commands on remote servers via SSH. Supports password and private key authentication.";
    }

    @Override
    public String getCategory() {
        return "System";
    }

    @Override
    public String getIcon() {
        return "terminal";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String host = getStringConfig(context, "host", "");
        int port = getIntConfig(context, "port", DEFAULT_PORT);
        String username = getStringConfig(context, "username", "");
        String authType = getStringConfig(context, "authType", "password");
        String command = getStringConfig(context, "command", "");
        int timeout = getIntConfig(context, "timeout", DEFAULT_TIMEOUT);
        boolean failOnError = getBooleanConfig(context, "failOnError", true);

        // Validate required fields
        if (host.isEmpty()) {
            return NodeExecutionResult.failure("SSH host is required");
        }
        if (username.isEmpty()) {
            return NodeExecutionResult.failure("SSH username is required");
        }
        if (command.isEmpty()) {
            return NodeExecutionResult.failure("SSH command is required");
        }

        // Get credentials
        String credentialIdStr = getStringConfig(context, "credentialId", "");
        Map<String, Object> credentials = null;

        if (!credentialIdStr.isEmpty() && context.getCredentialResolver() != null) {
            try {
                UUID credentialId = UUID.fromString(credentialIdStr);
                credentials = context.getCredentialResolver().resolve(credentialId, context.getUserId());
            } catch (Exception e) {
                log.error("Failed to resolve credentials: {}", e.getMessage());
                return NodeExecutionResult.failure("Failed to resolve SSH credentials: " + e.getMessage());
            }
        }

        log.info("Executing SSH command: host={}@{}, port={}, authType={}", username, host, port, authType);

        SSHClient client = new SSHClient();
        try {
            // Configure SSH client
            client.addHostKeyVerifier(new PromiscuousVerifier()); // Skip host key verification
            client.setConnectTimeout(CONNECTION_TIMEOUT);
            client.setTimeout(timeout * 1000);

            // Connect
            client.connect(host, port);

            // Authenticate
            authenticate(client, username, authType, credentials);

            // Execute command
            Map<String, Object> result = executeCommand(client, command, timeout);

            int exitCode = (int) result.get("exitCode");
            if (failOnError && exitCode != 0) {
                return NodeExecutionResult.failure(String.format(
                    "SSH command failed with exit code %d: %s",
                    exitCode, result.get("stderr")
                ));
            }

            // Build output
            Map<String, Object> output = new HashMap<>();
            output.put("exitCode", exitCode);
            output.put("stdout", result.get("stdout"));
            output.put("stderr", result.get("stderr"));
            output.put("success", exitCode == 0);
            output.put("host", host);
            output.put("command", command);

            log.info("SSH command completed: host={}, exitCode={}", host, exitCode);

            return NodeExecutionResult.success(output);

        } catch (Exception e) {
            log.error("SSH execution failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("SSH execution failed: " + e.getMessage());
        } finally {
            try {
                client.disconnect();
            } catch (IOException e) {
                log.warn("Error disconnecting SSH client: {}", e.getMessage());
            }
        }
    }

    private void authenticate(SSHClient client, String username, String authType,
                               Map<String, Object> credentials) throws IOException {
        if (credentials == null || credentials.isEmpty()) {
            throw new IOException("No credentials provided for SSH authentication");
        }

        if ("privateKey".equals(authType)) {
            String privateKey = (String) credentials.get("privateKey");
            String passphrase = (String) credentials.get("passphrase");

            if (privateKey == null || privateKey.isEmpty()) {
                throw new IOException("Private key not found in credentials");
            }

            KeyProvider keyProvider;
            if (passphrase != null && !passphrase.isEmpty()) {
                keyProvider = client.loadKeys(privateKey, null,
                    net.schmizz.sshj.userauth.password.PasswordUtils.createOneOff(passphrase.toCharArray()));
            } else {
                keyProvider = client.loadKeys(privateKey, null, null);
            }

            client.authPublickey(username, keyProvider);
            log.debug("Authenticated with private key for user: {}", username);

        } else {
            // Password authentication
            String password = (String) credentials.get("password");
            if (password == null || password.isEmpty()) {
                throw new IOException("Password not found in credentials");
            }

            client.authPassword(username, password);
            log.debug("Authenticated with password for user: {}", username);
        }
    }

    private Map<String, Object> executeCommand(SSHClient client, String command, int timeout) throws IOException {
        Map<String, Object> result = new HashMap<>();

        try (Session session = client.startSession()) {
            Session.Command cmd = session.exec(command);

            // Read output
            String stdout = IOUtils.readFully(cmd.getInputStream()).toString();
            String stderr = IOUtils.readFully(cmd.getErrorStream()).toString();

            // Wait for command completion
            cmd.join(timeout, TimeUnit.SECONDS);

            result.put("stdout", stdout.trim());
            result.put("stderr", stderr.trim());
            result.put("exitCode", cmd.getExitStatus() != null ? cmd.getExitStatus() : -1);

            return result;
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("host", "username", "command"),
            "properties", Map.of(
                "host", Map.of(
                    "type", "string",
                    "title", "Host",
                    "description", "SSH server hostname or IP address"
                ),
                "port", Map.of(
                    "type", "integer",
                    "title", "Port",
                    "description", "SSH port number",
                    "default", 22,
                    "minimum", 1,
                    "maximum", 65535
                ),
                "username", Map.of(
                    "type", "string",
                    "title", "Username",
                    "description", "SSH username"
                ),
                "authType", Map.of(
                    "type", "string",
                    "title", "Authentication Type",
                    "enum", List.of("password", "privateKey"),
                    "enumNames", List.of("Password", "Private Key"),
                    "default", "password"
                ),
                "credentialId", Map.of(
                    "type", "string",
                    "title", "Credential",
                    "description", "Select the SSH credential to use",
                    "format", "credential"
                ),
                "command", Map.of(
                    "type", "string",
                    "title", "Command",
                    "description", "Shell command to execute",
                    "ui:widget", "textarea"
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "title", "Timeout (seconds)",
                    "description", "Maximum time to wait for command completion",
                    "default", 60,
                    "minimum", 1,
                    "maximum", 3600
                ),
                "failOnError", Map.of(
                    "type", "boolean",
                    "title", "Fail on Error",
                    "description", "Fail the node if command returns non-zero exit code",
                    "default", true
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
                Map.of("name", "output", "type", "object", "description", "Command execution result (stdout, stderr, exitCode)")
            )
        );
    }
}
