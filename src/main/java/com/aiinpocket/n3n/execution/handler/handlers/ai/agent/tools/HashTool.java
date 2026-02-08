package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hash computation tool
 * Supports MD5, SHA-1, SHA-256, SHA-512 and other hash algorithms
 */
@Component
@Slf4j
public class HashTool implements AgentNodeTool {

    private static final List<String> SUPPORTED_ALGORITHMS = List.of(
            "MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512"
    );

    @Override
    public String getId() {
        return "hash";
    }

    @Override
    public String getName() {
        return "Hash";
    }

    @Override
    public String getDescription() {
        return """
                Compute hash values for text or data. Supported algorithms:
                - MD5: 128-bit hash (not recommended for security purposes)
                - SHA-1: 160-bit hash (not recommended for security purposes)
                - SHA-256: 256-bit hash (recommended)
                - SHA-384: 384-bit hash
                - SHA-512: 512-bit hash

                Parameters:
                - text: Text to hash
                - algorithm: Hash algorithm (default SHA-256)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to hash"
                        ),
                        "algorithm", Map.of(
                                "type", "string",
                                "enum", SUPPORTED_ALGORITHMS,
                                "description", "Hash algorithm",
                                "default", "SHA-256"
                        )
                ),
                "required", List.of("text")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = (String) parameters.get("text");
                if (text == null) {
                    return ToolResult.failure("Text cannot be empty");
                }

                String algorithm = (String) parameters.getOrDefault("algorithm", "SHA-256");
                if (!SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return ToolResult.failure("Unsupported algorithm: " + algorithm);
                }

                MessageDigest digest = MessageDigest.getInstance(algorithm);
                byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
                String hexHash = HexFormat.of().formatHex(hash);

                String output = String.format("Hash result (%s):\n%s", algorithm, hexHash);

                return ToolResult.success(output, Map.of(
                        "algorithm", algorithm,
                        "hash", hexHash,
                        "length", hexHash.length()
                ));

            } catch (Exception e) {
                log.error("Hash calculation failed", e);
                return ToolResult.failure("Hash calculation failed");
            }
        });
    }

    @Override
    public String getCategory() {
        return "crypto";
    }
}
