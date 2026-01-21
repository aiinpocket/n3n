package com.aiinpocket.n3n.service.openapi;

import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
@Slf4j
public class OpenApiParserService {

    private final RestClient restClient;

    public OpenApiParserService() {
        this.restClient = RestClient.create();
    }

    public List<ServiceEndpoint> parseFromUrl(String baseUrl, String schemaPath, UUID serviceId) {
        String fullUrl = baseUrl + schemaPath;
        log.info("Fetching OpenAPI schema from: {}", fullUrl);

        try {
            String schemaContent = restClient.get()
                    .uri(fullUrl)
                    .retrieve()
                    .body(String.class);

            return parseFromContent(schemaContent, serviceId);
        } catch (Exception e) {
            log.error("Failed to fetch OpenAPI schema from {}: {}", fullUrl, e.getMessage());
            throw new RuntimeException("Failed to fetch OpenAPI schema: " + e.getMessage(), e);
        }
    }

    public List<ServiceEndpoint> parseFromContent(String content, UUID serviceId) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, options);

        if (result.getOpenAPI() == null) {
            String errors = result.getMessages() != null ? String.join(", ", result.getMessages()) : "Unknown error";
            log.error("Failed to parse OpenAPI document: {}", errors);
            throw new RuntimeException("Failed to parse OpenAPI document: " + errors);
        }

        OpenAPI openApi = result.getOpenAPI();
        List<ServiceEndpoint> endpoints = new ArrayList<>();

        if (openApi.getPaths() == null) {
            log.warn("No paths found in OpenAPI document");
            return endpoints;
        }

        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            extractOperations(path, pathItem, serviceId, endpoints);
        }

        log.info("Parsed {} endpoints from OpenAPI document", endpoints.size());
        return endpoints;
    }

    private void extractOperations(String path, PathItem pathItem, UUID serviceId, List<ServiceEndpoint> endpoints) {
        Map<String, Operation> operations = new LinkedHashMap<>();
        if (pathItem.getGet() != null) operations.put("GET", pathItem.getGet());
        if (pathItem.getPost() != null) operations.put("POST", pathItem.getPost());
        if (pathItem.getPut() != null) operations.put("PUT", pathItem.getPut());
        if (pathItem.getPatch() != null) operations.put("PATCH", pathItem.getPatch());
        if (pathItem.getDelete() != null) operations.put("DELETE", pathItem.getDelete());
        if (pathItem.getHead() != null) operations.put("HEAD", pathItem.getHead());
        if (pathItem.getOptions() != null) operations.put("OPTIONS", pathItem.getOptions());

        for (Map.Entry<String, Operation> opEntry : operations.entrySet()) {
            String method = opEntry.getKey();
            Operation operation = opEntry.getValue();

            ServiceEndpoint endpoint = createEndpoint(path, method, operation, serviceId);
            endpoints.add(endpoint);
        }
    }

    private ServiceEndpoint createEndpoint(String path, String method, Operation operation, UUID serviceId) {
        String name = operation.getOperationId() != null ?
                operation.getOperationId() :
                method.toLowerCase() + path.replace("/", "_").replace("{", "").replace("}", "");

        Map<String, Object> pathParams = new HashMap<>();
        Map<String, Object> queryParams = new HashMap<>();
        Map<String, Object> requestBody = null;
        Map<String, Object> responseSchema = null;
        List<String> tags = operation.getTags();

        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                Map<String, Object> paramSchema = schemaToMap(param.getSchema());
                paramSchema.put("description", param.getDescription());
                paramSchema.put("required", param.getRequired());

                if ("path".equals(param.getIn())) {
                    pathParams.put(param.getName(), paramSchema);
                } else if ("query".equals(param.getIn())) {
                    queryParams.put(param.getName(), paramSchema);
                }
            }
        }

        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            requestBody = extractContentSchema(operation.getRequestBody().getContent());
        }

        if (operation.getResponses() != null) {
            ApiResponse successResponse = operation.getResponses().get("200");
            if (successResponse == null) {
                successResponse = operation.getResponses().get("201");
            }
            if (successResponse == null) {
                successResponse = operation.getResponses().get("default");
            }
            if (successResponse != null && successResponse.getContent() != null) {
                responseSchema = extractContentSchema(successResponse.getContent());
            }
        }

        return ServiceEndpoint.builder()
                .serviceId(serviceId)
                .name(name)
                .description(operation.getSummary() != null ? operation.getSummary() : operation.getDescription())
                .method(method)
                .path(path)
                .pathParams(pathParams.isEmpty() ? null : wrapAsJsonSchema(pathParams))
                .queryParams(queryParams.isEmpty() ? null : wrapAsJsonSchema(queryParams))
                .requestBody(requestBody)
                .responseSchema(responseSchema)
                .tags(tags)
                .isEnabled(true)
                .build();
    }

    private Map<String, Object> extractContentSchema(Content content) {
        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.values().stream().findFirst().orElse(null);
        }
        if (mediaType != null && mediaType.getSchema() != null) {
            return schemaToMap(mediaType.getSchema());
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private Map<String, Object> schemaToMap(Schema<?> schema) {
        if (schema == null) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        if (schema.getType() != null) {
            result.put("type", schema.getType());
        }
        if (schema.getFormat() != null) {
            result.put("format", schema.getFormat());
        }
        if (schema.getDescription() != null) {
            result.put("description", schema.getDescription());
        }
        if (schema.getDefault() != null) {
            result.put("default", schema.getDefault());
        }
        if (schema.getEnum() != null) {
            result.put("enum", schema.getEnum());
        }
        if (schema.getMinimum() != null) {
            result.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            result.put("maximum", schema.getMaximum());
        }
        if (schema.getMinLength() != null) {
            result.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            result.put("maxLength", schema.getMaxLength());
        }

        if (schema.getProperties() != null) {
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                properties.put(entry.getKey(), schemaToMap(entry.getValue()));
            }
            result.put("properties", properties);
        }

        if (schema.getRequired() != null) {
            result.put("required", schema.getRequired());
        }

        if (schema.getItems() != null) {
            result.put("items", schemaToMap(schema.getItems()));
        }

        return result;
    }

    private Map<String, Object> wrapAsJsonSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);

        List<String> required = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                if (Boolean.TRUE.equals(prop.get("required"))) {
                    required.add(entry.getKey());
                }
            }
        }
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }
}
