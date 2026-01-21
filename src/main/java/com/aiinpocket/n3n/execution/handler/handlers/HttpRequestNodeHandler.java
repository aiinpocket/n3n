package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Handler for HTTP Request nodes.
 * Supports GET, POST, PUT, PATCH, DELETE methods with customizable headers and body.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpRequestNodeHandler extends AbstractNodeHandler {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MAX_RESPONSE_SIZE = 10 * 1024 * 1024; // 10MB

    private final ObjectMapper objectMapper;

    // OkHttpClient with sensible defaults
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build();

    @Override
    public String getType() {
        return "httpRequest";
    }

    @Override
    public String getDisplayName() {
        return "HTTP Request";
    }

    @Override
    public String getDescription() {
        return "Make HTTP requests to external APIs and services.";
    }

    @Override
    public String getCategory() {
        return "Network";
    }

    @Override
    public String getIcon() {
        return "globe";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String url = getStringConfig(context, "url", "");
        String method = getStringConfig(context, "method", "GET").toUpperCase();
        int timeout = getIntConfig(context, "timeout", DEFAULT_TIMEOUT_SECONDS);

        if (url.isEmpty()) {
            return NodeExecutionResult.failure("URL is required");
        }

        // Validate URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return NodeExecutionResult.failure("URL must start with http:// or https://");
        }

        // Cap timeout
        if (timeout > MAX_TIMEOUT_SECONDS) {
            timeout = MAX_TIMEOUT_SECONDS;
        }

        try {
            // Build request
            Request.Builder requestBuilder = new Request.Builder()
                .url(url);

            // Add headers
            addHeaders(requestBuilder, context);

            // Add body for POST, PUT, PATCH
            if (Set.of("POST", "PUT", "PATCH").contains(method)) {
                RequestBody body = buildRequestBody(context);
                requestBuilder.method(method, body);
            } else if ("DELETE".equals(method)) {
                requestBuilder.delete();
            } else {
                requestBuilder.get();
            }

            // Create client with custom timeout
            OkHttpClient client = httpClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build();

            Request request = requestBuilder.build();
            log.debug("HTTP {} {} with timeout {}s", method, url, timeout);

            try (Response response = client.newCall(request).execute()) {
                return processResponse(response, context);
            }

        } catch (IOException e) {
            log.error("HTTP request failed: {}", e.getMessage());
            return NodeExecutionResult.failure("HTTP request failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void addHeaders(Request.Builder builder, NodeExecutionContext context) {
        Object headersConfig = context.getNodeConfig().get("headers");

        if (headersConfig instanceof Map) {
            Map<String, Object> headers = (Map<String, Object>) headersConfig;
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (entry.getValue() != null) {
                    builder.addHeader(entry.getKey(), entry.getValue().toString());
                }
            }
        } else if (headersConfig instanceof List) {
            // Support array of {name, value} objects
            List<Map<String, String>> headers = (List<Map<String, String>>) headersConfig;
            for (Map<String, String> header : headers) {
                String name = header.get("name");
                String value = header.get("value");
                if (name != null && value != null) {
                    builder.addHeader(name, value);
                }
            }
        }

        // Add default User-Agent if not specified
        if (builder.build().header("User-Agent") == null) {
            builder.addHeader("User-Agent", "n3n-workflow/1.0");
        }
    }

    private RequestBody buildRequestBody(NodeExecutionContext context) throws JsonProcessingException {
        String contentType = getStringConfig(context, "contentType", "application/json");
        Object bodyConfig = context.getNodeConfig().get("body");

        if (bodyConfig == null) {
            return RequestBody.create("", MediaType.parse(contentType));
        }

        String bodyString;
        if (bodyConfig instanceof String) {
            bodyString = (String) bodyConfig;
        } else {
            // Convert object to JSON
            bodyString = objectMapper.writeValueAsString(bodyConfig);
        }

        return RequestBody.create(bodyString, MediaType.parse(contentType));
    }

    private NodeExecutionResult processResponse(Response response, NodeExecutionContext context) throws IOException {
        int statusCode = response.code();
        boolean successOnly = getBooleanConfig(context, "successOnly", false);

        // Check if we should fail on non-2xx status
        if (successOnly && (statusCode < 200 || statusCode >= 300)) {
            return NodeExecutionResult.failure("HTTP request returned status " + statusCode);
        }

        // Read response body
        ResponseBody responseBody = response.body();
        String bodyString = "";
        Object parsedBody = null;

        if (responseBody != null) {
            // Check content length
            long contentLength = responseBody.contentLength();
            if (contentLength > MAX_RESPONSE_SIZE) {
                return NodeExecutionResult.failure("Response too large: " + contentLength + " bytes");
            }

            bodyString = responseBody.string();

            // Try to parse as JSON
            String contentType = response.header("Content-Type", "");
            if (contentType.contains("application/json") || contentType.contains("text/json")) {
                try {
                    parsedBody = objectMapper.readValue(bodyString, Object.class);
                } catch (JsonProcessingException e) {
                    log.debug("Response is not valid JSON, treating as string");
                    parsedBody = bodyString;
                }
            } else {
                parsedBody = bodyString;
            }
        }

        // Build response headers map
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        for (String name : response.headers().names()) {
            responseHeaders.put(name, response.header(name));
        }

        // Build output
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", statusCode);
        output.put("statusText", response.message());
        output.put("headers", responseHeaders);
        output.put("data", parsedBody);

        // Include raw body if requested
        if (getBooleanConfig(context, "includeRawBody", false)) {
            output.put("body", bodyString);
        }

        return NodeExecutionResult.success(output);
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object url = config.get("url");
        if (url == null || url.toString().trim().isEmpty()) {
            return ValidationResult.invalid("url", "URL is required");
        }

        String urlStr = url.toString().trim();
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            return ValidationResult.invalid("url", "URL must start with http:// or https://");
        }

        Object method = config.get("method");
        if (method != null) {
            String methodStr = method.toString().toUpperCase();
            if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(methodStr)) {
                return ValidationResult.invalid("method", "Invalid HTTP method: " + methodStr);
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("url"),
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "title", "URL",
                    "description", "The URL to make the request to",
                    "format", "uri"
                ),
                "method", Map.of(
                    "type", "string",
                    "title", "Method",
                    "enum", List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"),
                    "default", "GET"
                ),
                "headers", Map.of(
                    "type", "object",
                    "title", "Headers",
                    "description", "HTTP headers to send with the request",
                    "additionalProperties", Map.of("type", "string")
                ),
                "body", Map.of(
                    "type", "object",
                    "title", "Body",
                    "description", "Request body (for POST, PUT, PATCH)"
                ),
                "contentType", Map.of(
                    "type", "string",
                    "title", "Content Type",
                    "default", "application/json",
                    "enum", List.of("application/json", "application/x-www-form-urlencoded", "text/plain", "multipart/form-data")
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "title", "Timeout (seconds)",
                    "default", 30,
                    "minimum", 1,
                    "maximum", 300
                ),
                "successOnly", Map.of(
                    "type", "boolean",
                    "title", "Fail on Error Status",
                    "default", false,
                    "description", "Fail if HTTP status is not 2xx"
                ),
                "includeRawBody", Map.of(
                    "type", "boolean",
                    "title", "Include Raw Body",
                    "default", false,
                    "description", "Include the raw response body string in output"
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
