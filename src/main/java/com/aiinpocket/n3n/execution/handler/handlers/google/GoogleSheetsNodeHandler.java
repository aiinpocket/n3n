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
 * Google Sheets API node handler.
 *
 * Supports spreadsheet operations via Google Sheets API v4.
 *
 * Features:
 * - Spreadsheet management (get, create)
 * - Sheet operations (read, append, update, clear)
 * - Batch operations for efficiency
 *
 * Credential schema:
 * - accessToken: OAuth2 access token (for user context)
 * - refreshToken: OAuth2 refresh token (optional)
 * - clientId: OAuth2 client ID (for token refresh)
 * - clientSecret: OAuth2 client secret (for token refresh)
 * - serviceAccountJson: Service Account JSON key (alternative to OAuth2)
 *
 * Required OAuth2 scopes:
 * - https://www.googleapis.com/auth/spreadsheets
 * - https://www.googleapis.com/auth/spreadsheets.readonly (read-only)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleSheetsNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets";

    @Override
    public String getType() {
        return "googleSheets";
    }

    @Override
    public String getDisplayName() {
        return "Google Sheets";
    }

    @Override
    public String getDescription() {
        return "Google Sheets API. Read and write spreadsheet data.";
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public String getIcon() {
        return "googleSheets";
    }

    @Override
    public String getCredentialType() {
        return "googleSheets";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("spreadsheet", ResourceDef.of("spreadsheet", "Spreadsheet", "Spreadsheet metadata operations"));
        resources.put("sheet", ResourceDef.of("sheet", "Sheet", "Sheet data operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Spreadsheet operations
        operations.put("spreadsheet", List.of(
            OperationDef.create("get", "Get Spreadsheet")
                .description("Get spreadsheet metadata")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.bool("includeGridData", "Include Grid Data")
                        .withDescription("Include cell data in response")
                        .withDefault(false)
                ))
                .outputDescription("Returns spreadsheet metadata")
                .build(),

            OperationDef.create("create", "Create Spreadsheet")
                .description("Create a new spreadsheet")
                .fields(List.of(
                    FieldDef.string("title", "Title")
                        .withDescription("Spreadsheet title")
                        .required(),
                    FieldDef.string("sheetTitle", "Sheet Title")
                        .withDescription("First sheet title")
                        .withDefault("Sheet1")
                ))
                .outputDescription("Returns { spreadsheetId, spreadsheetUrl, sheets }")
                .build()
        ));

        // Sheet operations
        operations.put("sheet", List.of(
            OperationDef.create("read", "Read Data")
                .description("Read data from a range")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.string("range", "Range")
                        .withDescription("A1 notation range (e.g., Sheet1!A1:D10)")
                        .required(),
                    FieldDef.string("valueRenderOption", "Value Render")
                        .withDescription("How values should be rendered")
                        .withOptions(List.of("FORMATTED_VALUE", "UNFORMATTED_VALUE", "FORMULA"))
                        .withDefault("FORMATTED_VALUE"),
                    FieldDef.string("dateTimeRenderOption", "DateTime Render")
                        .withDescription("How dates should be rendered")
                        .withOptions(List.of("SERIAL_NUMBER", "FORMATTED_STRING"))
                        .withDefault("FORMATTED_STRING")
                ))
                .outputDescription("Returns { values: [[...], [...]] }")
                .build(),

            OperationDef.create("append", "Append Data")
                .description("Append rows to a sheet")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.string("range", "Range")
                        .withDescription("A1 notation range (e.g., Sheet1)")
                        .required(),
                    FieldDef.textarea("values", "Values")
                        .withDescription("JSON array of rows: [[\"a\",\"b\"],[\"c\",\"d\"]]")
                        .required(),
                    FieldDef.string("valueInputOption", "Value Input")
                        .withDescription("How input data should be interpreted")
                        .withOptions(List.of("RAW", "USER_ENTERED"))
                        .withDefault("USER_ENTERED"),
                    FieldDef.string("insertDataOption", "Insert Option")
                        .withDescription("How input data should be inserted")
                        .withOptions(List.of("OVERWRITE", "INSERT_ROWS"))
                        .withDefault("INSERT_ROWS")
                ))
                .outputDescription("Returns { updatedRange, updatedRows, updatedColumns, updatedCells }")
                .build(),

            OperationDef.create("update", "Update Data")
                .description("Update data in a range")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.string("range", "Range")
                        .withDescription("A1 notation range (e.g., Sheet1!A1:D10)")
                        .required(),
                    FieldDef.textarea("values", "Values")
                        .withDescription("JSON array of rows: [[\"a\",\"b\"],[\"c\",\"d\"]]")
                        .required(),
                    FieldDef.string("valueInputOption", "Value Input")
                        .withDescription("How input data should be interpreted")
                        .withOptions(List.of("RAW", "USER_ENTERED"))
                        .withDefault("USER_ENTERED")
                ))
                .outputDescription("Returns { updatedRange, updatedRows, updatedColumns, updatedCells }")
                .build(),

            OperationDef.create("clear", "Clear Data")
                .description("Clear data from a range")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.string("range", "Range")
                        .withDescription("A1 notation range to clear")
                        .required()
                ))
                .outputDescription("Returns { clearedRange }")
                .build(),

            OperationDef.create("batchGet", "Batch Get")
                .description("Read multiple ranges at once")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.textarea("ranges", "Ranges")
                        .withDescription("Comma-separated ranges (e.g., Sheet1!A1:B2,Sheet1!C1:D2)")
                        .required(),
                    FieldDef.string("valueRenderOption", "Value Render")
                        .withOptions(List.of("FORMATTED_VALUE", "UNFORMATTED_VALUE", "FORMULA"))
                        .withDefault("FORMATTED_VALUE")
                ))
                .outputDescription("Returns { valueRanges: [...] }")
                .build(),

            OperationDef.create("batchUpdate", "Batch Update")
                .description("Update multiple ranges at once")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.textarea("data", "Data")
                        .withDescription("JSON array: [{\"range\":\"A1:B2\",\"values\":[[1,2]]}]")
                        .required(),
                    FieldDef.string("valueInputOption", "Value Input")
                        .withOptions(List.of("RAW", "USER_ENTERED"))
                        .withDefault("USER_ENTERED")
                ))
                .outputDescription("Returns { totalUpdatedRows, totalUpdatedColumns, totalUpdatedCells }")
                .build(),

            OperationDef.create("addSheet", "Add Sheet")
                .description("Add a new sheet to the spreadsheet")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.string("title", "Sheet Title")
                        .withDescription("Title for the new sheet")
                        .required(),
                    FieldDef.integer("index", "Index")
                        .withDescription("Position of the new sheet (0 = first)")
                        .withDefault(0)
                ))
                .outputDescription("Returns { sheetId, title, index }")
                .build(),

            OperationDef.create("deleteSheet", "Delete Sheet")
                .description("Delete a sheet from the spreadsheet")
                .fields(List.of(
                    FieldDef.string("spreadsheetId", "Spreadsheet ID")
                        .withDescription("The ID of the spreadsheet")
                        .required(),
                    FieldDef.integer("sheetId", "Sheet ID")
                        .withDescription("The ID of the sheet to delete")
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
                case "spreadsheet" -> executeSpreadsheetOperation(accessToken, operation, params);
                case "sheet" -> executeSheetOperation(accessToken, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Google Sheets API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Google Sheets API error: " + e.getMessage());
        }
    }

    /**
     * Get access token from credential (supports OAuth2 or Service Account).
     */
    private String getAccessToken(Map<String, Object> credential) throws Exception {
        // Try direct access token first
        String accessToken = getCredentialValue(credential, "accessToken");
        if (accessToken != null && !accessToken.isEmpty()) {
            return accessToken;
        }

        // Try service account JSON
        String serviceAccountJson = getCredentialValue(credential, "serviceAccountJson");
        if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
            return getServiceAccountToken(serviceAccountJson);
        }

        return null;
    }

    /**
     * Get access token using service account JSON.
     */
    private String getServiceAccountToken(String serviceAccountJson) throws Exception {
        // Parse service account JSON
        Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
        String clientEmail = (String) sa.get("client_email");
        String privateKey = (String) sa.get("private_key");
        String tokenUri = (String) sa.getOrDefault("token_uri", "https://oauth2.googleapis.com/token");

        if (clientEmail == null || privateKey == null) {
            throw new IllegalStateException("Invalid service account JSON: missing client_email or private_key");
        }

        // Create JWT
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
            "iss", clientEmail,
            "scope", "https://www.googleapis.com/auth/spreadsheets",
            "aud", tokenUri,
            "iat", now,
            "exp", now + 3600
        );

        String jwt = createJwt(header, claims, privateKey);

        // Exchange JWT for access token
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

    /**
     * Create a JWT token for service account authentication.
     */
    private String createJwt(Map<String, Object> header, Map<String, Object> claims, String privateKeyPem) throws Exception {
        String headerJson = objectMapper.writeValueAsString(header);
        String claimsJson = objectMapper.writeValueAsString(claims);

        String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String claimsBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));

        String signatureInput = headerBase64 + "." + claimsBase64;

        // Parse private key
        String privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyContent);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
        java.security.PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        // Sign
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signatureInput.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();

        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        return signatureInput + "." + signatureBase64;
    }

    // ==================== Spreadsheet Operations ====================

    private NodeExecutionResult executeSpreadsheetOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "get" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                boolean includeGridData = getBoolParam(params, "includeGridData", false);

                String url = SHEETS_API_BASE + "/" + spreadsheetId;
                if (includeGridData) {
                    url += "?includeGridData=true";
                }

                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String title = getRequiredParam(params, "title");
                String sheetTitle = getParam(params, "sheetTitle", "Sheet1");

                Map<String, Object> requestBody = Map.of(
                    "properties", Map.of("title", title),
                    "sheets", List.of(
                        Map.of("properties", Map.of("title", sheetTitle))
                    )
                );

                yield executePost(SHEETS_API_BASE, accessToken, requestBody);
            }
            default -> NodeExecutionResult.failure("Unknown spreadsheet operation: " + operation);
        };
    }

    // ==================== Sheet Operations ====================

    private NodeExecutionResult executeSheetOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "read" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String range = getRequiredParam(params, "range");
                String valueRenderOption = getParam(params, "valueRenderOption", "FORMATTED_VALUE");
                String dateTimeRenderOption = getParam(params, "dateTimeRenderOption", "FORMATTED_STRING");

                String encodedRange = URLEncoder.encode(range, StandardCharsets.UTF_8);
                String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + encodedRange
                    + "?valueRenderOption=" + valueRenderOption
                    + "&dateTimeRenderOption=" + dateTimeRenderOption;

                yield executeGet(url, accessToken);
            }
            case "append" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String range = getRequiredParam(params, "range");
                String valuesJson = getRequiredParam(params, "values");
                String valueInputOption = getParam(params, "valueInputOption", "USER_ENTERED");
                String insertDataOption = getParam(params, "insertDataOption", "INSERT_ROWS");

                List<List<Object>> values = objectMapper.readValue(valuesJson, new TypeReference<>() {});
                String encodedRange = URLEncoder.encode(range, StandardCharsets.UTF_8);
                String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + encodedRange + ":append"
                    + "?valueInputOption=" + valueInputOption
                    + "&insertDataOption=" + insertDataOption;

                yield executePost(url, accessToken, Map.of("values", values));
            }
            case "update" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String range = getRequiredParam(params, "range");
                String valuesJson = getRequiredParam(params, "values");
                String valueInputOption = getParam(params, "valueInputOption", "USER_ENTERED");

                List<List<Object>> values = objectMapper.readValue(valuesJson, new TypeReference<>() {});
                String encodedRange = URLEncoder.encode(range, StandardCharsets.UTF_8);
                String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + encodedRange
                    + "?valueInputOption=" + valueInputOption;

                yield executePut(url, accessToken, Map.of("values", values));
            }
            case "clear" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String range = getRequiredParam(params, "range");

                String encodedRange = URLEncoder.encode(range, StandardCharsets.UTF_8);
                String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + encodedRange + ":clear";

                yield executePost(url, accessToken, Map.of());
            }
            case "batchGet" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String rangesStr = getRequiredParam(params, "ranges");
                String valueRenderOption = getParam(params, "valueRenderOption", "FORMATTED_VALUE");

                String[] ranges = rangesStr.split(",");
                StringBuilder urlBuilder = new StringBuilder(SHEETS_API_BASE + "/" + spreadsheetId + "/values:batchGet");
                urlBuilder.append("?valueRenderOption=").append(valueRenderOption);
                for (String r : ranges) {
                    urlBuilder.append("&ranges=").append(URLEncoder.encode(r.trim(), StandardCharsets.UTF_8));
                }

                yield executeGet(urlBuilder.toString(), accessToken);
            }
            case "batchUpdate" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String dataJson = getRequiredParam(params, "data");
                String valueInputOption = getParam(params, "valueInputOption", "USER_ENTERED");

                List<Map<String, Object>> data = objectMapper.readValue(dataJson, new TypeReference<>() {});
                String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values:batchUpdate";

                yield executePost(url, accessToken, Map.of(
                    "valueInputOption", valueInputOption,
                    "data", data
                ));
            }
            case "addSheet" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                String title = getRequiredParam(params, "title");
                int index = getIntParam(params, "index", 0);

                String url = SHEETS_API_BASE + "/" + spreadsheetId + ":batchUpdate";
                Map<String, Object> requestBody = Map.of(
                    "requests", List.of(
                        Map.of("addSheet", Map.of(
                            "properties", Map.of(
                                "title", title,
                                "index", index
                            )
                        ))
                    )
                );

                yield executePost(url, accessToken, requestBody);
            }
            case "deleteSheet" -> {
                String spreadsheetId = getRequiredParam(params, "spreadsheetId");
                int sheetId = getIntParam(params, "sheetId", -1);

                if (sheetId < 0) {
                    yield NodeExecutionResult.failure("Sheet ID is required");
                }

                String url = SHEETS_API_BASE + "/" + spreadsheetId + ":batchUpdate";
                Map<String, Object> requestBody = Map.of(
                    "requests", List.of(
                        Map.of("deleteSheet", Map.of("sheetId", sheetId))
                    )
                );

                yield executePost(url, accessToken, requestBody);
            }
            default -> NodeExecutionResult.failure("Unknown sheet operation: " + operation);
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

    private NodeExecutionResult executePut(String url, String accessToken, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .put(requestBody)
            .build();

        return executeRequest(request);
    }

    private NodeExecutionResult executeRequest(Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});

            if (!response.isSuccessful()) {
                if (result.containsKey("error")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) result.get("error");
                    String message = (String) error.getOrDefault("message", "Unknown error");
                    return NodeExecutionResult.failure("Google Sheets API error: " + message);
                }
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }

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
