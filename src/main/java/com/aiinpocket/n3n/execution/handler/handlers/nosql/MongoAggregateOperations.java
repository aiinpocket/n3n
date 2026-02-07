package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

/**
 * Aggregation pipeline operations for MongoDB.
 * Handles pipeline execution with configurable max results.
 */
final class MongoAggregateOperations {

    private MongoAggregateOperations() {}

    static NodeExecutionResult execute(
        MongoDatabase database,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        if ("pipeline".equals(operation)) {
            return executePipeline(database, params, objectMapper);
        }
        return NodeExecutionResult.failure("Unknown aggregate operation: " + operation);
    }

    private static NodeExecutionResult executePipeline(
        MongoDatabase database,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String collectionName = getRequiredParam(params, "collection");
        String pipelineJson = getRequiredParam(params, "pipeline");
        int maxResults = getIntParam(params, "maxResults", 1000);

        MongoCollection<Document> collection = database.getCollection(collectionName);

        List<Document> pipeline = new ArrayList<>();
        List<Map<String, Object>> stages = objectMapper.readValue(pipelineJson, new TypeReference<>() {});
        for (Map<String, Object> stage : stages) {
            pipeline.add(new Document(stage));
        }

        AggregateIterable<Document> iterable = collection.aggregate(pipeline);

        List<Map<String, Object>> results = new ArrayList<>();
        int count = 0;
        for (Document doc : iterable) {
            if (count >= maxResults) break;
            results.add(documentToMap(doc));
            count++;
        }

        return NodeExecutionResult.success(Map.of(
            "results", results,
            "count", results.size()
        ));
    }

    // ==================== Helper Methods ====================

    private static String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
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

    private static Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ObjectId) {
                map.put(entry.getKey(), ((ObjectId) value).toHexString());
            } else if (value instanceof Document) {
                map.put(entry.getKey(), documentToMap((Document) value));
            } else if (value instanceof List) {
                map.put(entry.getKey(), convertList((List<?>) value));
            } else if (value instanceof Date) {
                map.put(entry.getKey(), ((Date) value).toInstant().toString());
            } else {
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    private static List<Object> convertList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof ObjectId) {
                result.add(((ObjectId) item).toHexString());
            } else if (item instanceof Document) {
                result.add(documentToMap((Document) item));
            } else if (item instanceof List) {
                result.add(convertList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
