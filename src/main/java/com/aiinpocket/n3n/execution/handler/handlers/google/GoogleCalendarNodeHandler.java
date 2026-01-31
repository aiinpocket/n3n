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
 * Google Calendar API node handler.
 *
 * Supports calendar and event operations via Google Calendar API v3.
 *
 * Features:
 * - Calendar listing and management
 * - Event CRUD operations
 * - Free/busy queries
 * - Attendee management
 *
 * Credential schema:
 * - accessToken: OAuth2 access token
 * - serviceAccountJson: Service Account JSON key (alternative)
 *
 * Required OAuth2 scopes:
 * - https://www.googleapis.com/auth/calendar
 * - https://www.googleapis.com/auth/calendar.readonly
 * - https://www.googleapis.com/auth/calendar.events
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleCalendarNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String CALENDAR_API_BASE = "https://www.googleapis.com/calendar/v3";

    @Override
    public String getType() {
        return "googleCalendar";
    }

    @Override
    public String getDisplayName() {
        return "Google Calendar";
    }

    @Override
    public String getDescription() {
        return "Google Calendar API. Manage calendars and events.";
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public String getIcon() {
        return "googleCalendar";
    }

    @Override
    public String getCredentialType() {
        return "googleCalendar";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("calendar", ResourceDef.of("calendar", "Calendar", "Calendar management"));
        resources.put("event", ResourceDef.of("event", "Event", "Event operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Calendar operations
        operations.put("calendar", List.of(
            OperationDef.create("list", "List Calendars")
                .description("List all calendars")
                .fields(List.of(
                    FieldDef.bool("showHidden", "Show Hidden")
                        .withDescription("Include hidden calendars")
                        .withDefault(false),
                    FieldDef.bool("showDeleted", "Show Deleted")
                        .withDescription("Include deleted calendars")
                        .withDefault(false)
                ))
                .outputDescription("Returns { items: [...] }")
                .build(),

            OperationDef.create("get", "Get Calendar")
                .description("Get calendar details")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDescription("Calendar ID (or 'primary' for primary calendar)")
                        .withDefault("primary")
                ))
                .outputDescription("Returns calendar object")
                .build(),

            OperationDef.create("create", "Create Calendar")
                .description("Create a new calendar")
                .fields(List.of(
                    FieldDef.string("summary", "Name")
                        .withDescription("Calendar name")
                        .required(),
                    FieldDef.textarea("description", "Description")
                        .withDescription("Calendar description"),
                    FieldDef.string("timeZone", "Time Zone")
                        .withDescription("Time zone (e.g., Asia/Taipei)")
                        .withDefault("Asia/Taipei")
                ))
                .outputDescription("Returns created calendar")
                .build(),

            OperationDef.create("delete", "Delete Calendar")
                .description("Delete a calendar")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDescription("Calendar ID to delete")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("freeBusy", "Check Free/Busy")
                .description("Check free/busy information")
                .fields(List.of(
                    FieldDef.string("calendarIds", "Calendar IDs")
                        .withDescription("Comma-separated calendar IDs")
                        .withDefault("primary"),
                    FieldDef.string("timeMin", "Start Time")
                        .withDescription("Start of the interval (ISO 8601)")
                        .required(),
                    FieldDef.string("timeMax", "End Time")
                        .withDescription("End of the interval (ISO 8601)")
                        .required()
                ))
                .outputDescription("Returns { calendars: { ... } }")
                .build()
        ));

        // Event operations
        operations.put("event", List.of(
            OperationDef.create("list", "List Events")
                .description("List events from a calendar")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("timeMin", "Start Time")
                        .withDescription("Events after this time (ISO 8601)"),
                    FieldDef.string("timeMax", "End Time")
                        .withDescription("Events before this time (ISO 8601)"),
                    FieldDef.string("query", "Search Query")
                        .withDescription("Free text search terms"),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(250)
                        .withRange(1, 2500),
                    FieldDef.bool("singleEvents", "Single Events")
                        .withDescription("Expand recurring events")
                        .withDefault(true),
                    FieldDef.string("orderBy", "Order By")
                        .withOptions(List.of("startTime", "updated"))
                        .withDefault("startTime")
                ))
                .outputDescription("Returns { items: [...] }")
                .build(),

            OperationDef.create("get", "Get Event")
                .description("Get event details")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("eventId", "Event ID")
                        .withDescription("Event ID")
                        .required()
                ))
                .outputDescription("Returns event object")
                .build(),

            OperationDef.create("create", "Create Event")
                .description("Create a new event")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("summary", "Title")
                        .withDescription("Event title")
                        .required(),
                    FieldDef.textarea("description", "Description")
                        .withDescription("Event description"),
                    FieldDef.string("location", "Location")
                        .withDescription("Event location"),
                    FieldDef.string("startDateTime", "Start Time")
                        .withDescription("Start time (ISO 8601, e.g., 2024-01-15T10:00:00+08:00)")
                        .required(),
                    FieldDef.string("endDateTime", "End Time")
                        .withDescription("End time (ISO 8601)")
                        .required(),
                    FieldDef.string("timeZone", "Time Zone")
                        .withDefault("Asia/Taipei"),
                    FieldDef.textarea("attendees", "Attendees")
                        .withDescription("Comma-separated email addresses"),
                    FieldDef.bool("sendNotifications", "Send Notifications")
                        .withDescription("Send email notifications to attendees")
                        .withDefault(true),
                    FieldDef.string("recurrence", "Recurrence")
                        .withDescription("RRULE (e.g., RRULE:FREQ=WEEKLY;COUNT=10)")
                ))
                .outputDescription("Returns created event")
                .build(),

            OperationDef.create("update", "Update Event")
                .description("Update an existing event")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("eventId", "Event ID")
                        .required(),
                    FieldDef.string("summary", "Title"),
                    FieldDef.textarea("description", "Description"),
                    FieldDef.string("location", "Location"),
                    FieldDef.string("startDateTime", "Start Time"),
                    FieldDef.string("endDateTime", "End Time"),
                    FieldDef.string("timeZone", "Time Zone"),
                    FieldDef.textarea("attendees", "Attendees"),
                    FieldDef.bool("sendNotifications", "Send Notifications")
                        .withDefault(true)
                ))
                .outputDescription("Returns updated event")
                .build(),

            OperationDef.create("delete", "Delete Event")
                .description("Delete an event")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("eventId", "Event ID")
                        .required(),
                    FieldDef.bool("sendNotifications", "Send Notifications")
                        .withDefault(true)
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("quickAdd", "Quick Add")
                .description("Create event from natural language text")
                .fields(List.of(
                    FieldDef.string("calendarId", "Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("text", "Text")
                        .withDescription("Event text (e.g., 'Meeting at 3pm tomorrow')")
                        .required(),
                    FieldDef.bool("sendNotifications", "Send Notifications")
                        .withDefault(true)
                ))
                .outputDescription("Returns created event")
                .build(),

            OperationDef.create("move", "Move Event")
                .description("Move event to another calendar")
                .fields(List.of(
                    FieldDef.string("calendarId", "Source Calendar ID")
                        .withDefault("primary"),
                    FieldDef.string("eventId", "Event ID")
                        .required(),
                    FieldDef.string("destinationCalendarId", "Destination Calendar ID")
                        .required(),
                    FieldDef.bool("sendNotifications", "Send Notifications")
                        .withDefault(true)
                ))
                .outputDescription("Returns moved event")
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
                case "calendar" -> executeCalendarOperation(accessToken, operation, params);
                case "event" -> executeEventOperation(accessToken, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Google Calendar API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Google Calendar API error: " + e.getMessage());
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
            "scope", "https://www.googleapis.com/auth/calendar",
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

    // ==================== Calendar Operations ====================

    private NodeExecutionResult executeCalendarOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                boolean showHidden = getBoolParam(params, "showHidden", false);
                boolean showDeleted = getBoolParam(params, "showDeleted", false);

                String url = CALENDAR_API_BASE + "/users/me/calendarList"
                    + "?showHidden=" + showHidden
                    + "&showDeleted=" + showDeleted;

                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String encodedId = URLEncoder.encode(calendarId, StandardCharsets.UTF_8);
                String url = CALENDAR_API_BASE + "/calendars/" + encodedId;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String summary = getRequiredParam(params, "summary");
                String description = getParam(params, "description", "");
                String timeZone = getParam(params, "timeZone", "Asia/Taipei");

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("summary", summary);
                if (!description.isEmpty()) {
                    body.put("description", description);
                }
                body.put("timeZone", timeZone);

                String url = CALENDAR_API_BASE + "/calendars";
                yield executePost(url, accessToken, body);
            }
            case "delete" -> {
                String calendarId = getRequiredParam(params, "calendarId");
                String encodedId = URLEncoder.encode(calendarId, StandardCharsets.UTF_8);
                String url = CALENDAR_API_BASE + "/calendars/" + encodedId;
                yield executeDelete(url, accessToken);
            }
            case "freeBusy" -> {
                String calendarIdsStr = getParam(params, "calendarIds", "primary");
                String timeMin = getRequiredParam(params, "timeMin");
                String timeMax = getRequiredParam(params, "timeMax");

                String[] ids = calendarIdsStr.split(",");
                List<Map<String, String>> items = new ArrayList<>();
                for (String id : ids) {
                    items.add(Map.of("id", id.trim()));
                }

                Map<String, Object> body = Map.of(
                    "timeMin", timeMin,
                    "timeMax", timeMax,
                    "items", items
                );

                String url = CALENDAR_API_BASE + "/freeBusy";
                yield executePost(url, accessToken, body);
            }
            default -> NodeExecutionResult.failure("Unknown calendar operation: " + operation);
        };
    }

    // ==================== Event Operations ====================

    private NodeExecutionResult executeEventOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String timeMin = getParam(params, "timeMin", "");
                String timeMax = getParam(params, "timeMax", "");
                String query = getParam(params, "query", "");
                int maxResults = getIntParam(params, "maxResults", 250);
                boolean singleEvents = getBoolParam(params, "singleEvents", true);
                String orderBy = getParam(params, "orderBy", "startTime");

                StringBuilder urlBuilder = new StringBuilder(CALENDAR_API_BASE)
                    .append("/calendars/")
                    .append(URLEncoder.encode(calendarId, StandardCharsets.UTF_8))
                    .append("/events")
                    .append("?maxResults=").append(maxResults)
                    .append("&singleEvents=").append(singleEvents);

                if (singleEvents) {
                    urlBuilder.append("&orderBy=").append(orderBy);
                }
                if (!timeMin.isEmpty()) {
                    urlBuilder.append("&timeMin=").append(URLEncoder.encode(timeMin, StandardCharsets.UTF_8));
                }
                if (!timeMax.isEmpty()) {
                    urlBuilder.append("&timeMax=").append(URLEncoder.encode(timeMax, StandardCharsets.UTF_8));
                }
                if (!query.isEmpty()) {
                    urlBuilder.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
                }

                yield executeGet(urlBuilder.toString(), accessToken);
            }
            case "get" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String eventId = getRequiredParam(params, "eventId");

                String url = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/" + eventId;

                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String summary = getRequiredParam(params, "summary");
                String description = getParam(params, "description", "");
                String location = getParam(params, "location", "");
                String startDateTime = getRequiredParam(params, "startDateTime");
                String endDateTime = getRequiredParam(params, "endDateTime");
                String timeZone = getParam(params, "timeZone", "Asia/Taipei");
                String attendeesStr = getParam(params, "attendees", "");
                boolean sendNotifications = getBoolParam(params, "sendNotifications", true);
                String recurrence = getParam(params, "recurrence", "");

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("summary", summary);
                if (!description.isEmpty()) {
                    body.put("description", description);
                }
                if (!location.isEmpty()) {
                    body.put("location", location);
                }
                body.put("start", Map.of("dateTime", startDateTime, "timeZone", timeZone));
                body.put("end", Map.of("dateTime", endDateTime, "timeZone", timeZone));

                if (!attendeesStr.isEmpty()) {
                    String[] emails = attendeesStr.split(",");
                    List<Map<String, String>> attendees = new ArrayList<>();
                    for (String email : emails) {
                        attendees.add(Map.of("email", email.trim()));
                    }
                    body.put("attendees", attendees);
                }

                if (!recurrence.isEmpty()) {
                    body.put("recurrence", List.of(recurrence));
                }

                String url = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events?sendUpdates=" + (sendNotifications ? "all" : "none");

                yield executePost(url, accessToken, body);
            }
            case "update" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String eventId = getRequiredParam(params, "eventId");
                boolean sendNotifications = getBoolParam(params, "sendNotifications", true);

                // First get existing event
                String getUrl = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/" + eventId;

                Request getRequest = new Request.Builder()
                    .url(getUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

                Map<String, Object> existingEvent;
                try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                    existingEvent = objectMapper.readValue(getResponse.body().string(), new TypeReference<>() {});
                }

                // Update fields
                String summary = getParam(params, "summary", "");
                String description = getParam(params, "description", "");
                String location = getParam(params, "location", "");
                String startDateTime = getParam(params, "startDateTime", "");
                String endDateTime = getParam(params, "endDateTime", "");
                String timeZone = getParam(params, "timeZone", "");
                String attendeesStr = getParam(params, "attendees", "");

                if (!summary.isEmpty()) {
                    existingEvent.put("summary", summary);
                }
                if (!description.isEmpty()) {
                    existingEvent.put("description", description);
                }
                if (!location.isEmpty()) {
                    existingEvent.put("location", location);
                }
                if (!startDateTime.isEmpty()) {
                    String tz = !timeZone.isEmpty() ? timeZone : "Asia/Taipei";
                    existingEvent.put("start", Map.of("dateTime", startDateTime, "timeZone", tz));
                }
                if (!endDateTime.isEmpty()) {
                    String tz = !timeZone.isEmpty() ? timeZone : "Asia/Taipei";
                    existingEvent.put("end", Map.of("dateTime", endDateTime, "timeZone", tz));
                }
                if (!attendeesStr.isEmpty()) {
                    String[] emails = attendeesStr.split(",");
                    List<Map<String, String>> attendees = new ArrayList<>();
                    for (String email : emails) {
                        attendees.add(Map.of("email", email.trim()));
                    }
                    existingEvent.put("attendees", attendees);
                }

                String updateUrl = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/" + eventId
                    + "?sendUpdates=" + (sendNotifications ? "all" : "none");

                yield executePut(updateUrl, accessToken, existingEvent);
            }
            case "delete" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String eventId = getRequiredParam(params, "eventId");
                boolean sendNotifications = getBoolParam(params, "sendNotifications", true);

                String url = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/" + eventId
                    + "?sendUpdates=" + (sendNotifications ? "all" : "none");

                yield executeDelete(url, accessToken);
            }
            case "quickAdd" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String text = getRequiredParam(params, "text");
                boolean sendNotifications = getBoolParam(params, "sendNotifications", true);

                String url = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/quickAdd"
                    + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&sendUpdates=" + (sendNotifications ? "all" : "none");

                yield executePost(url, accessToken, Map.of());
            }
            case "move" -> {
                String calendarId = getParam(params, "calendarId", "primary");
                String eventId = getRequiredParam(params, "eventId");
                String destinationId = getRequiredParam(params, "destinationCalendarId");
                boolean sendNotifications = getBoolParam(params, "sendNotifications", true);

                String url = CALENDAR_API_BASE + "/calendars/"
                    + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/" + eventId + "/move"
                    + "?destination=" + URLEncoder.encode(destinationId, StandardCharsets.UTF_8)
                    + "&sendUpdates=" + (sendNotifications ? "all" : "none");

                yield executePost(url, accessToken, Map.of());
            }
            default -> NodeExecutionResult.failure("Unknown event operation: " + operation);
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
                        return NodeExecutionResult.failure("Google Calendar API error: " + message);
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
