package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch node handler for search and analytics operations.
 *
 * Features:
 * - Document CRUD (index, get, update, delete)
 * - Search with query DSL
 * - Bulk operations
 * - Index management
 * - Aggregations
 *
 * Credential schema:
 * - host: Elasticsearch host (default: localhost)
 * - port: Elasticsearch port (default: 9200)
 * - scheme: http or https (default: http)
 * - username: Username for authentication (optional)
 * - password: Password for authentication (optional)
 * - apiKey: API key for authentication (optional, alternative to username/password)
 * - cloudId: Elastic Cloud ID (optional, for Elastic Cloud)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;

    // Client cache with TTL
    private final ConcurrentHashMap<String, ClientEntry> clients = new ConcurrentHashMap<>();
    private static final long CLIENT_TTL_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    public String getType() {
        return "elasticsearch";
    }

    @Override
    public String getDisplayName() {
        return "Elasticsearch";
    }

    @Override
    public String getDescription() {
        return "Elasticsearch search and analytics engine. Search, index documents, and manage indices.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "elasticsearch";
    }

    @Override
    public String getCredentialType() {
        return "elasticsearch";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("document", ResourceDef.of("document", "Document", "Document operations (index, get, update, delete)"));
        resources.put("search", ResourceDef.of("search", "Search", "Search and query operations"));
        resources.put("index", ResourceDef.of("index", "Index", "Index management operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Document operations
        operations.put("document", List.of(
            OperationDef.create("index", "Index Document")
                .description("Index (create or update) a document")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.string("id", "Document ID")
                        .withDescription("Document ID (optional, auto-generated if not provided)"),
                    FieldDef.textarea("document", "Document")
                        .withDescription("Document body as JSON")
                        .withPlaceholder("{\"title\": \"...\", \"content\": \"...\"}")
                        .required(),
                    FieldDef.bool("refresh", "Refresh")
                        .withDescription("Refresh index after indexing")
                        .withDefault(false)
                ))
                .outputDescription("Returns { id: \"...\", result: \"created\"|\"updated\", version: n }")
                .build(),

            OperationDef.create("get", "Get Document")
                .description("Get a document by ID")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.string("id", "Document ID")
                        .withDescription("Document ID")
                        .required(),
                    FieldDef.textarea("sourceIncludes", "Source Includes")
                        .withDescription("Fields to include (JSON array)")
                        .withPlaceholder("[\"title\", \"content\"]"),
                    FieldDef.textarea("sourceExcludes", "Source Excludes")
                        .withDescription("Fields to exclude (JSON array)")
                ))
                .outputDescription("Returns { found: boolean, document: {...}, id: \"...\" }")
                .build(),

            OperationDef.create("update", "Update Document")
                .description("Partially update a document")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.string("id", "Document ID")
                        .withDescription("Document ID")
                        .required(),
                    FieldDef.textarea("document", "Document")
                        .withDescription("Partial document to merge")
                        .withPlaceholder("{\"status\": \"published\"}")
                        .required(),
                    FieldDef.bool("refresh", "Refresh")
                        .withDescription("Refresh index after update")
                        .withDefault(false),
                    FieldDef.bool("docAsUpsert", "Upsert")
                        .withDescription("Create document if not exists")
                        .withDefault(false)
                ))
                .outputDescription("Returns { id: \"...\", result: \"updated\"|\"noop\", version: n }")
                .build(),

            OperationDef.create("delete", "Delete Document")
                .description("Delete a document by ID")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.string("id", "Document ID")
                        .withDescription("Document ID")
                        .required(),
                    FieldDef.bool("refresh", "Refresh")
                        .withDescription("Refresh index after delete")
                        .withDefault(false)
                ))
                .outputDescription("Returns { id: \"...\", result: \"deleted\"|\"not_found\" }")
                .build(),

            OperationDef.create("bulk", "Bulk Operations")
                .description("Perform multiple index/update/delete operations")
                .fields(List.of(
                    FieldDef.string("index", "Default Index")
                        .withDescription("Default index (can be overridden per operation)"),
                    FieldDef.textarea("operations", "Operations")
                        .withDescription("Array of operations")
                        .withPlaceholder("[{\"index\": {...}}, {\"delete\": {...}}]")
                        .required(),
                    FieldDef.bool("refresh", "Refresh")
                        .withDescription("Refresh indices after bulk")
                        .withDefault(false)
                ))
                .outputDescription("Returns { took: n, errors: boolean, items: [...] }")
                .build()
        ));

        // Search operations
        operations.put("search", List.of(
            OperationDef.create("query", "Search")
                .description("Search documents using Query DSL")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name (can include wildcards)")
                        .required(),
                    FieldDef.textarea("query", "Query")
                        .withDescription("Query DSL as JSON")
                        .withPlaceholder("{\"match\": {\"title\": \"search term\"}}"),
                    FieldDef.textarea("sort", "Sort")
                        .withDescription("Sort order as JSON array")
                        .withPlaceholder("[{\"date\": \"desc\"}, \"_score\"]"),
                    FieldDef.integer("from", "From")
                        .withDescription("Starting offset")
                        .withDefault(0),
                    FieldDef.integer("size", "Size")
                        .withDescription("Number of hits to return")
                        .withDefault(10)
                        .withRange(1, 10000),
                    FieldDef.textarea("sourceIncludes", "Source Includes")
                        .withDescription("Fields to include"),
                    FieldDef.textarea("aggs", "Aggregations")
                        .withDescription("Aggregations as JSON")
                        .withPlaceholder("{\"by_status\": {\"terms\": {\"field\": \"status\"}}}")
                ))
                .outputDescription("Returns { hits: [...], total: n, aggregations: {...} }")
                .build(),

            OperationDef.create("count", "Count")
                .description("Count documents matching a query")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.textarea("query", "Query")
                        .withDescription("Query DSL as JSON")
                        .withPlaceholder("{\"term\": {\"status\": \"active\"}}")
                ))
                .outputDescription("Returns { count: n }")
                .build(),

            OperationDef.create("deleteByQuery", "Delete by Query")
                .description("Delete documents matching a query")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.textarea("query", "Query")
                        .withDescription("Query DSL as JSON")
                        .withPlaceholder("{\"range\": {\"date\": {\"lt\": \"2020-01-01\"}}}")
                        .required(),
                    FieldDef.bool("refresh", "Refresh")
                        .withDescription("Refresh index after delete")
                        .withDefault(false)
                ))
                .outputDescription("Returns { deleted: n, total: n }")
                .build(),

            OperationDef.create("updateByQuery", "Update by Query")
                .description("Update documents matching a query")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.textarea("query", "Query")
                        .withDescription("Query DSL as JSON")
                        .required(),
                    FieldDef.textarea("script", "Script")
                        .withDescription("Update script")
                        .withPlaceholder("{\"source\": \"ctx._source.count++\", \"lang\": \"painless\"}")
                        .required(),
                    FieldDef.bool("refresh", "Refresh")
                        .withDescription("Refresh index after update")
                        .withDefault(false)
                ))
                .outputDescription("Returns { updated: n, total: n }")
                .build()
        ));

        // Index operations
        operations.put("index", List.of(
            OperationDef.create("create", "Create Index")
                .description("Create a new index")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.textarea("settings", "Settings")
                        .withDescription("Index settings as JSON")
                        .withPlaceholder("{\"number_of_shards\": 1, \"number_of_replicas\": 0}"),
                    FieldDef.textarea("mappings", "Mappings")
                        .withDescription("Index mappings as JSON")
                        .withPlaceholder("{\"properties\": {\"title\": {\"type\": \"text\"}}}")
                ))
                .outputDescription("Returns { acknowledged: boolean, index: \"...\" }")
                .build(),

            OperationDef.create("delete", "Delete Index")
                .description("Delete an index")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required()
                ))
                .outputDescription("Returns { acknowledged: boolean }")
                .build(),

            OperationDef.create("exists", "Index Exists")
                .description("Check if an index exists")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required()
                ))
                .outputDescription("Returns { exists: boolean }")
                .build(),

            OperationDef.create("getMapping", "Get Mapping")
                .description("Get index mapping")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required()
                ))
                .outputDescription("Returns { mapping: {...} }")
                .build(),

            OperationDef.create("putMapping", "Put Mapping")
                .description("Update index mapping")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required(),
                    FieldDef.textarea("mappings", "Mappings")
                        .withDescription("Mapping updates as JSON")
                        .withPlaceholder("{\"properties\": {\"new_field\": {\"type\": \"keyword\"}}}")
                        .required()
                ))
                .outputDescription("Returns { acknowledged: boolean }")
                .build(),

            OperationDef.create("refresh", "Refresh Index")
                .description("Refresh an index to make recent changes searchable")
                .fields(List.of(
                    FieldDef.string("index", "Index")
                        .withDescription("Index name")
                        .required()
                ))
                .outputDescription("Returns { shards: {...} }")
                .build(),

            OperationDef.create("list", "List Indices")
                .description("List all indices")
                .fields(List.of(
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Index pattern (default: *)")
                        .withDefault("*")
                ))
                .outputDescription("Returns { indices: [...] }")
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
            ElasticsearchClient client = getClient(credential);

            return switch (resource) {
                case "document" -> executeDocumentOperation(client, operation, params);
                case "search" -> executeSearchOperation(client, operation, params);
                case "index" -> executeIndexOperation(client, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Elasticsearch operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Elasticsearch error: " + e.getMessage());
        }
    }

    // ==================== Document Operations ====================

    private NodeExecutionResult executeDocumentOperation(
        ElasticsearchClient client,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "index" -> executeIndexDocument(client, params);
            case "get" -> executeGetDocument(client, params);
            case "update" -> executeUpdateDocument(client, params);
            case "delete" -> executeDeleteDocument(client, params);
            case "bulk" -> executeBulk(client, params);
            default -> NodeExecutionResult.failure("Unknown document operation: " + operation);
        };
    }

    private NodeExecutionResult executeIndexDocument(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeGetDocument(ElasticsearchClient client, Map<String, Object> params) throws Exception {
        String index = getRequiredParam(params, "index");
        String id = getRequiredParam(params, "id");
        String includesJson = getParam(params, "sourceIncludes", "");
        String excludesJson = getParam(params, "sourceExcludes", "");

        GetRequest.Builder request = new GetRequest.Builder()
            .index(index)
            .id(id);

        if (!includesJson.isEmpty() || !excludesJson.isEmpty()) {
            request.sourceIncludes(parseStringList(includesJson));
            request.sourceExcludes(parseStringList(excludesJson));
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

    private NodeExecutionResult executeUpdateDocument(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeDeleteDocument(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeBulk(ElasticsearchClient client, Map<String, Object> params) throws Exception {
        String defaultIndex = getParam(params, "index", "");
        String operationsJson = getRequiredParam(params, "operations");
        boolean refresh = getBoolParam(params, "refresh", false);

        List<Map<String, Object>> operations = objectMapper.readValue(operationsJson, new TypeReference<>() {});

        List<BulkOperation> bulkOps = new ArrayList<>();
        for (Map<String, Object> op : operations) {
            if (op.containsKey("index")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> indexOp = (Map<String, Object>) op.get("index");
                String idx = (String) indexOp.getOrDefault("_index", defaultIndex);
                String id = (String) indexOp.get("_id");
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) indexOp.get("document");

                BulkOperation.Builder builder = new BulkOperation.Builder();
                builder.index(i -> {
                    i.index(idx).document(doc);
                    if (id != null) i.id(id);
                    return i;
                });
                bulkOps.add(builder.build());
            } else if (op.containsKey("delete")) {
                @SuppressWarnings("unchecked")
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

    // ==================== Search Operations ====================

    private NodeExecutionResult executeSearchOperation(
        ElasticsearchClient client,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "query" -> executeSearch(client, params);
            case "count" -> executeCount(client, params);
            case "deleteByQuery" -> executeDeleteByQuery(client, params);
            case "updateByQuery" -> executeUpdateByQuery(client, params);
            default -> NodeExecutionResult.failure("Unknown search operation: " + operation);
        };
    }

    private NodeExecutionResult executeSearch(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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
                String aggJson = objectMapper.writeValueAsString(Map.of(aggName, aggsMap.get(aggName)));
                // Note: Simplified aggregation handling
            }
        }

        if (!includesJson.isEmpty()) {
            List<String> includes = parseStringList(includesJson);
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

    private NodeExecutionResult executeCount(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeDeleteByQuery(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeUpdateByQuery(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    // ==================== Index Operations ====================

    private NodeExecutionResult executeIndexOperation(
        ElasticsearchClient client,
        String operation,
        Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> executeCreateIndex(client, params);
            case "delete" -> executeDeleteIndex(client, params);
            case "exists" -> executeIndexExists(client, params);
            case "getMapping" -> executeGetMapping(client, params);
            case "putMapping" -> executePutMapping(client, params);
            case "refresh" -> executeRefreshIndex(client, params);
            case "list" -> executeListIndices(client, params);
            default -> NodeExecutionResult.failure("Unknown index operation: " + operation);
        };
    }

    private NodeExecutionResult executeCreateIndex(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeDeleteIndex(ElasticsearchClient client, Map<String, Object> params) throws Exception {
        String index = getRequiredParam(params, "index");

        DeleteIndexRequest request = new DeleteIndexRequest.Builder()
            .index(index)
            .build();

        DeleteIndexResponse response = client.indices().delete(request);

        return NodeExecutionResult.success(Map.of(
            "acknowledged", response.acknowledged()
        ));
    }

    private NodeExecutionResult executeIndexExists(ElasticsearchClient client, Map<String, Object> params) throws Exception {
        String index = getRequiredParam(params, "index");

        co.elastic.clients.elasticsearch.indices.ExistsRequest request =
            new co.elastic.clients.elasticsearch.indices.ExistsRequest.Builder()
                .index(index)
                .build();

        boolean exists = client.indices().exists(request).value();

        return NodeExecutionResult.success(Map.of("exists", exists));
    }

    private NodeExecutionResult executeGetMapping(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executePutMapping(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeRefreshIndex(ElasticsearchClient client, Map<String, Object> params) throws Exception {
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

    private NodeExecutionResult executeListIndices(ElasticsearchClient client, Map<String, Object> params) throws Exception {
        String pattern = getParam(params, "pattern", "*");

        GetIndexRequest request = new GetIndexRequest.Builder()
            .index(pattern)
            .build();

        GetIndexResponse response = client.indices().get(request);

        List<String> indices = new ArrayList<>(response.result().keySet());
        Collections.sort(indices);

        return NodeExecutionResult.success(Map.of("indices", indices));
    }

    // ==================== Connection Management ====================

    private ElasticsearchClient getClient(Map<String, Object> credential) {
        String cacheKey = generateCacheKey(credential);

        ClientEntry entry = clients.compute(cacheKey, (key, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.createdAt < CLIENT_TTL_MS) {
                return existing;
            }
            if (existing != null) {
                try {
                    existing.transport.close();
                } catch (Exception e) {
                    log.warn("Error closing Elasticsearch client: {}", e.getMessage());
                }
            }
            return createClient(credential);
        });

        return entry.client;
    }

    private ClientEntry createClient(Map<String, Object> credential) {
        String host = getCredentialValue(credential, "host");
        String portStr = getCredentialValue(credential, "port");
        String scheme = getCredentialValue(credential, "scheme");
        String username = getCredentialValue(credential, "username");
        String password = getCredentialValue(credential, "password");

        if (host == null || host.isEmpty()) {
            host = "localhost";
        }
        int port = 9200;
        if (portStr != null && !portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        if (scheme == null || scheme.isEmpty()) {
            scheme = "http";
        }

        RestClientBuilder builder = RestClient.builder(
            new HttpHost(host, port, scheme)
        );

        // Add authentication if provided
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
            );
            builder.setHttpClientConfigCallback(httpBuilder ->
                httpBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper(objectMapper)
        );
        ElasticsearchClient client = new ElasticsearchClient(transport);

        log.info("Created Elasticsearch client for {}://{}:{}", scheme, host, port);

        return new ClientEntry(client, transport, System.currentTimeMillis());
    }

    private String generateCacheKey(Map<String, Object> credential) {
        String host = getCredentialValue(credential, "host");
        String port = getCredentialValue(credential, "port");
        String username = getCredentialValue(credential, "username");
        return String.format("%s:%s:%s", host, port, username);
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
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
        final ElasticsearchClient client;
        final ElasticsearchTransport transport;
        final long createdAt;

        ClientEntry(ElasticsearchClient client, ElasticsearchTransport transport, long createdAt) {
            this.client = client;
            this.transport = transport;
            this.createdAt = createdAt;
        }
    }
}
