package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

/**
 * Document CRUD operations for MongoDB.
 * Handles find, findOne, insertOne, insertMany, updateOne, updateMany,
 * deleteOne, deleteMany, count, and distinct operations.
 */
final class MongoDocumentOperations {

    private MongoDocumentOperations() {}

    static NodeExecutionResult execute(
        MongoDatabase database,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String collectionName = getRequiredParam(params, "collection");
        MongoCollection<Document> collection = database.getCollection(collectionName);

        return switch (operation) {
            case "find" -> executeFind(collection, params);
            case "findOne" -> executeFindOne(collection, params);
            case "insertOne" -> executeInsertOne(collection, params);
            case "insertMany" -> executeInsertMany(collection, params, objectMapper);
            case "updateOne" -> executeUpdateOne(collection, params);
            case "updateMany" -> executeUpdateMany(collection, params);
            case "deleteOne" -> executeDeleteOne(collection, params);
            case "deleteMany" -> executeDeleteMany(collection, params);
            case "count" -> executeCount(collection, params);
            case "distinct" -> executeDistinct(collection, params);
            default -> NodeExecutionResult.failure("Unknown document operation: " + operation);
        };
    }

    private static NodeExecutionResult executeFind(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getParam(params, "filter", "{}"));
        Document projection = parseDocument(getParam(params, "projection", ""));
        Document sort = parseDocument(getParam(params, "sort", ""));
        int limit = getIntParam(params, "limit", 100);
        int skip = getIntParam(params, "skip", 0);

        FindIterable<Document> iterable = collection.find(filter);

        if (!projection.isEmpty()) {
            iterable.projection(projection);
        }
        if (!sort.isEmpty()) {
            iterable.sort(sort);
        }
        if (skip > 0) {
            iterable.skip(skip);
        }
        iterable.limit(limit);

        List<Map<String, Object>> documents = new ArrayList<>();
        for (Document doc : iterable) {
            documents.add(documentToMap(doc));
        }

        return NodeExecutionResult.success(Map.of(
            "documents", documents,
            "count", documents.size()
        ));
    }

    private static NodeExecutionResult executeFindOne(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getParam(params, "filter", "{}"));
        Document projection = parseDocument(getParam(params, "projection", ""));

        FindIterable<Document> iterable = collection.find(filter);
        if (!projection.isEmpty()) {
            iterable.projection(projection);
        }

        Document doc = iterable.first();

        if (doc == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("document", null);
            result.put("found", false);
            return NodeExecutionResult.success(result);
        }

        return NodeExecutionResult.success(Map.of(
            "document", documentToMap(doc),
            "found", true
        ));
    }

    private static NodeExecutionResult executeInsertOne(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        String docJson = getRequiredParam(params, "document");
        Document doc = Document.parse(docJson);

        InsertOneResult result = collection.insertOne(doc);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("acknowledged", result.wasAcknowledged());
        if (result.getInsertedId() != null) {
            response.put("insertedId", result.getInsertedId().asObjectId().getValue().toHexString());
        }

        return NodeExecutionResult.success(response);
    }

    private static NodeExecutionResult executeInsertMany(
        MongoCollection<Document> collection,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        String docsJson = getRequiredParam(params, "documents");
        List<Map<String, Object>> docMaps = objectMapper.readValue(docsJson, new TypeReference<>() {});

        List<Document> docs = new ArrayList<>();
        for (Map<String, Object> map : docMaps) {
            docs.add(new Document(map));
        }

        InsertManyResult result = collection.insertMany(docs);

        List<String> insertedIds = new ArrayList<>();
        if (result.getInsertedIds() != null) {
            result.getInsertedIds().forEach((idx, id) -> {
                if (id.isObjectId()) {
                    insertedIds.add(id.asObjectId().getValue().toHexString());
                } else {
                    insertedIds.add(id.toString());
                }
            });
        }

        return NodeExecutionResult.success(Map.of(
            "acknowledged", result.wasAcknowledged(),
            "insertedIds", insertedIds,
            "insertedCount", insertedIds.size()
        ));
    }

    private static NodeExecutionResult executeUpdateOne(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getRequiredParam(params, "filter"));
        Document update = parseDocument(getRequiredParam(params, "update"));
        boolean upsert = getBoolParam(params, "upsert", false);

        UpdateOptions options = new UpdateOptions().upsert(upsert);
        UpdateResult result = collection.updateOne(filter, update, options);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("matchedCount", result.getMatchedCount());
        response.put("modifiedCount", result.getModifiedCount());
        if (result.getUpsertedId() != null) {
            response.put("upsertedId", result.getUpsertedId().asObjectId().getValue().toHexString());
        }

        return NodeExecutionResult.success(response);
    }

    private static NodeExecutionResult executeUpdateMany(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getRequiredParam(params, "filter"));
        Document update = parseDocument(getRequiredParam(params, "update"));

        UpdateResult result = collection.updateMany(filter, update);

        return NodeExecutionResult.success(Map.of(
            "matchedCount", result.getMatchedCount(),
            "modifiedCount", result.getModifiedCount()
        ));
    }

    private static NodeExecutionResult executeDeleteOne(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getRequiredParam(params, "filter"));

        DeleteResult result = collection.deleteOne(filter);

        return NodeExecutionResult.success(Map.of(
            "deletedCount", result.getDeletedCount()
        ));
    }

    private static NodeExecutionResult executeDeleteMany(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getRequiredParam(params, "filter"));

        DeleteResult result = collection.deleteMany(filter);

        return NodeExecutionResult.success(Map.of(
            "deletedCount", result.getDeletedCount()
        ));
    }

    private static NodeExecutionResult executeCount(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        Document filter = parseDocument(getParam(params, "filter", "{}"));

        long count = collection.countDocuments(filter);

        return NodeExecutionResult.success(Map.of("count", count));
    }

    private static NodeExecutionResult executeDistinct(
        MongoCollection<Document> collection, Map<String, Object> params
    ) {
        String field = getRequiredParam(params, "field");
        Document filter = parseDocument(getParam(params, "filter", "{}"));

        DistinctIterable<Object> iterable = collection.distinct(field, filter, Object.class);

        List<Object> values = new ArrayList<>();
        for (Object value : iterable) {
            if (value instanceof ObjectId) {
                values.add(((ObjectId) value).toHexString());
            } else {
                values.add(value);
            }
        }

        return NodeExecutionResult.success(Map.of(
            "values", values,
            "count", values.size()
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

    private static Document parseDocument(String json) {
        if (json == null || json.isEmpty()) {
            return new Document();
        }
        return Document.parse(json);
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
