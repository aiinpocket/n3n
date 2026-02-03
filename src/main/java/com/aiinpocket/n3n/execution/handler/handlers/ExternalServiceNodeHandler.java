package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import com.aiinpocket.n3n.service.ExternalServiceService;
import com.aiinpocket.n3n.service.dto.EndpointSchemaResponse;
import com.aiinpocket.n3n.service.entity.ExternalService;
import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import com.aiinpocket.n3n.service.repository.ExternalServiceRepository;
import com.aiinpocket.n3n.service.repository.ServiceEndpointRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Handler for External Service nodes.
 * Executes HTTP requests to registered external service endpoints.
 */
@Component
@Slf4j
public class ExternalServiceNodeHandler extends AbstractNodeHandler {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MAX_RESPONSE_SIZE = 10 * 1024 * 1024; // 10MB

    private final ExternalServiceRepository serviceRepository;
    private final ServiceEndpointRepository endpointRepository;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    public ExternalServiceNodeHandler(
            ExternalServiceRepository serviceRepository,
            ServiceEndpointRepository endpointRepository,
            ObjectMapper objectMapper) {
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return "externalService";
    }

    @Override
    public String getDisplayName() {
        return "External Service";
    }

    @Override
    public String getDescription() {
        return "Call an external service endpoint. Configure the service and endpoint in the node settings.";
    }

    @Override
    public String getCategory() {
        return "Integrations";
    }

    @Override
    public String getIcon() {
        return "api";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String serviceIdStr = getStringConfig(context, "serviceId", "");
        String endpointIdStr = getStringConfig(context, "endpointId", "");

        if (serviceIdStr.isEmpty() || endpointIdStr.isEmpty()) {
            return NodeExecutionResult.failure("Service and endpoint must be configured");
        }

        UUID serviceId;
        UUID endpointId;
        try {
            serviceId = UUID.fromString(serviceIdStr);
            endpointId = UUID.fromString(endpointIdStr);
        } catch (IllegalArgumentException e) {
            return NodeExecutionResult.failure("Invalid service or endpoint ID format");
        }

        // Fetch service and endpoint
        Optional<ExternalService> serviceOpt = serviceRepository.findByIdAndIsDeletedFalse(serviceId);
        if (serviceOpt.isEmpty()) {
            return NodeExecutionResult.failure("Service not found: " + serviceId);
        }

        Optional<ServiceEndpoint> endpointOpt = endpointRepository.findById(endpointId);
        if (endpointOpt.isEmpty()) {
            return NodeExecutionResult.failure("Endpoint not found: " + endpointId);
        }

        ExternalService service = serviceOpt.get();
        ServiceEndpoint endpoint = endpointOpt.get();

        if (!endpoint.getServiceId().equals(serviceId)) {
            return NodeExecutionResult.failure("Endpoint does not belong to the specified service");
        }

        // Build URL with path parameters
        String url = buildUrl(service.getBaseUrl(), endpoint.getPath(), context);

        // Add query parameters
        url = addQueryParams(url, context);

        log.info("Executing external service call: {} {} (service={}, endpoint={})",
                endpoint.getMethod(), url, service.getName(), endpoint.getName());

        int timeout = getIntConfig(context, "timeout", DEFAULT_TIMEOUT_SECONDS);
        if (timeout > MAX_TIMEOUT_SECONDS) {
            timeout = MAX_TIMEOUT_SECONDS;
        }

        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);

            // Add headers
            addHeaders(requestBuilder, context, service);

            // Add body for POST, PUT, PATCH
            String method = endpoint.getMethod().toUpperCase();
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

            try (Response response = client.newCall(request).execute()) {
                return processResponse(response, context, endpoint.getName());
            }

        } catch (IOException e) {
            log.error("External service call failed: {}", e.getMessage());
            return NodeExecutionResult.failure("HTTP request failed: " + e.getMessage());
        }
    }

    private String buildUrl(String baseUrl, String path, NodeExecutionContext context) {
        String fullPath = path;

        // Replace path parameters
        @SuppressWarnings("unchecked")
        Map<String, Object> pathParams = (Map<String, Object>) context.getNodeConfig().get("pathParams");
        if (pathParams != null) {
            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (fullPath.contains(placeholder) && entry.getValue() != null) {
                    fullPath = fullPath.replace(placeholder, entry.getValue().toString());
                }
            }
        }

        return baseUrl + fullPath;
    }

    private String addQueryParams(String url, NodeExecutionContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> queryParams = (Map<String, Object>) context.getNodeConfig().get("queryParams");
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");

        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            if (entry.getValue() != null) {
                sb.append(hasQuery ? "&" : "?");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                hasQuery = true;
            }
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void addHeaders(Request.Builder builder, NodeExecutionContext context, ExternalService service) {
        // Add custom headers from config
        Object headersConfig = context.getNodeConfig().get("headers");
        if (headersConfig instanceof Map) {
            Map<String, Object> headers = (Map<String, Object>) headersConfig;
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (entry.getValue() != null) {
                    builder.addHeader(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        // Add authentication header based on service auth config
        if (service.getAuthType() != null && service.getAuthConfig() != null) {
            String authType = service.getAuthType().toLowerCase();
            Map<String, Object> authConfig = service.getAuthConfig();

            switch (authType) {
                case "bearer" -> {
                    Object token = authConfig.get("token");
                    if (token != null) {
                        builder.addHeader("Authorization", "Bearer " + token);
                    }
                }
                case "api_key" -> {
                    Object header = authConfig.get("header");
                    Object value = authConfig.get("value");
                    if (header != null && value != null) {
                        builder.addHeader(header.toString(), value.toString());
                    }
                }
                case "basic" -> {
                    Object username = authConfig.get("username");
                    Object password = authConfig.get("password");
                    if (username != null && password != null) {
                        String credentials = username + ":" + password;
                        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                        builder.addHeader("Authorization", "Basic " + encoded);
                    }
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
        Object bodyConfig = context.getNodeConfig().get("requestBody");

        if (bodyConfig == null) {
            return RequestBody.create("", MediaType.parse(contentType));
        }

        String bodyString;
        if (bodyConfig instanceof String) {
            bodyString = (String) bodyConfig;
        } else {
            bodyString = objectMapper.writeValueAsString(bodyConfig);
        }

        return RequestBody.create(bodyString, MediaType.parse(contentType));
    }

    private NodeExecutionResult processResponse(Response response, NodeExecutionContext context, String endpointName) throws IOException {
        int statusCode = response.code();
        boolean successOnly = getBooleanConfig(context, "successOnly", false);

        if (successOnly && (statusCode < 200 || statusCode >= 300)) {
            return NodeExecutionResult.failure("HTTP request returned status " + statusCode);
        }

        ResponseBody responseBody = response.body();
        String bodyString = "";
        Object parsedBody = null;

        if (responseBody != null) {
            long contentLength = responseBody.contentLength();
            if (contentLength > MAX_RESPONSE_SIZE) {
                return NodeExecutionResult.failure("Response too large: " + contentLength + " bytes");
            }

            bodyString = responseBody.string();

            // Try to parse as JSON
            String contentTypeHeader = response.header("Content-Type", "");
            if (contentTypeHeader.contains("application/json") || contentTypeHeader.contains("text/json")) {
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

        log.info("External service {} returned status {}", endpointName, statusCode);

        return NodeExecutionResult.success(output);
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object serviceId = config.get("serviceId");
        Object endpointId = config.get("endpointId");

        if (serviceId == null || serviceId.toString().trim().isEmpty()) {
            return ValidationResult.invalid("serviceId", "Service ID is required");
        }

        if (endpointId == null || endpointId.toString().trim().isEmpty()) {
            return ValidationResult.invalid("endpointId", "Endpoint ID is required");
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        // Return base schema - actual schema is loaded dynamically per endpoint
        return Map.of(
                "type", "object",
                "required", List.of("serviceId", "endpointId"),
                "properties", Map.of(
                        "serviceId", Map.of(
                                "type", "string",
                                "title", "Service ID",
                                "description", "The external service to call"
                        ),
                        "endpointId", Map.of(
                                "type", "string",
                                "title", "Endpoint ID",
                                "description", "The specific endpoint to call"
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
                        )
                ),
                "x-dynamic-schema", true
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "input", "type", "any", "required", false,
                                "description", "Input data from previous node (can be used in expressions)")
                ),
                "outputs", List.of(
                        Map.of("name", "output", "type", "object", "description", "Response data"),
                        Map.of("name", "status", "type", "integer", "description", "HTTP status code"),
                        Map.of("name", "headers", "type", "object", "description", "Response headers")
                )
        );
    }
}
