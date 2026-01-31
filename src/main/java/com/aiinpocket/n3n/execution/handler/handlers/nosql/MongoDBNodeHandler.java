package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB node handler for document database operations.
 *
 * Features:
 * - Document CRUD operations (find, insert, update, delete)
 * - Aggregation pipeline support
 * - Collection management
 * - Index management
 * - Connection pooling with automatic cleanup
 *
 * Credential schema:
 * - connectionString: MongoDB connection string (mongodb://... or mongodb+srv://...)
 * - host: MongoDB host (if not using connectionString)
 * - port: MongoDB port (default: 27017)
 * - database: Database name
 * - username: Username (optional)
 * - password: Password (optional)
 * - authSource: Authentication database (default: admin)
 * - replicaSet: Replica set name (optional)
 * - tls: Enable TLS (default: false)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MongoDBNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;

    // Client cache with TTL
    private final ConcurrentHashMap<String, ClientEntry> clients = new ConcurrentHashMap<>();
    private static final long CLIENT_TTL_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    public String getType() {
        return "mongodb";
    }

    @Override
    public String getDisplayName() {
        return "MongoDB";
    }

    @Override
    public String getDescription() {
        return "MongoDB document database operations. Query, insert, update, delete documents and manage collections.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "mongodb";
    }

    @Override
    public String getCredentialType() {
        return "mongodb";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("document", ResourceDef.of("document", "Document", "Document CRUD operations"));
        resources.put("aggregate", ResourceDef.of("aggregate", "Aggregate", "Aggregation pipeline operations"));
        resources.put("collection", ResourceDef.of("collection", "Collection", "Collection management"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Document operations
        operations.put("document", List.of(
            OperationDef.create("find", "Find")
                .description("Find documents matching a query")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter as JSON")
                        .withPlaceholder("{\"status\": \"active\"}"),
                    FieldDef.textarea("projection", "Projection")
                        .withDescription("Fields to include/exclude")
                        .withPlaceholder("{\"name\": 1, \"email\": 1}"),
                    FieldDef.textarea("sort", "Sort")
                        .withDescription("Sort order")
                        .withPlaceholder("{\"createdAt\": -1}"),
                    FieldDef.integer("limit", "Limit")
                        .withDefault(100)
                        .withRange(1, 10000),
                    FieldDef.integer("skip", "Skip")
                        .withDefault(0)
                        .withRange(0, 1000000)
                ))
                .outputDescription("Returns { documents: [...], count: n }")
                .build(),

            OperationDef.create("findOne", "Find One")
                .description("Find a single document")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter as JSON")
                        .withPlaceholder("{\"_id\": \"...\"}"),
                    FieldDef.textarea("projection", "Projection")
                        .withDescription("Fields to include/exclude")
                ))
                .outputDescription("Returns { document: {...}, found: boolean }")
                .build(),

            OperationDef.create("insertOne", "Insert One")
                .description("Insert a single document")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("document", "Document")
                        .withDescription("Document to insert as JSON")
                        .withPlaceholder("{\"name\": \"John\", \"email\": \"john@example.com\"}")
                        .required()
                ))
                .outputDescription("Returns { insertedId: \"...\", acknowledged: boolean }")
                .build(),

            OperationDef.create("insertMany", "Insert Many")
                .description("Insert multiple documents")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("documents", "Documents")
                        .withDescription("Array of documents to insert")
                        .withPlaceholder("[{\"name\": \"John\"}, {\"name\": \"Jane\"}]")
                        .required()
                ))
                .outputDescription("Returns { insertedIds: [...], insertedCount: n }")
                .build(),

            OperationDef.create("updateOne", "Update One")
                .description("Update a single document")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter to match document")
                        .withPlaceholder("{\"_id\": \"...\"}")
                        .required(),
                    FieldDef.textarea("update", "Update")
                        .withDescription("Update operations")
                        .withPlaceholder("{\"$set\": {\"status\": \"inactive\"}}")
                        .required(),
                    FieldDef.bool("upsert", "Upsert")
                        .withDefault(false)
                        .withDescription("Insert if document doesn't exist")
                ))
                .outputDescription("Returns { matchedCount: n, modifiedCount: n, upsertedId: \"...\" }")
                .build(),

            OperationDef.create("updateMany", "Update Many")
                .description("Update multiple documents")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter to match documents")
                        .withPlaceholder("{\"status\": \"pending\"}")
                        .required(),
                    FieldDef.textarea("update", "Update")
                        .withDescription("Update operations")
                        .withPlaceholder("{\"$set\": {\"status\": \"processed\"}}")
                        .required()
                ))
                .outputDescription("Returns { matchedCount: n, modifiedCount: n }")
                .build(),

            OperationDef.create("deleteOne", "Delete One")
                .description("Delete a single document")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter to match document")
                        .withPlaceholder("{\"_id\": \"...\"}")
                        .required()
                ))
                .outputDescription("Returns { deletedCount: n }")
                .build(),

            OperationDef.create("deleteMany", "Delete Many")
                .description("Delete multiple documents")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter to match documents")
                        .withPlaceholder("{\"status\": \"expired\"}")
                        .required()
                ))
                .outputDescription("Returns { deletedCount: n }")
                .build(),

            OperationDef.create("count", "Count")
                .description("Count documents matching a filter")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Query filter")
                        .withPlaceholder("{\"status\": \"active\"}")
                ))
                .outputDescription("Returns { count: n }")
                .build(),

            OperationDef.create("distinct", "Distinct")
                .description("Get distinct values for a field")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.string("field", "Field")
                        .withDescription("Field name to get distinct values")
                        .required(),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Optional query filter")
                ))
                .outputDescription("Returns { values: [...], count: n }")
                .build()
        ));

        // Aggregate operations
        operations.put("aggregate", List.of(
            OperationDef.create("pipeline", "Pipeline")
                .description("Execute an aggregation pipeline")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("pipeline", "Pipeline")
                        .withDescription("Aggregation pipeline stages as JSON array")
                        .withPlaceholder("[{\"$match\": {...}}, {\"$group\": {...}}]")
                        .required(),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(1000)
                        .withRange(1, 100000)
                ))
                .outputDescription("Returns { results: [...], count: n }")
                .build()
        ));

        // Collection operations
        operations.put("collection", List.of(
            OperationDef.create("list", "List Collections")
                .description("List all collections in the database")
                .fields(List.of())
                .outputDescription("Returns { collections: [...] }")
                .build(),

            OperationDef.create("create", "Create Collection")
                .description("Create a new collection")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name to create")
                        .required(),
                    FieldDef.textarea("options", "Options")
                        .withDescription("Collection options as JSON")
                        .withPlaceholder("{\"capped\": true, \"size\": 10000000}")
                ))
                .outputDescription("Returns { created: boolean }")
                .build(),

            OperationDef.create("drop", "Drop Collection")
                .description("Drop (delete) a collection")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name to drop")
                        .required()
                ))
                .outputDescription("Returns { dropped: boolean }")
                .build(),

            OperationDef.create("createIndex", "Create Index")
                .description("Create an index on a collection")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.textarea("keys", "Index Keys")
                        .withDescription("Index keys specification")
                        .withPlaceholder("{\"email\": 1}")
                        .required(),
                    FieldDef.textarea("options", "Options")
                        .withDescription("Index options")
                        .withPlaceholder("{\"unique\": true, \"name\": \"email_idx\"}")
                ))
                .outputDescription("Returns { indexName: \"...\" }")
                .build(),

            OperationDef.create("listIndexes", "List Indexes")
                .description("List indexes on a collection")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required()
                ))
                .outputDescription("Returns { indexes: [...] }")
                .build(),

            OperationDef.create("dropIndex", "Drop Index")
                .description("Drop an index from a collection")
                .fields(List.of(
                    FieldDef.string("collection", "Collection")
                        .withDescription("Collection name")
                        .required(),
                    FieldDef.string("indexName", "Index Name")
                        .withDescription("Name of the index to drop")
                        .required()
                ))
                .outputDescription("Returns { dropped: boolean }")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        try {
            MongoClient client = getClient(credential);
            String databaseName = getCredentialValue(credential, "database");
            if (databaseName == null || databaseName.isEmpty()) {
                return NodeExecutionResult.failure("Database name is required in credential");
            }

            MongoDatabase database = client.getDatabase(databaseName);

            return switch (resource) {
                case "document" -> executeDocumentOperation(database, operation, params);
                case "aggregate" -> executeAggregateOperation(database, operation, params);
                case "collection" -> executeCollectionOperation(database, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("MongoDB operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("MongoDB error: " + e.getMessage());
        }
    }

    // ==================== Document Operations ====================

    private NodeExecutionResult executeDocumentOperation(
        MongoDatabase database,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        String collectionName = getRequiredParam(params, "collection");
        MongoCollection<Document> collection = database.getCollection(collectionName);

        return switch (operation) {
            case "find" -> executeFind(collection, params);
            case "findOne" -> executeFindOne(collection, params);
            case "insertOne" -> executeInsertOne(collection, params);
            case "insertMany" -> executeInsertMany(collection, params);
            case "updateOne" -> executeUpdateOne(collection, params);
            case "updateMany" -> executeUpdateMany(collection, params);
            case "deleteOne" -> executeDeleteOne(collection, params);
            case "deleteMany" -> executeDeleteMany(collection, params);
            case "count" -> executeCount(collection, params);
            case "distinct" -> executeDistinct(collection, params);
            default -> NodeExecutionResult.failure("Unknown document operation: " + operation);
        };
    }

    private NodeExecutionResult executeFind(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeFindOne(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
        Document filter = parseDocument(getParam(params, "filter", "{}"));
        Document projection = parseDocument(getParam(params, "projection", ""));

        FindIterable<Document> iterable = collection.find(filter);
        if (!projection.isEmpty()) {
            iterable.projection(projection);
        }

        Document doc = iterable.first();

        if (doc == null) {
            return NodeExecutionResult.success(Map.of(
                "document", (Object) null,
                "found", false
            ));
        }

        return NodeExecutionResult.success(Map.of(
            "document", documentToMap(doc),
            "found", true
        ));
    }

    private NodeExecutionResult executeInsertOne(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeInsertMany(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeUpdateOne(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeUpdateMany(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
        Document filter = parseDocument(getRequiredParam(params, "filter"));
        Document update = parseDocument(getRequiredParam(params, "update"));

        UpdateResult result = collection.updateMany(filter, update);

        return NodeExecutionResult.success(Map.of(
            "matchedCount", result.getMatchedCount(),
            "modifiedCount", result.getModifiedCount()
        ));
    }

    private NodeExecutionResult executeDeleteOne(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
        Document filter = parseDocument(getRequiredParam(params, "filter"));

        DeleteResult result = collection.deleteOne(filter);

        return NodeExecutionResult.success(Map.of(
            "deletedCount", result.getDeletedCount()
        ));
    }

    private NodeExecutionResult executeDeleteMany(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
        Document filter = parseDocument(getRequiredParam(params, "filter"));

        DeleteResult result = collection.deleteMany(filter);

        return NodeExecutionResult.success(Map.of(
            "deletedCount", result.getDeletedCount()
        ));
    }

    private NodeExecutionResult executeCount(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
        Document filter = parseDocument(getParam(params, "filter", "{}"));

        long count = collection.countDocuments(filter);

        return NodeExecutionResult.success(Map.of("count", count));
    }

    private NodeExecutionResult executeDistinct(MongoCollection<Document> collection, Map<String, Object> params) throws Exception {
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

    // ==================== Aggregate Operations ====================

    private NodeExecutionResult executeAggregateOperation(
        MongoDatabase database,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        if ("pipeline".equals(operation)) {
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

        return NodeExecutionResult.failure("Unknown aggregate operation: " + operation);
    }

    // ==================== Collection Operations ====================

    private NodeExecutionResult executeCollectionOperation(
        MongoDatabase database,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                List<String> collections = new ArrayList<>();
                for (String name : database.listCollectionNames()) {
                    collections.add(name);
                }
                yield NodeExecutionResult.success(Map.of("collections", collections));
            }
            case "create" -> {
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
                yield NodeExecutionResult.success(Map.of("created", true));
            }
            case "drop" -> {
                String collectionName = getRequiredParam(params, "collection");
                database.getCollection(collectionName).drop();
                yield NodeExecutionResult.success(Map.of("dropped", true));
            }
            case "createIndex" -> {
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
                yield NodeExecutionResult.success(Map.of("indexName", indexName));
            }
            case "listIndexes" -> {
                String collectionName = getRequiredParam(params, "collection");
                MongoCollection<Document> collection = database.getCollection(collectionName);

                List<Map<String, Object>> indexes = new ArrayList<>();
                for (Document index : collection.listIndexes()) {
                    indexes.add(documentToMap(index));
                }
                yield NodeExecutionResult.success(Map.of("indexes", indexes));
            }
            case "dropIndex" -> {
                String collectionName = getRequiredParam(params, "collection");
                String indexName = getRequiredParam(params, "indexName");

                MongoCollection<Document> collection = database.getCollection(collectionName);
                collection.dropIndex(indexName);
                yield NodeExecutionResult.success(Map.of("dropped", true));
            }
            default -> NodeExecutionResult.failure("Unknown collection operation: " + operation);
        };
    }

    // ==================== Connection Management ====================

    private MongoClient getClient(Map<String, Object> credential) {
        String cacheKey = generateCacheKey(credential);

        ClientEntry entry = clients.compute(cacheKey, (key, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.createdAt < CLIENT_TTL_MS) {
                return existing;
            }
            if (existing != null) {
                try {
                    existing.client.close();
                } catch (Exception e) {
                    log.warn("Error closing MongoDB client: {}", e.getMessage());
                }
            }
            return createClient(credential);
        });

        return entry.client;
    }

    private ClientEntry createClient(Map<String, Object> credential) {
        String connectionString = getCredentialValue(credential, "connectionString");

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder();

        if (connectionString != null && !connectionString.isEmpty()) {
            settingsBuilder.applyConnectionString(new ConnectionString(connectionString));
        } else {
            String host = getCredentialValue(credential, "host");
            String portStr = getCredentialValue(credential, "port");
            String database = getCredentialValue(credential, "database");
            String username = getCredentialValue(credential, "username");
            String password = getCredentialValue(credential, "password");
            String authSource = getCredentialValue(credential, "authSource");
            String replicaSet = getCredentialValue(credential, "replicaSet");
            String tlsStr = getCredentialValue(credential, "tls");

            if (host == null || host.isEmpty()) {
                host = "localhost";
            }
            int port = 27017;
            if (portStr != null && !portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    // Use default
                }
            }

            // Build connection string from parts
            StringBuilder connStr = new StringBuilder("mongodb://");
            connStr.append(host).append(":").append(port);

            if (database != null && !database.isEmpty()) {
                connStr.append("/").append(database);
            }

            List<String> options = new ArrayList<>();
            if (replicaSet != null && !replicaSet.isEmpty()) {
                options.add("replicaSet=" + replicaSet);
            }
            if ("true".equalsIgnoreCase(tlsStr)) {
                options.add("tls=true");
            }
            if (authSource != null && !authSource.isEmpty()) {
                options.add("authSource=" + authSource);
            }

            if (!options.isEmpty()) {
                connStr.append("?").append(String.join("&", options));
            }

            settingsBuilder.applyConnectionString(new ConnectionString(connStr.toString()));

            // Add credentials if provided
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                String authDb = (authSource != null && !authSource.isEmpty()) ? authSource : "admin";
                MongoCredential mongoCredential = MongoCredential.createCredential(
                    username, authDb, password.toCharArray()
                );
                settingsBuilder.credential(mongoCredential);
            }
        }

        // Connection pool settings
        settingsBuilder.applyToConnectionPoolSettings(builder ->
            builder
                .maxSize(5)
                .minSize(1)
                .maxWaitTime(10, TimeUnit.SECONDS)
                .maxConnectionIdleTime(5, TimeUnit.MINUTES)
        );

        MongoClient client = MongoClients.create(settingsBuilder.build());
        log.info("Created MongoDB client for connection");

        return new ClientEntry(client, System.currentTimeMillis());
    }

    private String generateCacheKey(Map<String, Object> credential) {
        String connStr = getCredentialValue(credential, "connectionString");
        if (connStr != null && !connStr.isEmpty()) {
            return String.valueOf(connStr.hashCode());
        }
        String host = getCredentialValue(credential, "host");
        String port = getCredentialValue(credential, "port");
        String database = getCredentialValue(credential, "database");
        String username = getCredentialValue(credential, "username");
        return String.format("%s:%s:%s:%s", host, port, database, username);
    }

    // ==================== Helper Methods ====================

    private Document parseDocument(String json) {
        if (json == null || json.isEmpty()) {
            return new Document();
        }
        return Document.parse(json);
    }

    private Map<String, Object> documentToMap(Document doc) {
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

    private List<Object> convertList(List<?> list) {
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

    // Client cache entry
    private static class ClientEntry {
        final MongoClient client;
        final long createdAt;

        ClientEntry(MongoClient client, long createdAt) {
            this.client = client;
            this.createdAt = createdAt;
        }
    }
}
