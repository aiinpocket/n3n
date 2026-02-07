package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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
 *
 * Operation logic is delegated to:
 * - {@link ElasticsearchDocumentOperations} - document CRUD and bulk
 * - {@link ElasticsearchSearchOperations} - query, count, deleteByQuery, updateByQuery
 * - {@link ElasticsearchIndexOperations} - index lifecycle management
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
                case "document" -> ElasticsearchDocumentOperations.execute(client, operation, params, objectMapper);
                case "search" -> ElasticsearchSearchOperations.execute(client, operation, params, objectMapper);
                case "index" -> ElasticsearchIndexOperations.execute(client, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Elasticsearch operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Elasticsearch error: " + e.getMessage());
        }
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
