package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.StringReader;
import java.util.*;

/**
 * Search operations for Elasticsearch: query, count, deleteByQuery, updateByQuery.
 */
final class ElasticsearchSearchOperations {

    private ElasticsearchSearchOperations() {}

    static NodeExecutionResult execute(
        ElasticsearchClient client,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "query" -> search(client, params, objectMapper);
            case "count" -> count(client, params);
            case "deleteByQuery" -> deleteByQuery(client, params);
            case "updateByQuery" -> updateByQuery(client, params, objectMapper);
            default -> NodeExecutionResult.failure("Unknown search operation: " + operation);
        };
    }

    private static NodeExecutionResult search(
        ElasticsearchClient client,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String queryJson = getParam(params, "query", "");
        String sortJson = getParam(params, "sort", "");
        String aggsJson = getParam(params, "aggs", "");
        String includesJson = getParam(params, "sourceIncludes", "");
        int from = getIntParam(params, "from", 0);
        int size = getIntParam(params, "size", 10);

        SearchRequest.Builder request = new SearchRequest.Builder()
            .index(index)
            .from(from)
            .size(size);

        if (!queryJson.isEmpty()) {
            request.query(q -> q.withJson(new StringReader(queryJson)));
        }

        if (!sortJson.isEmpty()) {
            // Parse sort JSON and apply
            List<Map<String, Object>> sorts = objectMapper.readValue(sortJson, new TypeReference<>() {});
            for (Map<String, Object> sortItem : sorts) {
                for (Map.Entry<String, Object> entry : sortItem.entrySet()) {
                    String field = entry.getKey();
                    String order = entry.getValue().toString();
                    request.sort(s -> s.field(f -> f.field(field).order(
                        "desc".equalsIgnoreCase(order) ?
                            co.elastic.clients.elasticsearch._types.SortOrder.Desc :
                            co.elastic.clients.elasticsearch._types.SortOrder.Asc
                    )));
                }
            }
        }

        if (!aggsJson.isEmpty()) {
            // Apply aggregations using withJson
            Map<String, Object> aggsMap = objectMapper.readValue(aggsJson, new TypeReference<>() {});
            for (String aggName : aggsMap.keySet()) {
                String aggJsonStr = objectMapper.writeValueAsString(Map.of(aggName, aggsMap.get(aggName)));
                // Note: Simplified aggregation handling
            }
        }

        if (!includesJson.isEmpty()) {
            List<String> includes = parseStringList(includesJson, objectMapper);
            request.source(s -> s.filter(f -> f.includes(includes)));
        }

        SearchResponse<Map> response = client.search(request.build(), Map.class);

        List<Map<String, Object>> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> hitMap = new LinkedHashMap<>();
            hitMap.put("_id", hit.id());
            hitMap.put("_score", hit.score());
            hitMap.put("_source", hit.source());
            hits.add(hitMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hits", hits);
        result.put("total", response.hits().total() != null ? response.hits().total().value() : hits.size());
        result.put("maxScore", response.hits().maxScore());

        if (response.aggregations() != null && !response.aggregations().isEmpty()) {
            result.put("aggregations", response.aggregations());
        }

        return NodeExecutionResult.success(result);
    }

    private static NodeExecutionResult count(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String queryJson = getParam(params, "query", "");

        CountRequest.Builder request = new CountRequest.Builder()
            .index(index);

        if (!queryJson.isEmpty()) {
            request.query(q -> q.withJson(new StringReader(queryJson)));
        }

        CountResponse response = client.count(request.build());

        return NodeExecutionResult.success(Map.of("count", response.count()));
    }

    private static NodeExecutionResult deleteByQuery(
        ElasticsearchClient client,
        Map<String, Object> params
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String queryJson = getRequiredParam(params, "query");
        boolean refresh = getBoolParam(params, "refresh", false);

        DeleteByQueryRequest.Builder request = new DeleteByQueryRequest.Builder()
            .index(index)
            .query(q -> q.withJson(new StringReader(queryJson)));

        if (refresh) {
            request.refresh(true);
        }

        DeleteByQueryResponse response = client.deleteByQuery(request.build());

        return NodeExecutionResult.success(Map.of(
            "deleted", response.deleted() != null ? response.deleted() : 0,
            "total", response.total() != null ? response.total() : 0
        ));
    }

    private static NodeExecutionResult updateByQuery(
        ElasticsearchClient client,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String index = getRequiredParam(params, "index");
        String queryJson = getRequiredParam(params, "query");
        String scriptJson = getRequiredParam(params, "script");
        boolean refresh = getBoolParam(params, "refresh", false);

        Map<String, Object> scriptMap = objectMapper.readValue(scriptJson, new TypeReference<>() {});
        String source = (String) scriptMap.get("source");
        String lang = (String) scriptMap.getOrDefault("lang", "painless");

        UpdateByQueryRequest.Builder request = new UpdateByQueryRequest.Builder()
            .index(index)
            .query(q -> q.withJson(new StringReader(queryJson)))
            .script(s -> s.inline(i -> i.source(source).lang(lang)));

        if (refresh) {
            request.refresh(true);
        }

        UpdateByQueryResponse response = client.updateByQuery(request.build());

        return NodeExecutionResult.success(Map.of(
            "updated", response.updated() != null ? response.updated() : 0,
            "total", response.total() != null ? response.total() : 0
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

    private static int getIntParam(Map<String, Object> params, String name, int defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
