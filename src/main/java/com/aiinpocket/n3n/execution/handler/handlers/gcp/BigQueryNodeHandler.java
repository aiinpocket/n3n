package com.aiinpocket.n3n.execution.handler.handlers.gcp;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Google BigQuery API node handler.
 *
 * Supports query and data management via BigQuery API v2.
 *
 * Features:
 * - Query execution (sync and async)
 * - Dataset management
 * - Table operations
 * - Data insertion
 *
 * Credential schema:
 * - serviceAccountJson: Service Account JSON key
 * - projectId: GCP Project ID (optional)
 *
 * Required IAM roles:
 * - roles/bigquery.admin (full access)
 * - roles/bigquery.user (query and job)
 * - roles/bigquery.dataViewer (read-only)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BigQueryNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String BIGQUERY_API_BASE = "https://bigquery.googleapis.com/bigquery/v2";

    @Override
    public String getType() {
        return "bigQuery";
    }

    @Override
    public String getDisplayName() {
        return "BigQuery";
    }

    @Override
    public String getDescription() {
        return "Google BigQuery API. Run queries and manage data warehouse.";
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public String getIcon() {
        return "bigQuery";
    }

    @Override
    public String getCredentialType() {
        return "bigQuery";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("query", ResourceDef.of("query", "Query", "Execute SQL queries"));
        resources.put("dataset", ResourceDef.of("dataset", "Dataset", "Dataset operations"));
        resources.put("table", ResourceDef.of("table", "Table", "Table operations"));
        resources.put("job", ResourceDef.of("job", "Job", "Job management"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Query operations
        operations.put("query", List.of(
            OperationDef.create("execute", "Execute Query")
                .description("Execute a SQL query synchronously")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID")
                        .withDescription("GCP Project ID"),
                    FieldDef.textarea("query", "SQL Query")
                        .withDescription("SQL query to execute")
                        .required(),
                    FieldDef.bool("useLegacySql", "Legacy SQL")
                        .withDescription("Use legacy SQL dialect")
                        .withDefault(false),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDescription("Maximum rows to return")
                        .withDefault(1000),
                    FieldDef.integer("timeoutMs", "Timeout (ms)")
                        .withDescription("Query timeout in milliseconds")
                        .withDefault(60000),
                    FieldDef.bool("dryRun", "Dry Run")
                        .withDescription("Validate query without executing")
                        .withDefault(false)
                ))
                .outputDescription("Returns { rows: [...], schema: {...}, totalRows }")
                .build(),

            OperationDef.create("executeAsync", "Execute Query (Async)")
                .description("Execute a query asynchronously as a job")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.textarea("query", "SQL Query")
                        .required(),
                    FieldDef.bool("useLegacySql", "Legacy SQL")
                        .withDefault(false),
                    FieldDef.string("destinationTable", "Destination Table")
                        .withDescription("Table to write results (project.dataset.table)"),
                    FieldDef.string("writeDisposition", "Write Disposition")
                        .withOptions(List.of("WRITE_TRUNCATE", "WRITE_APPEND", "WRITE_EMPTY"))
                        .withDefault("WRITE_TRUNCATE")
                ))
                .outputDescription("Returns { jobId, status }")
                .build()
        ));

        // Dataset operations
        operations.put("dataset", List.of(
            OperationDef.create("list", "List Datasets")
                .description("List datasets in the project")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                ))
                .outputDescription("Returns { datasets: [...] }")
                .build(),

            OperationDef.create("get", "Get Dataset")
                .description("Get dataset information")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required()
                ))
                .outputDescription("Returns dataset metadata")
                .build(),

            OperationDef.create("create", "Create Dataset")
                .description("Create a new dataset")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.string("location", "Location")
                        .withDefault("US"),
                    FieldDef.textarea("description", "Description")
                ))
                .outputDescription("Returns created dataset")
                .build(),

            OperationDef.create("delete", "Delete Dataset")
                .description("Delete a dataset")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.bool("deleteContents", "Delete Contents")
                        .withDescription("Delete all tables in dataset")
                        .withDefault(false)
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Table operations
        operations.put("table", List.of(
            OperationDef.create("list", "List Tables")
                .description("List tables in a dataset")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                ))
                .outputDescription("Returns { tables: [...] }")
                .build(),

            OperationDef.create("get", "Get Table")
                .description("Get table metadata and schema")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.string("tableId", "Table ID")
                        .required()
                ))
                .outputDescription("Returns table metadata with schema")
                .build(),

            OperationDef.create("create", "Create Table")
                .description("Create a new table")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.string("tableId", "Table ID")
                        .required(),
                    FieldDef.textarea("schema", "Schema")
                        .withDescription("JSON schema: [{\"name\":\"col\",\"type\":\"STRING\"}]")
                        .required(),
                    FieldDef.textarea("description", "Description")
                ))
                .outputDescription("Returns created table")
                .build(),

            OperationDef.create("delete", "Delete Table")
                .description("Delete a table")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.string("tableId", "Table ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("insert", "Insert Rows")
                .description("Insert rows into a table")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.string("tableId", "Table ID")
                        .required(),
                    FieldDef.textarea("rows", "Rows")
                        .withDescription("JSON array of row objects")
                        .required(),
                    FieldDef.bool("skipInvalidRows", "Skip Invalid")
                        .withDefault(false),
                    FieldDef.bool("ignoreUnknownValues", "Ignore Unknown")
                        .withDefault(false)
                ))
                .outputDescription("Returns { insertedRows, errors }")
                .build(),

            OperationDef.create("getData", "Get Table Data")
                .description("Read data from a table")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("datasetId", "Dataset ID")
                        .required(),
                    FieldDef.string("tableId", "Table ID")
                        .required(),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(1000),
                    FieldDef.integer("startIndex", "Start Index")
                        .withDefault(0),
                    FieldDef.string("selectedFields", "Fields")
                        .withDescription("Comma-separated field names")
                ))
                .outputDescription("Returns { rows: [...], totalRows }")
                .build()
        ));

        // Job operations
        operations.put("job", List.of(
            OperationDef.create("list", "List Jobs")
                .description("List jobs in the project")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("stateFilter", "State Filter")
                        .withOptions(List.of("done", "pending", "running")),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                ))
                .outputDescription("Returns { jobs: [...] }")
                .build(),

            OperationDef.create("get", "Get Job")
                .description("Get job status and results")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("jobId", "Job ID")
                        .required()
                ))
                .outputDescription("Returns job details")
                .build(),

            OperationDef.create("getResults", "Get Job Results")
                .description("Get query results from a completed job")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("jobId", "Job ID")
                        .required(),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(1000),
                    FieldDef.integer("startIndex", "Start Index")
                        .withDefault(0)
                ))
                .outputDescription("Returns { rows: [...], schema: {...} }")
                .build(),

            OperationDef.create("cancel", "Cancel Job")
                .description("Cancel a running job")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("jobId", "Job ID")
                        .required()
                ))
                .outputDescription("Returns job status")
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
            String serviceAccountJson = getCredentialValue(credential, "serviceAccountJson");

            if (serviceAccountJson == null || serviceAccountJson.isEmpty()) {
                return NodeExecutionResult.failure("Service account JSON is required");
            }

            String accessToken = getServiceAccountToken(serviceAccountJson);
            String projectId = getProjectId(credential, serviceAccountJson, params);

            return switch (resource) {
                case "query" -> executeQueryOperation(accessToken, projectId, operation, params);
                case "dataset" -> executeDatasetOperation(accessToken, projectId, operation, params);
                case "table" -> executeTableOperation(accessToken, projectId, operation, params);
                case "job" -> executeJobOperation(accessToken, projectId, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("BigQuery API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("BigQuery API error: " + e.getMessage());
        }
    }

    private String getProjectId(Map<String, Object> credential, String serviceAccountJson, Map<String, Object> params) throws Exception {
        String projectId = getParam(params, "projectId", "");
        if (!projectId.isEmpty()) {
            return projectId;
        }

        projectId = getCredentialValue(credential, "projectId");
        if (projectId != null && !projectId.isEmpty()) {
            return projectId;
        }

        Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
        return (String) sa.get("project_id");
    }

    private String getServiceAccountToken(String serviceAccountJson) throws Exception {
        Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
        String clientEmail = (String) sa.get("client_email");
        String privateKey = (String) sa.get("private_key");
        String tokenUri = (String) sa.getOrDefault("token_uri", "https://oauth2.googleapis.com/token");

        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
            "iss", clientEmail,
            "scope", "https://www.googleapis.com/auth/bigquery",
            "aud", tokenUri,
            "iat", now,
            "exp", now + 3600
        );

        String jwt = createJwt(header, claims, privateKey);

        RequestBody body = new FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build();

        Request request = new Request.Builder()
            .url(tokenUri)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body().string(), new TypeReference<>() {});
            return (String) tokenResponse.get("access_token");
        }
    }

    private String createJwt(Map<String, Object> header, Map<String, Object> claims, String privateKeyPem) throws Exception {
        String headerJson = objectMapper.writeValueAsString(header);
        String claimsJson = objectMapper.writeValueAsString(claims);

        String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String claimsBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));

        String signatureInput = headerBase64 + "." + claimsBase64;

        String privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyContent);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
        java.security.PrivateKey pk = keyFactory.generatePrivate(keySpec);

        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(pk);
        signature.update(signatureInput.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();

        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        return signatureInput + "." + signatureBase64;
    }

    // ==================== Query Operations ====================

    private NodeExecutionResult executeQueryOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "execute" -> {
                String query = getRequiredParam(params, "query");
                boolean useLegacySql = getBoolParam(params, "useLegacySql", false);
                int maxResults = getIntParam(params, "maxResults", 1000);
                int timeoutMs = getIntParam(params, "timeoutMs", 60000);
                boolean dryRun = getBoolParam(params, "dryRun", false);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                body.put("useLegacySql", useLegacySql);
                body.put("maxResults", maxResults);
                body.put("timeoutMs", timeoutMs);
                body.put("dryRun", dryRun);

                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/queries";
                NodeExecutionResult result = executePost(url, accessToken, body);

                if (result.isSuccess()) {
                    Map<String, Object> data = result.getOutput();
                    // Transform to more friendly format
                    Map<String, Object> transformed = transformQueryResult(data);
                    yield NodeExecutionResult.success(transformed);
                }
                yield result;
            }
            case "executeAsync" -> {
                String query = getRequiredParam(params, "query");
                boolean useLegacySql = getBoolParam(params, "useLegacySql", false);
                String destinationTable = getParam(params, "destinationTable", "");
                String writeDisposition = getParam(params, "writeDisposition", "WRITE_TRUNCATE");

                Map<String, Object> queryConfig = new LinkedHashMap<>();
                queryConfig.put("query", query);
                queryConfig.put("useLegacySql", useLegacySql);

                if (!destinationTable.isEmpty()) {
                    String[] parts = destinationTable.split("\\.");
                    if (parts.length == 3) {
                        queryConfig.put("destinationTable", Map.of(
                            "projectId", parts[0],
                            "datasetId", parts[1],
                            "tableId", parts[2]
                        ));
                        queryConfig.put("writeDisposition", writeDisposition);
                    }
                }

                Map<String, Object> body = Map.of(
                    "configuration", Map.of("query", queryConfig)
                );

                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/jobs";
                yield executePost(url, accessToken, body);
            }
            default -> NodeExecutionResult.failure("Unknown query operation: " + operation);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> transformQueryResult(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Get schema
        Map<String, Object> schema = (Map<String, Object>) data.get("schema");
        result.put("schema", schema);

        // Get total rows
        Object totalRows = data.get("totalRows");
        result.put("totalRows", totalRows != null ? Long.parseLong(totalRows.toString()) : 0);

        // Transform rows
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        List<Map<String, Object>> fields = schema != null ? (List<Map<String, Object>>) schema.get("fields") : List.of();

        if (rows != null && fields != null) {
            List<Map<String, Object>> transformedRows = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                List<Map<String, Object>> f = (List<Map<String, Object>>) row.get("f");
                if (f != null) {
                    Map<String, Object> transformedRow = new LinkedHashMap<>();
                    for (int i = 0; i < Math.min(f.size(), fields.size()); i++) {
                        String fieldName = (String) fields.get(i).get("name");
                        Object value = f.get(i).get("v");
                        transformedRow.put(fieldName, value);
                    }
                    transformedRows.add(transformedRow);
                }
            }
            result.put("rows", transformedRows);
        } else {
            result.put("rows", List.of());
        }

        // Job info
        if (data.containsKey("jobReference")) {
            result.put("jobReference", data.get("jobReference"));
        }
        if (data.containsKey("jobComplete")) {
            result.put("jobComplete", data.get("jobComplete"));
        }

        return result;
    }

    // ==================== Dataset Operations ====================

    private NodeExecutionResult executeDatasetOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                int maxResults = getIntParam(params, "maxResults", 100);
                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/datasets?maxResults=" + maxResults;
                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/datasets/" + datasetId;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String location = getParam(params, "location", "US");
                String description = getParam(params, "description", "");

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("datasetReference", Map.of(
                    "projectId", projectId,
                    "datasetId", datasetId
                ));
                body.put("location", location);
                if (!description.isEmpty()) {
                    body.put("description", description);
                }

                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/datasets";
                yield executePost(url, accessToken, body);
            }
            case "delete" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                boolean deleteContents = getBoolParam(params, "deleteContents", false);

                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/datasets/" + datasetId
                    + "?deleteContents=" + deleteContents;
                yield executeDelete(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown dataset operation: " + operation);
        };
    }

    // ==================== Table Operations ====================

    private NodeExecutionResult executeTableOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                int maxResults = getIntParam(params, "maxResults", 100);

                String url = BIGQUERY_API_BASE + "/projects/" + projectId
                    + "/datasets/" + datasetId + "/tables?maxResults=" + maxResults;
                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String tableId = getRequiredParam(params, "tableId");

                String url = BIGQUERY_API_BASE + "/projects/" + projectId
                    + "/datasets/" + datasetId + "/tables/" + tableId;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String tableId = getRequiredParam(params, "tableId");
                String schemaJson = getRequiredParam(params, "schema");
                String description = getParam(params, "description", "");

                List<Map<String, Object>> fields = objectMapper.readValue(schemaJson, new TypeReference<>() {});

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("tableReference", Map.of(
                    "projectId", projectId,
                    "datasetId", datasetId,
                    "tableId", tableId
                ));
                body.put("schema", Map.of("fields", fields));
                if (!description.isEmpty()) {
                    body.put("description", description);
                }

                String url = BIGQUERY_API_BASE + "/projects/" + projectId
                    + "/datasets/" + datasetId + "/tables";
                yield executePost(url, accessToken, body);
            }
            case "delete" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String tableId = getRequiredParam(params, "tableId");

                String url = BIGQUERY_API_BASE + "/projects/" + projectId
                    + "/datasets/" + datasetId + "/tables/" + tableId;
                yield executeDelete(url, accessToken);
            }
            case "insert" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String tableId = getRequiredParam(params, "tableId");
                String rowsJson = getRequiredParam(params, "rows");
                boolean skipInvalid = getBoolParam(params, "skipInvalidRows", false);
                boolean ignoreUnknown = getBoolParam(params, "ignoreUnknownValues", false);

                List<Map<String, Object>> rows = objectMapper.readValue(rowsJson, new TypeReference<>() {});

                List<Map<String, Object>> insertRows = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    insertRows.add(Map.of(
                        "insertId", UUID.randomUUID().toString(),
                        "json", row
                    ));
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("rows", insertRows);
                body.put("skipInvalidRows", skipInvalid);
                body.put("ignoreUnknownValues", ignoreUnknown);

                String url = BIGQUERY_API_BASE + "/projects/" + projectId
                    + "/datasets/" + datasetId + "/tables/" + tableId + "/insertAll";
                yield executePost(url, accessToken, body);
            }
            case "getData" -> {
                String datasetId = getRequiredParam(params, "datasetId");
                String tableId = getRequiredParam(params, "tableId");
                int maxResults = getIntParam(params, "maxResults", 1000);
                int startIndex = getIntParam(params, "startIndex", 0);
                String selectedFields = getParam(params, "selectedFields", "");

                StringBuilder url = new StringBuilder(BIGQUERY_API_BASE)
                    .append("/projects/").append(projectId)
                    .append("/datasets/").append(datasetId)
                    .append("/tables/").append(tableId)
                    .append("/data?maxResults=").append(maxResults)
                    .append("&startIndex=").append(startIndex);

                if (!selectedFields.isEmpty()) {
                    url.append("&selectedFields=").append(URLEncoder.encode(selectedFields, StandardCharsets.UTF_8));
                }

                yield executeGet(url.toString(), accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown table operation: " + operation);
        };
    }

    // ==================== Job Operations ====================

    private NodeExecutionResult executeJobOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                int maxResults = getIntParam(params, "maxResults", 100);
                String stateFilter = getParam(params, "stateFilter", "");

                StringBuilder url = new StringBuilder(BIGQUERY_API_BASE)
                    .append("/projects/").append(projectId)
                    .append("/jobs?maxResults=").append(maxResults);

                if (!stateFilter.isEmpty()) {
                    url.append("&stateFilter=").append(stateFilter);
                }

                yield executeGet(url.toString(), accessToken);
            }
            case "get" -> {
                String jobId = getRequiredParam(params, "jobId");
                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/jobs/" + jobId;
                yield executeGet(url, accessToken);
            }
            case "getResults" -> {
                String jobId = getRequiredParam(params, "jobId");
                int maxResults = getIntParam(params, "maxResults", 1000);
                int startIndex = getIntParam(params, "startIndex", 0);

                String url = BIGQUERY_API_BASE + "/projects/" + projectId
                    + "/queries/" + jobId
                    + "?maxResults=" + maxResults
                    + "&startIndex=" + startIndex;

                NodeExecutionResult result = executeGet(url, accessToken);
                if (result.isSuccess()) {
                    Map<String, Object> transformed = transformQueryResult(result.getOutput());
                    yield NodeExecutionResult.success(transformed);
                }
                yield result;
            }
            case "cancel" -> {
                String jobId = getRequiredParam(params, "jobId");
                String url = BIGQUERY_API_BASE + "/projects/" + projectId + "/jobs/" + jobId + "/cancel";
                yield executePost(url, accessToken, Map.of());
            }
            default -> NodeExecutionResult.failure("Unknown job operation: " + operation);
        };
    }

    // ==================== HTTP Helpers ====================

    private NodeExecutionResult executeGet(String url, String accessToken) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .get()
            .build();
        return executeRequest(request);
    }

    private NodeExecutionResult executePost(String url, String accessToken, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .post(requestBody)
            .build();
        return executeRequest(request);
    }

    private NodeExecutionResult executeDelete(String url, String accessToken) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }
            return NodeExecutionResult.success(Map.of("success", true));
        }
    }

    private NodeExecutionResult executeRequest(Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";

            if (!response.isSuccessful()) {
                try {
                    Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});
                    if (result.containsKey("error")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> error = (Map<String, Object>) result.get("error");
                        String message = (String) error.getOrDefault("message", "Unknown error");
                        return NodeExecutionResult.failure("BigQuery API error: " + message);
                    }
                } catch (Exception e) {
                    // Ignore parse error
                }
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }

            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});
            return NodeExecutionResult.success(result);
        }
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(Map.of("name", "input", "type", "any", "required", false)),
            "outputs", List.of(Map.of("name", "output", "type", "object"))
        );
    }
}
