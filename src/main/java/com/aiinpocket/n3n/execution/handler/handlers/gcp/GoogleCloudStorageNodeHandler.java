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
 * Google Cloud Storage API node handler.
 *
 * Supports bucket and object operations via Cloud Storage JSON API v1.
 *
 * Features:
 * - Bucket operations (list, get, create, delete)
 * - Object operations (list, get, upload, download, copy, delete)
 * - Object metadata management
 * - Signed URL generation
 *
 * Credential schema:
 * - serviceAccountJson: Service Account JSON key
 * - projectId: GCP Project ID (optional, can be in service account)
 *
 * Required IAM roles:
 * - roles/storage.admin (full access)
 * - roles/storage.objectViewer (read-only)
 * - roles/storage.objectCreator (write)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleCloudStorageNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String STORAGE_API_BASE = "https://storage.googleapis.com/storage/v1";
    private static final String UPLOAD_API_BASE = "https://storage.googleapis.com/upload/storage/v1";

    @Override
    public String getType() {
        return "googleCloudStorage";
    }

    @Override
    public String getDisplayName() {
        return "Google Cloud Storage";
    }

    @Override
    public String getDescription() {
        return "Google Cloud Storage API. Manage buckets and objects.";
    }

    @Override
    public String getCategory() {
        return "Cloud Storage";
    }

    @Override
    public String getIcon() {
        return "googleCloudStorage";
    }

    @Override
    public String getCredentialType() {
        return "googleCloudStorage";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("bucket", ResourceDef.of("bucket", "Bucket", "Bucket operations"));
        resources.put("object", ResourceDef.of("object", "Object", "Object operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Bucket operations
        operations.put("bucket", List.of(
            OperationDef.create("list", "List Buckets")
                .description("List all buckets in the project")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID")
                        .withDescription("GCP Project ID (uses service account project if empty)"),
                    FieldDef.string("prefix", "Prefix")
                        .withDescription("Filter buckets by name prefix"),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                        .withRange(1, 1000)
                ))
                .outputDescription("Returns { items: [...] }")
                .build(),

            OperationDef.create("get", "Get Bucket")
                .description("Get bucket metadata")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required()
                ))
                .outputDescription("Returns bucket metadata")
                .build(),

            OperationDef.create("create", "Create Bucket")
                .description("Create a new bucket")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("projectId", "Project ID")
                        .withDescription("GCP Project ID"),
                    FieldDef.string("location", "Location")
                        .withDescription("Bucket location (e.g., US, ASIA, EU)")
                        .withDefault("US"),
                    FieldDef.string("storageClass", "Storage Class")
                        .withOptions(List.of("STANDARD", "NEARLINE", "COLDLINE", "ARCHIVE"))
                        .withDefault("STANDARD"),
                    FieldDef.bool("uniformBucketLevelAccess", "Uniform Access")
                        .withDescription("Enable uniform bucket-level access")
                        .withDefault(true)
                ))
                .outputDescription("Returns created bucket")
                .build(),

            OperationDef.create("delete", "Delete Bucket")
                .description("Delete an empty bucket")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("getIamPolicy", "Get IAM Policy")
                .description("Get bucket IAM policy")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required()
                ))
                .outputDescription("Returns IAM policy")
                .build()
        ));

        // Object operations
        operations.put("object", List.of(
            OperationDef.create("list", "List Objects")
                .description("List objects in a bucket")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("prefix", "Prefix")
                        .withDescription("Filter objects by prefix"),
                    FieldDef.string("delimiter", "Delimiter")
                        .withDescription("Delimiter for hierarchical listing")
                        .withDefault("/"),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(1000)
                        .withRange(1, 1000),
                    FieldDef.bool("versions", "Include Versions")
                        .withDescription("Include object versions")
                        .withDefault(false)
                ))
                .outputDescription("Returns { items: [...], prefixes: [...] }")
                .build(),

            OperationDef.create("get", "Get Object Metadata")
                .description("Get object metadata")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("objectName", "Object Name")
                        .withDescription("Object path/name")
                        .required()
                ))
                .outputDescription("Returns object metadata")
                .build(),

            OperationDef.create("download", "Download Object")
                .description("Download object content (returns base64)")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("objectName", "Object Name")
                        .required()
                ))
                .outputDescription("Returns { content: base64, contentType, size }")
                .build(),

            OperationDef.create("upload", "Upload Object")
                .description("Upload an object")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("objectName", "Object Name")
                        .withDescription("Object path/name")
                        .required(),
                    FieldDef.textarea("content", "Content")
                        .withDescription("Object content (text or base64)")
                        .required(),
                    FieldDef.string("contentType", "Content Type")
                        .withDescription("MIME type")
                        .withDefault("application/octet-stream"),
                    FieldDef.bool("isBase64", "Is Base64")
                        .withDescription("Content is base64 encoded")
                        .withDefault(false),
                    FieldDef.textarea("metadata", "Metadata")
                        .withDescription("Custom metadata as JSON object")
                ))
                .outputDescription("Returns uploaded object metadata")
                .build(),

            OperationDef.create("copy", "Copy Object")
                .description("Copy an object")
                .fields(List.of(
                    FieldDef.string("sourceBucket", "Source Bucket")
                        .required(),
                    FieldDef.string("sourceObject", "Source Object")
                        .required(),
                    FieldDef.string("destinationBucket", "Destination Bucket")
                        .required(),
                    FieldDef.string("destinationObject", "Destination Object")
                        .required()
                ))
                .outputDescription("Returns copied object metadata")
                .build(),

            OperationDef.create("move", "Move Object")
                .description("Move an object (copy then delete)")
                .fields(List.of(
                    FieldDef.string("sourceBucket", "Source Bucket")
                        .required(),
                    FieldDef.string("sourceObject", "Source Object")
                        .required(),
                    FieldDef.string("destinationBucket", "Destination Bucket")
                        .required(),
                    FieldDef.string("destinationObject", "Destination Object")
                        .required()
                ))
                .outputDescription("Returns moved object metadata")
                .build(),

            OperationDef.create("delete", "Delete Object")
                .description("Delete an object")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("objectName", "Object Name")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("updateMetadata", "Update Metadata")
                .description("Update object metadata")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("objectName", "Object Name")
                        .required(),
                    FieldDef.textarea("metadata", "Metadata")
                        .withDescription("Custom metadata as JSON object")
                        .required(),
                    FieldDef.string("contentType", "Content Type")
                        .withDescription("New content type (optional)")
                ))
                .outputDescription("Returns updated object metadata")
                .build(),

            OperationDef.create("makePublic", "Make Public")
                .description("Make object publicly readable")
                .fields(List.of(
                    FieldDef.string("bucketName", "Bucket Name")
                        .required(),
                    FieldDef.string("objectName", "Object Name")
                        .required()
                ))
                .outputDescription("Returns { publicUrl }")
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
            String projectId = getProjectId(credential, serviceAccountJson);

            return switch (resource) {
                case "bucket" -> executeBucketOperation(accessToken, projectId, operation, params);
                case "object" -> executeObjectOperation(accessToken, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Google Cloud Storage API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Google Cloud Storage API error: " + e.getMessage());
        }
    }

    private String getProjectId(Map<String, Object> credential, String serviceAccountJson) throws Exception {
        String projectId = getCredentialValue(credential, "projectId");
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
            "scope", "https://www.googleapis.com/auth/devstorage.full_control",
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

    // ==================== Bucket Operations ====================

    private NodeExecutionResult executeBucketOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String pid = getParam(params, "projectId", projectId);
                String prefix = getParam(params, "prefix", "");
                int maxResults = getIntParam(params, "maxResults", 100);

                StringBuilder url = new StringBuilder(STORAGE_API_BASE + "/b");
                url.append("?project=").append(pid);
                url.append("&maxResults=").append(maxResults);
                if (!prefix.isEmpty()) {
                    url.append("&prefix=").append(URLEncoder.encode(prefix, StandardCharsets.UTF_8));
                }

                yield executeGet(url.toString(), accessToken);
            }
            case "get" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String url = STORAGE_API_BASE + "/b/" + bucketName;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String pid = getParam(params, "projectId", projectId);
                String location = getParam(params, "location", "US");
                String storageClass = getParam(params, "storageClass", "STANDARD");
                boolean uniformAccess = getBoolParam(params, "uniformBucketLevelAccess", true);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", bucketName);
                body.put("location", location);
                body.put("storageClass", storageClass);
                if (uniformAccess) {
                    body.put("iamConfiguration", Map.of(
                        "uniformBucketLevelAccess", Map.of("enabled", true)
                    ));
                }

                String url = STORAGE_API_BASE + "/b?project=" + pid;
                yield executePost(url, accessToken, body);
            }
            case "delete" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String url = STORAGE_API_BASE + "/b/" + bucketName;
                yield executeDelete(url, accessToken);
            }
            case "getIamPolicy" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String url = STORAGE_API_BASE + "/b/" + bucketName + "/iam";
                yield executeGet(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown bucket operation: " + operation);
        };
    }

    // ==================== Object Operations ====================

    private NodeExecutionResult executeObjectOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String prefix = getParam(params, "prefix", "");
                String delimiter = getParam(params, "delimiter", "");
                int maxResults = getIntParam(params, "maxResults", 1000);
                boolean versions = getBoolParam(params, "versions", false);

                StringBuilder url = new StringBuilder(STORAGE_API_BASE + "/b/")
                    .append(bucketName).append("/o")
                    .append("?maxResults=").append(maxResults)
                    .append("&versions=").append(versions);

                if (!prefix.isEmpty()) {
                    url.append("&prefix=").append(URLEncoder.encode(prefix, StandardCharsets.UTF_8));
                }
                if (!delimiter.isEmpty()) {
                    url.append("&delimiter=").append(URLEncoder.encode(delimiter, StandardCharsets.UTF_8));
                }

                yield executeGet(url.toString(), accessToken);
            }
            case "get" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String objectName = getRequiredParam(params, "objectName");

                String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
                String url = STORAGE_API_BASE + "/b/" + bucketName + "/o/" + encodedObject;
                yield executeGet(url, accessToken);
            }
            case "download" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String objectName = getRequiredParam(params, "objectName");

                String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
                String url = STORAGE_API_BASE + "/b/" + bucketName + "/o/" + encodedObject + "?alt=media";

                Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        yield NodeExecutionResult.failure("HTTP " + response.code());
                    }

                    byte[] content = response.body().bytes();
                    String contentType = response.header("Content-Type", "application/octet-stream");

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("content", Base64.getEncoder().encodeToString(content));
                    result.put("contentType", contentType);
                    result.put("size", content.length);
                    result.put("name", objectName);

                    yield NodeExecutionResult.success(result);
                }
            }
            case "upload" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String objectName = getRequiredParam(params, "objectName");
                String content = getRequiredParam(params, "content");
                String contentType = getParam(params, "contentType", "application/octet-stream");
                boolean isBase64 = getBoolParam(params, "isBase64", false);
                String metadataJson = getParam(params, "metadata", "");

                byte[] contentBytes = isBase64 ? Base64.getDecoder().decode(content) : content.getBytes(StandardCharsets.UTF_8);

                String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
                String url = UPLOAD_API_BASE + "/b/" + bucketName + "/o?uploadType=media&name=" + encodedObject;

                RequestBody requestBody = RequestBody.create(contentBytes, MediaType.parse(contentType));

                Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .post(requestBody);

                yield executeRequest(requestBuilder.build());
            }
            case "copy" -> {
                String sourceBucket = getRequiredParam(params, "sourceBucket");
                String sourceObject = getRequiredParam(params, "sourceObject");
                String destBucket = getRequiredParam(params, "destinationBucket");
                String destObject = getRequiredParam(params, "destinationObject");

                String encodedSource = URLEncoder.encode(sourceObject, StandardCharsets.UTF_8);
                String encodedDest = URLEncoder.encode(destObject, StandardCharsets.UTF_8);

                String url = STORAGE_API_BASE + "/b/" + sourceBucket + "/o/" + encodedSource
                    + "/copyTo/b/" + destBucket + "/o/" + encodedDest;

                yield executePost(url, accessToken, Map.of());
            }
            case "move" -> {
                String sourceBucket = getRequiredParam(params, "sourceBucket");
                String sourceObject = getRequiredParam(params, "sourceObject");
                String destBucket = getRequiredParam(params, "destinationBucket");
                String destObject = getRequiredParam(params, "destinationObject");

                // Copy first
                String encodedSource = URLEncoder.encode(sourceObject, StandardCharsets.UTF_8);
                String encodedDest = URLEncoder.encode(destObject, StandardCharsets.UTF_8);

                String copyUrl = STORAGE_API_BASE + "/b/" + sourceBucket + "/o/" + encodedSource
                    + "/copyTo/b/" + destBucket + "/o/" + encodedDest;

                NodeExecutionResult copyResult = executePost(copyUrl, accessToken, Map.of());
                if (!copyResult.isSuccess()) {
                    yield copyResult;
                }

                // Then delete source
                String deleteUrl = STORAGE_API_BASE + "/b/" + sourceBucket + "/o/" + encodedSource;
                executeDelete(deleteUrl, accessToken);

                yield copyResult;
            }
            case "delete" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String objectName = getRequiredParam(params, "objectName");

                String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
                String url = STORAGE_API_BASE + "/b/" + bucketName + "/o/" + encodedObject;

                yield executeDelete(url, accessToken);
            }
            case "updateMetadata" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String objectName = getRequiredParam(params, "objectName");
                String metadataJson = getRequiredParam(params, "metadata");
                String contentType = getParam(params, "contentType", "");

                Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<>() {});

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("metadata", metadata);
                if (!contentType.isEmpty()) {
                    body.put("contentType", contentType);
                }

                String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
                String url = STORAGE_API_BASE + "/b/" + bucketName + "/o/" + encodedObject;

                yield executePatch(url, accessToken, body);
            }
            case "makePublic" -> {
                String bucketName = getRequiredParam(params, "bucketName");
                String objectName = getRequiredParam(params, "objectName");

                String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
                String url = STORAGE_API_BASE + "/b/" + bucketName + "/o/" + encodedObject + "/acl";

                Map<String, Object> body = Map.of(
                    "entity", "allUsers",
                    "role", "READER"
                );

                NodeExecutionResult result = executePost(url, accessToken, body);
                if (result.isSuccess()) {
                    String publicUrl = "https://storage.googleapis.com/" + bucketName + "/" + objectName;
                    yield NodeExecutionResult.success(Map.of("publicUrl", publicUrl));
                }
                yield result;
            }
            default -> NodeExecutionResult.failure("Unknown object operation: " + operation);
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

    private NodeExecutionResult executePatch(String url, String accessToken, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .patch(requestBody)
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
                        return NodeExecutionResult.failure("GCS API error: " + message);
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
