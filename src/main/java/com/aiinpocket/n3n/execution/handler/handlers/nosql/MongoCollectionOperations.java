package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Collection management operations for MongoDB.
 * Handles list, create, drop collections and index management
 * (createIndex, listIndexes, dropIndex).
 */
final class MongoCollectionOperations {

    private MongoCollectionOperations() {}

    static NodeExecutionResult execute(
        MongoDatabase database,
        String operation,
        Map<String, Object> params
    ) {
        return switch (operation) {
            case "list" -> executeList(database);
            case "create" -> executeCreate(database, params);
            case "drop" -> executeDrop(database, params);
            case "createIndex" -> executeCreateIndex(database, params);
            case "listIndexes" -> executeListIndexes(database, params);
            case "dropIndex" -> executeDropIndex(database, params);
            default -> NodeExecutionResult.failure("Unknown collection operation: " + operation);
        };
    }

    private static NodeExecutionResult executeList(MongoDatabase database) {
        List<String> collections = new ArrayList<>();
        for (String name : database.listCollectionNames()) {
            collections.add(name);
        }
        return NodeExecutionResult.success(Map.of("collections", collections));
    }

    private static NodeExecutionResult executeCreate(MongoDatabase database, Map<String, Object> params) {
        String collectionName = getRequiredParam(params, "collection");
        String optionsJson = getParam(params, "options", "");

        if (optionsJson.isEmpty()) {
            database.createCollection(collectionName);
        } else {
            Document options = Document.parse(optionsJson);
            CreateCollectionOptions createOptions = new CreateCollectionOptions();
            if (options.containsKey("capped")) {
                createOptions.capped(options.getBoolean("capped"));
            }
            if (options.containsKey("size")) {
                createOptions.sizeInBytes(options.getLong("size"));
            }
            if (options.containsKey("max")) {
                createOptions.maxDocuments(options.getLong("max"));
            }
            database.createCollection(collectionName, createOptions);
        }
        return NodeExecutionResult.success(Map.of("created", true));
    }

    private static NodeExecutionResult executeDrop(MongoDatabase database, Map<String, Object> params) {
        String collectionName = getRequiredParam(params, "collection");
        database.getCollection(collectionName).drop();
        return NodeExecutionResult.success(Map.of("dropped", true));
    }

    private static NodeExecutionResult executeCreateIndex(MongoDatabase database, Map<String, Object> params) {
        String collectionName = getRequiredParam(params, "collection");
        Document keys = parseDocument(getRequiredParam(params, "keys"));
        String optionsJson = getParam(params, "options", "");

        MongoCollection<Document> collection = database.getCollection(collectionName);

        String indexName;
        if (optionsJson.isEmpty()) {
            indexName = collection.createIndex(keys);
        } else {
            Document optionsDoc = Document.parse(optionsJson);
            IndexOptions indexOptions = new IndexOptions();
            if (optionsDoc.containsKey("unique")) {
                indexOptions.unique(optionsDoc.getBoolean("unique"));
            }
            if (optionsDoc.containsKey("name")) {
                indexOptions.name(optionsDoc.getString("name"));
            }
            if (optionsDoc.containsKey("sparse")) {
                indexOptions.sparse(optionsDoc.getBoolean("sparse"));
            }
            if (optionsDoc.containsKey("expireAfterSeconds")) {
                indexOptions.expireAfter(optionsDoc.getLong("expireAfterSeconds"), TimeUnit.SECONDS);
            }
            indexName = collection.createIndex(keys, indexOptions);
        }
        return NodeExecutionResult.success(Map.of("indexName", indexName));
    }

    private static NodeExecutionResult executeListIndexes(MongoDatabase database, Map<String, Object> params) {
        String collectionName = getRequiredParam(params, "collection");
        MongoCollection<Document> collection = database.getCollection(collectionName);

        List<Map<String, Object>> indexes = new ArrayList<>();
        for (Document index : collection.listIndexes()) {
            indexes.add(documentToMap(index));
        }
        return NodeExecutionResult.success(Map.of("indexes", indexes));
    }

    private static NodeExecutionResult executeDropIndex(MongoDatabase database, Map<String, Object> params) {
        String collectionName = getRequiredParam(params, "collection");
        String indexName = getRequiredParam(params, "indexName");

        MongoCollection<Document> collection = database.getCollection(collectionName);
        collection.dropIndex(indexName);
        return NodeExecutionResult.success(Map.of("dropped", true));
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
