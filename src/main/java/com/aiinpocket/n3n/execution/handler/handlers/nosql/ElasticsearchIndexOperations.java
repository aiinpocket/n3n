package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.*;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;

import java.io.StringReader;
import java.util.*;

/**
 * Index management operations for Elasticsearch: create, delete, exists, getMapping, putMapping, refresh, list.
 */
final class ElasticsearchIndexOperations {

    private ElasticsearchIndexOperations() {}

    static NodeExecutionResult execute(
        ElasticsearchClient client,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> createIndex(client, params);
            case "delete" -> deleteIndex(client, params);
            case "exists" -> indexExists(client, params);
            case "getMapping" -> getMapping(client, params);
            case "putMapping" -> putMapping(client, params);
            case "refresh" -> refreshIndex(client, params);
            case "list" -> listIndices(client, params);
            default -> NodeExecutionResult.failure("Unknown index operation: " + operation);
        };
    }

    private static NodeExecutionResult createIndex(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String settingsJson = getParam(params, "settings", "");
        String mappingsJson = getParam(params, "mappings", "");

        CreateIndexRequest.Builder request = new CreateIndexRequest.Builder()
            .index(index);

        if (!settingsJson.isEmpty()) {
            request.settings(s -> s.withJson(new StringReader(settingsJson)));
        }

        if (!mappingsJson.isEmpty()) {
            request.mappings(m -> m.withJson(new StringReader(mappingsJson)));
        }

        CreateIndexResponse response = client.indices().create(request.build());

        return NodeExecutionResult.success(Map.of(
            "acknowledged", response.acknowledged(),
            "index", response.index()
        ));
    }

    private static NodeExecutionResult deleteIndex(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");

        DeleteIndexRequest request = new DeleteIndexRequest.Builder()
            .index(index)
            .build();

        DeleteIndexResponse response = client.indices().delete(request);

        return NodeExecutionResult.success(Map.of(
            "acknowledged", response.acknowledged()
        ));
    }

    private static NodeExecutionResult indexExists(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");

        co.elastic.clients.elasticsearch.indices.ExistsRequest request =
            new co.elastic.clients.elasticsearch.indices.ExistsRequest.Builder()
                .index(index)
                .build();

        boolean exists = client.indices().exists(request).value();

        return NodeExecutionResult.success(Map.of("exists", exists));
    }

    private static NodeExecutionResult getMapping(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");

        GetMappingRequest request = new GetMappingRequest.Builder()
            .index(index)
            .build();

        GetMappingResponse response = client.indices().getMapping(request);

        Map<String, Object> mappings = new LinkedHashMap<>();
        response.result().forEach((idx, mapping) -> {
            mappings.put(idx, mapping.mappings());
        });

        return NodeExecutionResult.success(Map.of("mapping", mappings));
    }

    private static NodeExecutionResult putMapping(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String mappingsJson = getRequiredParam(params, "mappings");

        PutMappingRequest request = new PutMappingRequest.Builder()
            .index(index)
            .withJson(new StringReader(mappingsJson))
            .build();

        PutMappingResponse response = client.indices().putMapping(request);

        return NodeExecutionResult.success(Map.of(
            "acknowledged", response.acknowledged()
        ));
    }

    private static NodeExecutionResult refreshIndex(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");

        RefreshRequest request = new RefreshRequest.Builder()
            .index(index)
            .build();

        RefreshResponse response = client.indices().refresh(request);

        return NodeExecutionResult.success(Map.of(
            "successful", response.shards().successful(),
            "failed", response.shards().failed(),
            "total", response.shards().total()
        ));
    }

    private static NodeExecutionResult listIndices(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String pattern = getParam(params, "pattern", "*");

        GetIndexRequest request = new GetIndexRequest.Builder()
            .index(pattern)
            .build();

        GetIndexResponse response = client.indices().get(request);

        List<String> indices = new ArrayList<>(response.result().keySet());
        Collections.sort(indices);

        return NodeExecutionResult.success(Map.of("indices", indices));
    }

    // ==================== Parameter Helpers ====================

    private static String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        return value.toString();
    }

    private static String getParam(Map<String, Object> params, String name, String defaultValue) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            return defaultValue;
        }
        return value.toString();
    }
}
