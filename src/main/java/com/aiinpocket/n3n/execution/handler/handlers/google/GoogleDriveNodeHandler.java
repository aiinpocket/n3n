package com.aiinpocket.n3n.execution.handler.handlers.google;

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
 * Google Drive API node handler.
 *
 * Supports file and folder operations via Google Drive API v3.
 *
 * Features:
 * - File operations (list, get, create, copy, delete, move)
 * - Folder operations (create, list contents)
 * - Download and upload files
 * - Permission management
 *
 * Credential schema:
 * - accessToken: OAuth2 access token
 * - serviceAccountJson: Service Account JSON key (alternative)
 *
 * Required OAuth2 scopes:
 * - https://www.googleapis.com/auth/drive
 * - https://www.googleapis.com/auth/drive.file (limited)
 * - https://www.googleapis.com/auth/drive.readonly (read-only)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleDriveNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String DRIVE_API_BASE = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD_API_BASE = "https://www.googleapis.com/upload/drive/v3";

    @Override
    public String getType() {
        return "googleDrive";
    }

    @Override
    public String getDisplayName() {
        return "Google Drive";
    }

    @Override
    public String getDescription() {
        return "Google Drive API. Manage files and folders in Google Drive.";
    }

    @Override
    public String getCategory() {
        return "Storage";
    }

    @Override
    public String getIcon() {
        return "googleDrive";
    }

    @Override
    public String getCredentialType() {
        return "googleDrive";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("file", ResourceDef.of("file", "File", "File operations"));
        resources.put("folder", ResourceDef.of("folder", "Folder", "Folder operations"));
        resources.put("permission", ResourceDef.of("permission", "Permission", "Sharing and permission management"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // File operations
        operations.put("file", List.of(
            OperationDef.create("list", "List Files")
                .description("List files in Drive or folder")
                .fields(List.of(
                    FieldDef.string("folderId", "Folder ID")
                        .withDescription("Parent folder ID (empty for root)"),
                    FieldDef.string("query", "Query")
                        .withDescription("Search query (Drive API query format)"),
                    FieldDef.integer("pageSize", "Page Size")
                        .withDefault(100)
                        .withRange(1, 1000),
                    FieldDef.string("orderBy", "Order By")
                        .withDescription("Sort order (e.g., 'createdTime desc')")
                        .withDefault("modifiedTime desc"),
                    FieldDef.string("fields", "Fields")
                        .withDescription("Fields to include")
                        .withDefault("files(id,name,mimeType,size,createdTime,modifiedTime,parents,webViewLink)")
                ))
                .outputDescription("Returns { files: [...], nextPageToken }")
                .build(),

            OperationDef.create("get", "Get File")
                .description("Get file metadata")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,name,mimeType,size,createdTime,modifiedTime,parents,webViewLink,webContentLink")
                ))
                .outputDescription("Returns file metadata")
                .build(),

            OperationDef.create("download", "Download File")
                .description("Download file content (returns base64)")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required()
                ))
                .outputDescription("Returns { content: base64, mimeType, name }")
                .build(),

            OperationDef.create("create", "Create File")
                .description("Create a new file with content")
                .fields(List.of(
                    FieldDef.string("name", "File Name")
                        .withDescription("Name of the file")
                        .required(),
                    FieldDef.textarea("content", "Content")
                        .withDescription("File content (text or base64)")
                        .required(),
                    FieldDef.string("mimeType", "MIME Type")
                        .withDescription("File MIME type")
                        .withDefault("text/plain"),
                    FieldDef.string("folderId", "Parent Folder ID")
                        .withDescription("Folder to create file in"),
                    FieldDef.bool("isBase64", "Is Base64")
                        .withDescription("Content is base64 encoded")
                        .withDefault(false)
                ))
                .outputDescription("Returns { id, name, mimeType, webViewLink }")
                .build(),

            OperationDef.create("update", "Update File")
                .description("Update file content")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required(),
                    FieldDef.textarea("content", "Content")
                        .withDescription("New file content")
                        .required(),
                    FieldDef.string("mimeType", "MIME Type")
                        .withDescription("File MIME type"),
                    FieldDef.bool("isBase64", "Is Base64")
                        .withDefault(false)
                ))
                .outputDescription("Returns updated file metadata")
                .build(),

            OperationDef.create("copy", "Copy File")
                .description("Create a copy of a file")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The source file ID")
                        .required(),
                    FieldDef.string("name", "New Name")
                        .withDescription("Name for the copy"),
                    FieldDef.string("folderId", "Target Folder ID")
                        .withDescription("Folder to copy to")
                ))
                .outputDescription("Returns new file metadata")
                .build(),

            OperationDef.create("move", "Move File")
                .description("Move file to another folder")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required(),
                    FieldDef.string("folderId", "Target Folder ID")
                        .withDescription("Folder to move to")
                        .required()
                ))
                .outputDescription("Returns updated file metadata")
                .build(),

            OperationDef.create("delete", "Delete File")
                .description("Delete a file")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("trash", "Move to Trash")
                .description("Move file to trash")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required()
                ))
                .outputDescription("Returns updated file metadata")
                .build()
        ));

        // Folder operations
        operations.put("folder", List.of(
            OperationDef.create("create", "Create Folder")
                .description("Create a new folder")
                .fields(List.of(
                    FieldDef.string("name", "Folder Name")
                        .withDescription("Name of the folder")
                        .required(),
                    FieldDef.string("parentId", "Parent Folder ID")
                        .withDescription("Parent folder ID (empty for root)")
                ))
                .outputDescription("Returns { id, name, mimeType, webViewLink }")
                .build(),

            OperationDef.create("list", "List Folder Contents")
                .description("List files and folders in a folder")
                .fields(List.of(
                    FieldDef.string("folderId", "Folder ID")
                        .withDescription("Folder ID to list (empty for root)"),
                    FieldDef.integer("pageSize", "Page Size")
                        .withDefault(100)
                        .withRange(1, 1000),
                    FieldDef.bool("includeSubfolders", "Include Subfolders")
                        .withDefault(false)
                ))
                .outputDescription("Returns { files: [...] }")
                .build()
        ));

        // Permission operations
        operations.put("permission", List.of(
            OperationDef.create("list", "List Permissions")
                .description("List file permissions")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required()
                ))
                .outputDescription("Returns { permissions: [...] }")
                .build(),

            OperationDef.create("create", "Create Permission")
                .description("Share file with user or domain")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required(),
                    FieldDef.string("type", "Type")
                        .withDescription("Permission type")
                        .withOptions(List.of("user", "group", "domain", "anyone"))
                        .required(),
                    FieldDef.string("role", "Role")
                        .withDescription("Permission role")
                        .withOptions(List.of("reader", "commenter", "writer", "organizer", "owner"))
                        .required(),
                    FieldDef.string("emailAddress", "Email Address")
                        .withDescription("Email for user/group type"),
                    FieldDef.string("domain", "Domain")
                        .withDescription("Domain for domain type"),
                    FieldDef.bool("sendNotificationEmail", "Send Email")
                        .withDefault(true)
                ))
                .outputDescription("Returns permission object")
                .build(),

            OperationDef.create("delete", "Delete Permission")
                .description("Remove file permission")
                .fields(List.of(
                    FieldDef.string("fileId", "File ID")
                        .withDescription("The file ID")
                        .required(),
                    FieldDef.string("permissionId", "Permission ID")
                        .withDescription("The permission ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
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
            String accessToken = getAccessToken(credential);

            if (accessToken == null || accessToken.isEmpty()) {
                return NodeExecutionResult.failure("Access token is required");
            }

            return switch (resource) {
                case "file" -> executeFileOperation(accessToken, operation, params);
                case "folder" -> executeFolderOperation(accessToken, operation, params);
                case "permission" -> executePermissionOperation(accessToken, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Google Drive API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Google Drive API error: " + e.getMessage());
        }
    }

    private String getAccessToken(Map<String, Object> credential) throws Exception {
        String accessToken = getCredentialValue(credential, "accessToken");
        if (accessToken != null && !accessToken.isEmpty()) {
            return accessToken;
        }

        String serviceAccountJson = getCredentialValue(credential, "serviceAccountJson");
        if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
            return getServiceAccountToken(serviceAccountJson);
        }

        return null;
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
            "scope", "https://www.googleapis.com/auth/drive",
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
            String responseBody = response.body() != null ? response.body().string() : "{}";
            Map<String, Object> tokenResponse = objectMapper.readValue(responseBody, new TypeReference<>() {});
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

    // ==================== File Operations ====================

    private NodeExecutionResult executeFileOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String folderId = getParam(params, "folderId", "");
                String query = getParam(params, "query", "");
                int pageSize = getIntParam(params, "pageSize", 100);
                String orderBy = getParam(params, "orderBy", "modifiedTime desc");
                String fields = getParam(params, "fields", "files(id,name,mimeType,size,createdTime,modifiedTime,parents,webViewLink)");

                StringBuilder queryBuilder = new StringBuilder();
                if (!folderId.isEmpty()) {
                    queryBuilder.append("'").append(folderId).append("' in parents");
                }
                if (!query.isEmpty()) {
                    if (!queryBuilder.isEmpty()) {
                        queryBuilder.append(" and ");
                    }
                    queryBuilder.append(query);
                }
                queryBuilder.append(" and trashed = false");

                String url = DRIVE_API_BASE + "/files?pageSize=" + pageSize
                    + "&orderBy=" + URLEncoder.encode(orderBy, StandardCharsets.UTF_8)
                    + "&fields=nextPageToken," + URLEncoder.encode(fields, StandardCharsets.UTF_8);

                if (!queryBuilder.isEmpty()) {
                    url += "&q=" + URLEncoder.encode(queryBuilder.toString(), StandardCharsets.UTF_8);
                }

                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String fileId = getRequiredParam(params, "fileId");
                String fields = getParam(params, "fields", "id,name,mimeType,size,createdTime,modifiedTime,parents,webViewLink,webContentLink");

                String url = DRIVE_API_BASE + "/files/" + fileId + "?fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8);
                yield executeGet(url, accessToken);
            }
            case "download" -> {
                String fileId = getRequiredParam(params, "fileId");

                // First get metadata
                String metaUrl = DRIVE_API_BASE + "/files/" + fileId + "?fields=name,mimeType";
                Request metaRequest = new Request.Builder()
                    .url(metaUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

                Map<String, Object> meta;
                try (Response metaResponse = httpClient.newCall(metaRequest).execute()) {
                    meta = objectMapper.readValue(metaResponse.body().string(), new TypeReference<>() {});
                }

                // Download content
                String downloadUrl = DRIVE_API_BASE + "/files/" + fileId + "?alt=media";
                Request downloadRequest = new Request.Builder()
                    .url(downloadUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

                try (Response response = httpClient.newCall(downloadRequest).execute()) {
                    if (!response.isSuccessful()) {
                        yield NodeExecutionResult.failure("Download failed: HTTP " + response.code());
                    }

                    byte[] content = response.body().bytes();
                    String base64 = Base64.getEncoder().encodeToString(content);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("content", base64);
                    result.put("mimeType", meta.get("mimeType"));
                    result.put("name", meta.get("name"));
                    result.put("size", content.length);

                    yield NodeExecutionResult.success(result);
                }
            }
            case "create" -> {
                String name = getRequiredParam(params, "name");
                String content = getRequiredParam(params, "content");
                String mimeType = getParam(params, "mimeType", "text/plain");
                String folderId = getParam(params, "folderId", "");
                boolean isBase64 = getBoolParam(params, "isBase64", false);

                byte[] contentBytes = isBase64 ? Base64.getDecoder().decode(content) : content.getBytes(StandardCharsets.UTF_8);

                // Create metadata
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("name", name);
                if (!folderId.isEmpty()) {
                    metadata.put("parents", List.of(folderId));
                }

                String url = UPLOAD_API_BASE + "/files?uploadType=multipart&fields=id,name,mimeType,webViewLink";

                String boundary = "====" + System.currentTimeMillis() + "====";
                StringBuilder bodyBuilder = new StringBuilder();
                bodyBuilder.append("--").append(boundary).append("\r\n");
                bodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
                bodyBuilder.append(objectMapper.writeValueAsString(metadata)).append("\r\n");
                bodyBuilder.append("--").append(boundary).append("\r\n");
                bodyBuilder.append("Content-Type: ").append(mimeType).append("\r\n\r\n");

                byte[] headerBytes = bodyBuilder.toString().getBytes(StandardCharsets.UTF_8);
                byte[] footerBytes = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
                byte[] fullBody = new byte[headerBytes.length + contentBytes.length + footerBytes.length];
                System.arraycopy(headerBytes, 0, fullBody, 0, headerBytes.length);
                System.arraycopy(contentBytes, 0, fullBody, headerBytes.length, contentBytes.length);
                System.arraycopy(footerBytes, 0, fullBody, headerBytes.length + contentBytes.length, footerBytes.length);

                RequestBody requestBody = RequestBody.create(fullBody, MediaType.parse("multipart/related; boundary=" + boundary));

                Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .post(requestBody)
                    .build();

                yield executeRequest(request);
            }
            case "update" -> {
                String fileId = getRequiredParam(params, "fileId");
                String content = getRequiredParam(params, "content");
                String mimeType = getParam(params, "mimeType", "text/plain");
                boolean isBase64 = getBoolParam(params, "isBase64", false);

                byte[] contentBytes = isBase64 ? Base64.getDecoder().decode(content) : content.getBytes(StandardCharsets.UTF_8);

                String url = UPLOAD_API_BASE + "/files/" + fileId + "?uploadType=media&fields=id,name,mimeType,modifiedTime";

                RequestBody requestBody = RequestBody.create(contentBytes, MediaType.parse(mimeType));

                Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .patch(requestBody)
                    .build();

                yield executeRequest(request);
            }
            case "copy" -> {
                String fileId = getRequiredParam(params, "fileId");
                String name = getParam(params, "name", "");
                String folderId = getParam(params, "folderId", "");

                Map<String, Object> body = new LinkedHashMap<>();
                if (!name.isEmpty()) {
                    body.put("name", name);
                }
                if (!folderId.isEmpty()) {
                    body.put("parents", List.of(folderId));
                }

                String url = DRIVE_API_BASE + "/files/" + fileId + "/copy?fields=id,name,mimeType,webViewLink";
                yield executePost(url, accessToken, body);
            }
            case "move" -> {
                String fileId = getRequiredParam(params, "fileId");
                String targetFolderId = getRequiredParam(params, "folderId");

                // Get current parents
                String getUrl = DRIVE_API_BASE + "/files/" + fileId + "?fields=parents";
                Request getRequest = new Request.Builder()
                    .url(getUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

                String currentParents;
                try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                    Map<String, Object> fileInfo = objectMapper.readValue(getResponse.body().string(), new TypeReference<>() {});
                    @SuppressWarnings("unchecked")
                    List<String> parents = (List<String>) fileInfo.get("parents");
                    currentParents = parents != null ? String.join(",", parents) : "";
                }

                String url = DRIVE_API_BASE + "/files/" + fileId
                    + "?addParents=" + targetFolderId
                    + "&removeParents=" + currentParents
                    + "&fields=id,name,parents";

                yield executePatch(url, accessToken, Map.of());
            }
            case "delete" -> {
                String fileId = getRequiredParam(params, "fileId");
                String url = DRIVE_API_BASE + "/files/" + fileId;

                yield executeDelete(url, accessToken);
            }
            case "trash" -> {
                String fileId = getRequiredParam(params, "fileId");
                String url = DRIVE_API_BASE + "/files/" + fileId + "?fields=id,name,trashed";

                yield executePatch(url, accessToken, Map.of("trashed", true));
            }
            default -> NodeExecutionResult.failure("Unknown file operation: " + operation);
        };
    }

    // ==================== Folder Operations ====================

    private NodeExecutionResult executeFolderOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> {
                String name = getRequiredParam(params, "name");
                String parentId = getParam(params, "parentId", "");

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", name);
                body.put("mimeType", "application/vnd.google-apps.folder");
                if (!parentId.isEmpty()) {
                    body.put("parents", List.of(parentId));
                }

                String url = DRIVE_API_BASE + "/files?fields=id,name,mimeType,webViewLink";
                yield executePost(url, accessToken, body);
            }
            case "list" -> {
                String folderId = getParam(params, "folderId", "root");
                int pageSize = getIntParam(params, "pageSize", 100);
                boolean includeSubfolders = getBoolParam(params, "includeSubfolders", false);

                String query = "'" + folderId + "' in parents and trashed = false";
                if (!includeSubfolders) {
                    // Only if not including subfolders, we still list all types
                }

                String url = DRIVE_API_BASE + "/files?pageSize=" + pageSize
                    + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&fields=nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,webViewLink)";

                yield executeGet(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown folder operation: " + operation);
        };
    }

    // ==================== Permission Operations ====================

    private NodeExecutionResult executePermissionOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String fileId = getRequiredParam(params, "fileId");
                String url = DRIVE_API_BASE + "/files/" + fileId + "/permissions?fields=permissions(id,type,role,emailAddress,domain)";
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String fileId = getRequiredParam(params, "fileId");
                String type = getRequiredParam(params, "type");
                String role = getRequiredParam(params, "role");
                String emailAddress = getParam(params, "emailAddress", "");
                String domain = getParam(params, "domain", "");
                boolean sendNotification = getBoolParam(params, "sendNotificationEmail", true);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("type", type);
                body.put("role", role);
                if (!emailAddress.isEmpty()) {
                    body.put("emailAddress", emailAddress);
                }
                if (!domain.isEmpty()) {
                    body.put("domain", domain);
                }

                String url = DRIVE_API_BASE + "/files/" + fileId + "/permissions"
                    + "?sendNotificationEmail=" + sendNotification
                    + "&fields=id,type,role,emailAddress";

                yield executePost(url, accessToken, body);
            }
            case "delete" -> {
                String fileId = getRequiredParam(params, "fileId");
                String permissionId = getRequiredParam(params, "permissionId");
                String url = DRIVE_API_BASE + "/files/" + fileId + "/permissions/" + permissionId;
                yield executeDelete(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown permission operation: " + operation);
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
                        return NodeExecutionResult.failure("Google Drive API error: " + message);
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
