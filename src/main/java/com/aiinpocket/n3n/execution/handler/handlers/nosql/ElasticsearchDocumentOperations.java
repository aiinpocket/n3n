package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Document operations for Elasticsearch: index, get, update, delete, bulk.
 */
final class ElasticsearchDocumentOperations {

    private ElasticsearchDocumentOperations() {}

    static NodeExecutionResult execute(
        ElasticsearchClient client,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "index" -> indexDocument(client, params, objectMapper);
            case "get" -> getDocument(client, params, objectMapper);
            case "update" -> updateDocument(client, params, objectMapper);
            case "delete" -> deleteDocument(client, params);
            case "bulk" -> bulk(client, params, objectMapper);
            default -> NodeExecutionResult.failure("Unknown document operation: " + operation);
        };
    }

    private static NodeExecutionResult indexDocument(
        ElasticsearchClient client,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String id = getParam(params, "id", "");
        String documentJson = getRequiredParam(params, "document");
        boolean refresh = getBoolParam(params, "refresh", false);

        Map<String, Object> document = objectMapper.readValue(documentJson, new TypeReference<>() {});

        IndexRequest.Builder<Map<String, Object>> request = new IndexRequest.Builder<Map<String, Object>>()
            .index(index)
            .document(document);

        if (!id.isEmpty()) {
            request.id(id);
        }
        if (refresh) {
            request.refresh(Refresh.True);
        }

        IndexResponse response = client.index(request.build());

        return NodeExecutionResult.success(Map.of(
            "id", response.id(),
            "result", response.result().jsonValue(),
            "version", response.version()
        ));
    }

    private static NodeExecutionResult getDocument(
        ElasticsearchClient client,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String id = getRequiredParam(params, "id");
        String includesJson = getParam(params, "sourceIncludes", "");
        String excludesJson = getParam(params, "sourceExcludes", "");

        GetRequest.Builder request = new GetRequest.Builder()
            .index(index)
            .id(id);

        if (!includesJson.isEmpty() || !excludesJson.isEmpty()) {
            request.sourceIncludes(parseStringList(includesJson, objectMapper));
            request.sourceExcludes(parseStringList(excludesJson, objectMapper));
        }

        GetResponse<Map> response = client.get(request.build(), Map.class);

        if (!response.found()) {
            return NodeExecutionResult.success(Map.of(
                "found", false,
                "id", id
            ));
        }

        return NodeExecutionResult.success(Map.of(
            "found", true,
            "id", response.id(),
            "version", response.version(),
            "document", response.source()
        ));
    }

    private static NodeExecutionResult updateDocument(
        ElasticsearchClient client,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String id = getRequiredParam(params, "id");
        String documentJson = getRequiredParam(params, "document");
        boolean refresh = getBoolParam(params, "refresh", false);
        boolean upsert = getBoolParam(params, "docAsUpsert", false);

        Map<String, Object> document = objectMapper.readValue(documentJson, new TypeReference<>() {});

        UpdateRequest.Builder<Map<String, Object>, Map<String, Object>> request =
            new UpdateRequest.Builder<Map<String, Object>, Map<String, Object>>()
                .index(index)
                .id(id)
                .doc(document);

        if (refresh) {
            request.refresh(Refresh.True);
        }
        if (upsert) {
            request.docAsUpsert(true);
        }

        UpdateResponse<Map<String, Object>> response = client.update(request.build(), (Class<Map<String, Object>>) (Class<?>) Map.class);

        return NodeExecutionResult.success(Map.of(
            "id", response.id(),
            "result", response.result().jsonValue(),
            "version", response.version()
        ));
    }

    private static NodeExecutionResult deleteDocument(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String id = getRequiredParam(params, "id");
        boolean refresh = getBoolParam(params, "refresh", false);

        DeleteRequest.Builder request = new DeleteRequest.Builder()
            .index(index)
            .id(id);

        if (refresh) {
            request.refresh(Refresh.True);
        }

        DeleteResponse response = client.delete(request.build());

        return NodeExecutionResult.success(Map.of(
            "id", response.id(),
            "result", response.result().jsonValue()
        ));
    }

    @SuppressWarnings("unchecked")
    private static NodeExecutionResult bulk(
        ElasticsearchClient client,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String defaultIndex = getParam(params, "index", "");
        String operationsJson = getRequiredParam(params, "operations");
        boolean refresh = getBoolParam(params, "refresh", false);

        List<Map<String, Object>> operations = objectMapper.readValue(operationsJson, new TypeReference<>() {});

        List<BulkOperation> bulkOps = new ArrayList<>();
        for (Map<String, Object> op : operations) {
            if (op.containsKey("index")) {
                Map<String, Object> indexOp = (Map<String, Object>) op.get("index");
                String idx = (String) indexOp.getOrDefault("_index", defaultIndex);
                String id = (String) indexOp.get("_id");
                Map<String, Object> doc = (Map<String, Object>) indexOp.get("document");

                BulkOperation.Builder builder = new BulkOperation.Builder();
                builder.index(i -> {
                    i.index(idx).document(doc);
                    if (id != null) i.id(id);
                    return i;
                });
                bulkOps.add(builder.build());
            } else if (op.containsKey("delete")) {
                Map<String, Object> deleteOp = (Map<String, Object>) op.get("delete");
                String idx = (String) deleteOp.getOrDefault("_index", defaultIndex);
                String id = (String) deleteOp.get("_id");

                BulkOperation.Builder builder = new BulkOperation.Builder();
                builder.delete(d -> d.index(idx).id(id));
                bulkOps.add(builder.build());
            }
        }

        BulkRequest.Builder request = new BulkRequest.Builder()
            .operations(bulkOps);

        if (refresh) {
            request.refresh(Refresh.True);
        }

        BulkResponse response = client.bulk(request.build());

        List<Map<String, Object>> items = new ArrayList<>();
        for (BulkResponseItem item : response.items()) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("id", item.id());
            itemMap.put("result", item.result());
            itemMap.put("status", item.status());
            if (item.error() != null) {
                itemMap.put("error", item.error().reason());
            }
            items.add(itemMap);
        }

        return NodeExecutionResult.success(Map.of(
            "took", response.took(),
            "errors", response.errors(),
            "items", items
        ));
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

    private static boolean getBoolParam(Map<String, Object> params, String name, boolean defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private static List<String> parseStringList(String json, ObjectMapper objectMapper) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
