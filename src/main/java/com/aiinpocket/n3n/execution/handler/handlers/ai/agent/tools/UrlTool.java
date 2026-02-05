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
 * URL 處理工具
 * 支援 URL 解析、編碼、解碼、建構
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
                URL 處理工具，支援多種操作：
                - parse: 解析 URL 成組件
                - encode: URL 編碼
                - decode: URL 解碼
                - build: 從組件建構 URL
                - extractParams: 提取查詢參數

                參數：
                - url: URL 字串（用於 parse, encode, decode, extractParams）
                - operation: 操作類型
                - scheme: 協定（用於 build）
                - host: 主機（用於 build）
                - port: 連接埠（用於 build）
                - path: 路徑（用於 build）
                - params: 查詢參數（用於 build，JSON 格式）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of(
                                "type", "string",
                                "description", "URL 字串"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "encode", "decode", "build", "extractParams"),
                                "description", "操作類型",
                                "default", "parse"
                        ),
                        "scheme", Map.of(
                                "type", "string",
                                "description", "協定（如 https）"
                        ),
                        "host", Map.of(
                                "type", "string",
                                "description", "主機"
                        ),
                        "port", Map.of(
                                "type", "integer",
                                "description", "連接埠"
                        ),
                        "path", Map.of(
                                "type", "string",
                                "description", "路徑"
                        ),
                        "params", Map.of(
                                "type", "string",
                                "description", "查詢參數（JSON 格式）"
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
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("URL operation failed", e);
                return ToolResult.failure("URL 操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult parseUrl(String url) {
        if (url == null || url.isBlank()) {
            return ToolResult.failure("URL 不能為空");
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
            sb.append("URL 解析結果：\n");
            sb.append(String.format("- 協定: %s\n", uri.getScheme()));
            sb.append(String.format("- 主機: %s\n", uri.getHost()));
            if (uri.getPort() != -1) {
                sb.append(String.format("- 連接埠: %d\n", uri.getPort()));
            }
            sb.append(String.format("- 路徑: %s\n", uri.getPath()));
            if (uri.getQuery() != null) {
                sb.append(String.format("- 查詢字串: %s\n", uri.getQuery()));
            }
            if (uri.getFragment() != null) {
                sb.append(String.format("- 片段: %s\n", uri.getFragment()));
            }

            return ToolResult.success(sb.toString(), components);
        } catch (Exception e) {
            return ToolResult.failure("無效的 URL: " + e.getMessage());
        }
    }

    private ToolResult encodeUrl(String text) {
        if (text == null || text.isEmpty()) {
            return ToolResult.failure("文字不能為空");
        }

        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return ToolResult.success(
                "URL 編碼結果：\n" + encoded,
                Map.of("encoded", encoded, "original", text)
        );
    }

    private ToolResult decodeUrl(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return ToolResult.failure("編碼字串不能為空");
        }

        try {
            String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            return ToolResult.success(
                    "URL 解碼結果：\n" + decoded,
                    Map.of("decoded", decoded, "original", encoded)
            );
        } catch (Exception e) {
            return ToolResult.failure("解碼失敗: " + e.getMessage());
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
                return ToolResult.failure("主機不能為空");
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
                    "URL 建構結果：\n" + url,
                    Map.of("url", url)
            );
        } catch (Exception e) {
            return ToolResult.failure("URL 建構失敗: " + e.getMessage());
        }
    }

    private ToolResult extractParams(String url) {
        if (url == null || url.isBlank()) {
            return ToolResult.failure("URL 不能為空");
        }

        try {
            URI uri = new URI(url);
            String query = uri.getQuery();

            if (query == null || query.isEmpty()) {
                return ToolResult.success("URL 沒有查詢參數", Map.of("params", Map.of()));
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
            sb.append(String.format("提取到 %d 個查詢參數：\n", params.size()));
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(String.format("- %s = %s\n", entry.getKey(), entry.getValue()));
            }

            return ToolResult.success(sb.toString(), Map.of("params", params, "count", params.size()));
        } catch (Exception e) {
            return ToolResult.failure("參數提取失敗: " + e.getMessage());
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
