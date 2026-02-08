package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * URL processing tool
 * Supports URL parsing, encoding, decoding, and building
 */
@Component
@Slf4j
public class UrlTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "url";
    }

    @Override
    public String getName() {
        return "URL Tool";
    }

    @Override
    public String getDescription() {
        return """
                URL processing tool with multiple operations:
                - parse: Parse URL into components
                - encode: URL encode
                - decode: URL decode
                - build: Build URL from components
                - extractParams: Extract query parameters

                Parameters:
                - url: URL string (for parse, encode, decode, extractParams)
                - operation: Operation type
                - scheme: Protocol (for build)
                - host: Host (for build)
                - port: Port (for build)
                - path: Path (for build)
                - params: Query parameters (for build, JSON format)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of(
                                "type", "string",
                                "description", "URL string"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "encode", "decode", "build", "extractParams"),
                                "description", "Operation type",
                                "default", "parse"
                        ),
                        "scheme", Map.of(
                                "type", "string",
                                "description", "Protocol (e.g. https)"
                        ),
                        "host", Map.of(
                                "type", "string",
                                "description", "Host"
                        ),
                        "port", Map.of(
                                "type", "integer",
                                "description", "Port"
                        ),
                        "path", Map.of(
                                "type", "string",
                                "description", "Path"
                        ),
                        "params", Map.of(
                                "type", "string",
                                "description", "Query parameters (JSON format)"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String operation = (String) parameters.getOrDefault("operation", "parse");

                return switch (operation) {
                    case "parse" -> parseUrl((String) parameters.get("url"));
                    case "encode" -> encodeUrl((String) parameters.get("url"));
                    case "decode" -> decodeUrl((String) parameters.get("url"));
                    case "build" -> buildUrl(parameters);
                    case "extractParams" -> extractParams((String) parameters.get("url"));
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("URL operation failed", e);
                return ToolResult.failure("URL operation failed");
            }
        });
    }

    private ToolResult parseUrl(String url) {
        if (url == null || url.isBlank()) {
            return ToolResult.failure("URL cannot be empty");
        }

        try {
            URI uri = new URI(url);

            Map<String, Object> components = new LinkedHashMap<>();
            components.put("scheme", uri.getScheme());
            components.put("host", uri.getHost());
            components.put("port", uri.getPort() == -1 ? null : uri.getPort());
            components.put("path", uri.getPath());
            components.put("query", uri.getQuery());
            components.put("fragment", uri.getFragment());
            components.put("userInfo", uri.getUserInfo());

            StringBuilder sb = new StringBuilder();
            sb.append("URL parse result:\n");
            sb.append(String.format("- Scheme: %s\n", uri.getScheme()));
            sb.append(String.format("- Host: %s\n", uri.getHost()));
            if (uri.getPort() != -1) {
                sb.append(String.format("- Port: %d\n", uri.getPort()));
            }
            sb.append(String.format("- Path: %s\n", uri.getPath()));
            if (uri.getQuery() != null) {
                sb.append(String.format("- Query: %s\n", uri.getQuery()));
            }
            if (uri.getFragment() != null) {
                sb.append(String.format("- Fragment: %s\n", uri.getFragment()));
            }

            return ToolResult.success(sb.toString(), components);
        } catch (Exception e) {
            return ToolResult.failure("Invalid URL");
        }
    }

    private ToolResult encodeUrl(String text) {
        if (text == null || text.isEmpty()) {
            return ToolResult.failure("Text cannot be empty");
        }

        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return ToolResult.success(
                "URL encode result:\n" + encoded,
                Map.of("encoded", encoded, "original", text)
        );
    }

    private ToolResult decodeUrl(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return ToolResult.failure("Encoded string cannot be empty");
        }

        try {
            String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            return ToolResult.success(
                    "URL decode result:\n" + decoded,
                    Map.of("decoded", decoded, "original", encoded)
            );
        } catch (Exception e) {
            return ToolResult.failure("Decoding failed");
        }
    }

    private ToolResult buildUrl(Map<String, Object> parameters) {
        try {
            String scheme = (String) parameters.getOrDefault("scheme", "https");
            String host = (String) parameters.get("host");
            Integer port = parameters.containsKey("port") ? ((Number) parameters.get("port")).intValue() : null;
            String path = (String) parameters.getOrDefault("path", "/");
            String paramsJson = (String) parameters.get("params");

            if (host == null || host.isBlank()) {
                return ToolResult.failure("Host cannot be empty");
            }

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);

            if (port != null && port > 0) {
                sb.append(":").append(port);
            }

            if (path != null && !path.isEmpty()) {
                if (!path.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(path);
            }

            if (paramsJson != null && !paramsJson.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> params = mapper.readValue(paramsJson, Map.class);

                if (!params.isEmpty()) {
                    sb.append("?");
                    StringJoiner joiner = new StringJoiner("&");
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                        String value = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
                        joiner.add(key + "=" + value);
                    }
                    sb.append(joiner);
                }
            }

            String url = sb.toString();
            return ToolResult.success(
                    "URL build result:\n" + url,
                    Map.of("url", url)
            );
        } catch (Exception e) {
            return ToolResult.failure("URL build failed");
        }
    }

    private ToolResult extractParams(String url) {
        if (url == null || url.isBlank()) {
            return ToolResult.failure("URL cannot be empty");
        }

        try {
            URI uri = new URI(url);
            String query = uri.getQuery();

            if (query == null || query.isEmpty()) {
                return ToolResult.success("URL has no query parameters", Map.of("params", Map.of()));
            }

            Map<String, String> params = new LinkedHashMap<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = idx < pair.length() - 1
                            ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                            : "";
                    params.put(key, value);
                } else if (!pair.isEmpty()) {
                    params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Extracted %d query parameters:\n", params.size()));
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(String.format("- %s = %s\n", entry.getKey(), entry.getValue()));
            }

            return ToolResult.success(sb.toString(), Map.of("params", params, "count", params.size()));
        } catch (Exception e) {
            return ToolResult.failure("Parameter extraction failed");
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
