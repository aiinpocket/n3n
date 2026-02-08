package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handler for URL parsing and manipulation.
 * Parses URLs, extracts components, modifies query parameters,
 * and encodes/decodes URL components.
 */
@Component
@Slf4j
public class UrlParserNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "urlParser";
    }

    @Override
    public String getDisplayName() {
        return "URL Parser";
    }

    @Override
    public String getDescription() {
        return "Parse, build, and manipulate URLs.";
    }

    @Override
    public String getCategory() {
        return NodeCategory.DATA_TRANSFORM;
    }

    @Override
    public String getIcon() {
        return "link";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "parse");

        try {
            return switch (operation) {
                case "parse" -> doParse(context);
                case "build" -> doBuild(context);
                case "encode" -> doEncode(context);
                case "decode" -> doDecode(context);
                case "addParams" -> doAddParams(context);
                case "removeParams" -> doRemoveParams(context);
                case "extractDomain" -> doExtractDomain(context);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            return NodeExecutionResult.failure("URL operation failed: " + e.getMessage());
        }
    }

    private NodeExecutionResult doParse(NodeExecutionContext context) throws Exception {
        String urlStr = getUrlInput(context);
        if (urlStr == null || urlStr.isBlank()) {
            return NodeExecutionResult.failure("URL is required.");
        }

        URI uri = new URI(urlStr);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("url", urlStr);
        output.put("protocol", uri.getScheme());
        output.put("host", uri.getHost());
        output.put("port", uri.getPort() != -1 ? uri.getPort() : null);
        output.put("path", uri.getPath());
        output.put("query", uri.getQuery());
        output.put("fragment", uri.getFragment());
        output.put("authority", uri.getAuthority());

        // Parse query parameters
        Map<String, List<String>> params = parseQueryString(uri.getQuery());
        output.put("queryParams", params);

        // Simplified flat params (last value wins)
        Map<String, String> flatParams = new LinkedHashMap<>();
        params.forEach((k, v) -> flatParams.put(k, v.get(v.size() - 1)));
        output.put("params", flatParams);

        return NodeExecutionResult.success(output);
    }

    private NodeExecutionResult doBuild(NodeExecutionContext context) throws Exception {
        String protocol = getStringConfig(context, "protocol", "https");
        String host = getStringConfig(context, "host", "");
        int port = getIntConfig(context, "port", -1);
        String path = getStringConfig(context, "path", "");
        String fragment = getStringConfig(context, "fragment", "");
        Map<String, Object> queryParams = getMapConfig(context, "queryParams");

        if (host.isBlank()) {
            return NodeExecutionResult.failure("Host is required to build a URL.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://").append(host);
        if (port > 0) sb.append(":").append(port);
        if (!path.isEmpty()) {
            if (!path.startsWith("/")) sb.append("/");
            sb.append(path);
        }
        if (!queryParams.isEmpty()) {
            sb.append("?");
            StringJoiner joiner = new StringJoiner("&");
            queryParams.forEach((k, v) -> {
                joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" +
                           URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
            });
            sb.append(joiner);
        }
        if (!fragment.isEmpty()) {
            sb.append("#").append(fragment);
        }

        return NodeExecutionResult.success(Map.of("url", sb.toString()));
    }

    private NodeExecutionResult doEncode(NodeExecutionContext context) {
        String input = getStringConfig(context, "input", "");
        if (input.isBlank() && context.getInputData() != null) {
            Object data = context.getInputData().get("input");
            if (data != null) input = data.toString();
        }
        if (input.isBlank()) return NodeExecutionResult.failure("Input is required.");

        String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
        return NodeExecutionResult.success(Map.of("result", encoded, "input", input));
    }

    private NodeExecutionResult doDecode(NodeExecutionContext context) {
        String input = getStringConfig(context, "input", "");
        if (input.isBlank() && context.getInputData() != null) {
            Object data = context.getInputData().get("input");
            if (data != null) input = data.toString();
        }
        if (input.isBlank()) return NodeExecutionResult.failure("Input is required.");

        String decoded = URLDecoder.decode(input, StandardCharsets.UTF_8);
        return NodeExecutionResult.success(Map.of("result", decoded, "input", input));
    }

    private NodeExecutionResult doAddParams(NodeExecutionContext context) throws Exception {
        String urlStr = getUrlInput(context);
        if (urlStr == null || urlStr.isBlank()) return NodeExecutionResult.failure("URL is required.");

        Map<String, Object> newParams = getMapConfig(context, "queryParams");
        URI uri = new URI(urlStr);
        Map<String, List<String>> existingParams = parseQueryString(uri.getQuery());

        newParams.forEach((k, v) -> existingParams.put(k, List.of(v.toString())));

        String newQuery = buildQueryString(existingParams);
        URI newUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
                            newQuery, uri.getFragment());

        return NodeExecutionResult.success(Map.of("url", newUri.toString()));
    }

    private NodeExecutionResult doRemoveParams(NodeExecutionContext context) throws Exception {
        String urlStr = getUrlInput(context);
        if (urlStr == null || urlStr.isBlank()) return NodeExecutionResult.failure("URL is required.");

        String keysStr = getStringConfig(context, "keys", "");
        List<String> keysToRemove = Arrays.asList(keysStr.split(","));

        URI uri = new URI(urlStr);
        Map<String, List<String>> params = parseQueryString(uri.getQuery());
        keysToRemove.forEach(k -> params.remove(k.trim()));

        String newQuery = params.isEmpty() ? null : buildQueryString(params);
        URI newUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
                            newQuery, uri.getFragment());

        return NodeExecutionResult.success(Map.of("url", newUri.toString()));
    }

    private NodeExecutionResult doExtractDomain(NodeExecutionContext context) throws Exception {
        String urlStr = getUrlInput(context);
        if (urlStr == null || urlStr.isBlank()) return NodeExecutionResult.failure("URL is required.");

        URI uri = new URI(urlStr);
        String host = uri.getHost();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("host", host);

        if (host != null) {
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                output.put("tld", parts[parts.length - 1]);
                output.put("domain", parts[parts.length - 2] + "." + parts[parts.length - 1]);
                if (parts.length > 2) {
                    output.put("subdomain", String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2)));
                }
            }
        }

        return NodeExecutionResult.success(output);
    }

    private String getUrlInput(NodeExecutionContext context) {
        String url = getStringConfig(context, "url", "");
        if (url.isBlank() && context.getInputData() != null) {
            Object data = context.getInputData().get("url");
            if (data == null) data = context.getInputData().get("input");
            if (data != null) url = data.toString();
        }
        return url;
    }

    private Map<String, List<String>> parseQueryString(String query) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String param : query.split("&")) {
            int idx = param.indexOf('=');
            String key = idx > 0 ? URLDecoder.decode(param.substring(0, idx), StandardCharsets.UTF_8)
                                 : URLDecoder.decode(param, StandardCharsets.UTF_8);
            String value = idx > 0 ? URLDecoder.decode(param.substring(idx + 1), StandardCharsets.UTF_8) : "";
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private String buildQueryString(Map<String, List<String>> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((k, values) -> {
            for (String v : values) {
                joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" +
                           URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        });
        return joiner.toString();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string", "title", "Operation",
            "enum", List.of("parse", "build", "encode", "decode", "addParams", "removeParams", "extractDomain"),
            "default", "parse"
        ));
        properties.put("url", Map.of("type", "string", "title", "URL"));
        properties.put("input", Map.of("type", "string", "title", "Input"));
        properties.put("protocol", Map.of("type", "string", "title", "Protocol", "default", "https"));
        properties.put("host", Map.of("type", "string", "title", "Host"));
        properties.put("port", Map.of("type", "integer", "title", "Port", "default", -1));
        properties.put("path", Map.of("type", "string", "title", "Path"));
        properties.put("fragment", Map.of("type", "string", "title", "Fragment"));
        properties.put("queryParams", Map.of(
            "type", "object", "title", "Query Parameters",
            "additionalProperties", Map.of("type", "string")
        ));
        properties.put("keys", Map.of("type", "string", "title", "Keys to Remove"));

        return Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("operation")
        );
    }
}
