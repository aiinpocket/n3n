package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regular Expression node handler.
 *
 * Supports:
 * - Match: Check if text matches pattern
 * - Find: Find all matches
 * - Replace: Replace matches
 * - Split: Split by pattern
 * - Extract: Extract capture groups
 * - Validate: Validate common patterns (email, URL, etc.)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RegexNodeHandler extends MultiOperationNodeHandler {

    @Override
    public String getType() {
        return "regex";
    }

    @Override
    public String getDisplayName() {
        return "Regex";
    }

    @Override
    public String getDescription() {
        return "Match, find, replace, and validate using regular expressions.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "regex";
    }

    @Override
    public String getCredentialType() {
        return null;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("match", ResourceDef.of("match", "Match", "Match and find patterns"));
        resources.put("replace", ResourceDef.of("replace", "Replace", "Replace text using patterns"));
        resources.put("validate", ResourceDef.of("validate", "Validate", "Validate common formats"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Match operations
        operations.put("match", List.of(
            OperationDef.create("test", "Test Match")
                .description("Check if text matches a pattern")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Text to test")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Regular expression pattern")
                        .withPlaceholder("^[A-Za-z]+$")
                        .required(),
                    FieldDef.bool("ignoreCase", "Ignore Case")
                        .withDefault(false)
                        .withDescription("Case-insensitive matching"),
                    FieldDef.bool("multiline", "Multiline")
                        .withDefault(false)
                        .withDescription("^ and $ match line boundaries")
                ))
                .requiresCredential(false)
                .outputDescription("Returns boolean match result")
                .build(),

            OperationDef.create("find", "Find All")
                .description("Find all matches in text")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Text to search")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Regular expression pattern")
                        .withPlaceholder("\\b\\w+@\\w+\\.\\w+\\b")
                        .required(),
                    FieldDef.bool("ignoreCase", "Ignore Case")
                        .withDefault(false)
                        .withDescription("Case-insensitive matching"),
                    FieldDef.bool("multiline", "Multiline")
                        .withDefault(false)
                        .withDescription("^ and $ match line boundaries")
                ))
                .requiresCredential(false)
                .outputDescription("Returns array of matches")
                .build(),

            OperationDef.create("extract", "Extract Groups")
                .description("Extract capture groups from matches")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Text to search")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Pattern with capture groups")
                        .withPlaceholder("(\\d{4})-(\\d{2})-(\\d{2})")
                        .required(),
                    FieldDef.bool("allMatches", "All Matches")
                        .withDefault(false)
                        .withDescription("Extract from all matches"),
                    FieldDef.bool("namedGroups", "Named Groups")
                        .withDefault(false)
                        .withDescription("Use named groups in output")
                ))
                .requiresCredential(false)
                .outputDescription("Returns capture groups")
                .build(),

            OperationDef.create("split", "Split")
                .description("Split text by pattern")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Text to split")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Separator pattern")
                        .withPlaceholder("[,;\\s]+")
                        .required(),
                    FieldDef.integer("limit", "Limit")
                        .withDefault(0)
                        .withDescription("Maximum splits (0 = unlimited)")
                ))
                .requiresCredential(false)
                .outputDescription("Returns array of parts")
                .build()
        ));

        // Replace operations
        operations.put("replace", List.of(
            OperationDef.create("replaceAll", "Replace All")
                .description("Replace all matches with replacement")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Input text")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Pattern to match")
                        .required(),
                    FieldDef.string("replacement", "Replacement")
                        .withDescription("Replacement text ($1, $2 for groups)")
                        .required(),
                    FieldDef.bool("ignoreCase", "Ignore Case")
                        .withDefault(false)
                        .withDescription("Case-insensitive matching")
                ))
                .requiresCredential(false)
                .outputDescription("Returns replaced text")
                .build(),

            OperationDef.create("replaceFirst", "Replace First")
                .description("Replace first match only")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Input text")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Pattern to match")
                        .required(),
                    FieldDef.string("replacement", "Replacement")
                        .withDescription("Replacement text ($1, $2 for groups)")
                        .required(),
                    FieldDef.bool("ignoreCase", "Ignore Case")
                        .withDefault(false)
                        .withDescription("Case-insensitive matching")
                ))
                .requiresCredential(false)
                .outputDescription("Returns replaced text")
                .build(),

            OperationDef.create("remove", "Remove Matches")
                .description("Remove all matches from text")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Input text")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Pattern to remove")
                        .required(),
                    FieldDef.bool("ignoreCase", "Ignore Case")
                        .withDefault(false)
                        .withDescription("Case-insensitive matching")
                ))
                .requiresCredential(false)
                .outputDescription("Returns text with matches removed")
                .build()
        ));

        // Validate operations
        operations.put("validate", List.of(
            OperationDef.create("email", "Validate Email")
                .description("Check if text is a valid email address")
                .fields(List.of(
                    FieldDef.string("email", "Email")
                        .withDescription("Email address to validate")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns validation result")
                .build(),

            OperationDef.create("url", "Validate URL")
                .description("Check if text is a valid URL")
                .fields(List.of(
                    FieldDef.string("url", "URL")
                        .withDescription("URL to validate")
                        .required(),
                    FieldDef.bool("requireProtocol", "Require Protocol")
                        .withDefault(true)
                        .withDescription("Require http:// or https://")
                ))
                .requiresCredential(false)
                .outputDescription("Returns validation result")
                .build(),

            OperationDef.create("phone", "Validate Phone")
                .description("Check if text is a valid phone number")
                .fields(List.of(
                    FieldDef.string("phone", "Phone Number")
                        .withDescription("Phone number to validate")
                        .required(),
                    FieldDef.select("format", "Format", List.of(
                            "international", "us", "uk", "jp", "tw", "any"
                        ))
                        .withDefault("any")
                        .withDescription("Expected phone format")
                ))
                .requiresCredential(false)
                .outputDescription("Returns validation result")
                .build(),

            OperationDef.create("ipAddress", "Validate IP Address")
                .description("Check if text is a valid IP address")
                .fields(List.of(
                    FieldDef.string("ip", "IP Address")
                        .withDescription("IP address to validate")
                        .required(),
                    FieldDef.select("version", "IP Version", List.of("v4", "v6", "both"))
                        .withDefault("both")
                        .withDescription("IP version to accept")
                ))
                .requiresCredential(false)
                .outputDescription("Returns validation result")
                .build(),

            OperationDef.create("custom", "Custom Pattern")
                .description("Validate with custom pattern")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Text to validate")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Regular expression pattern")
                        .required(),
                    FieldDef.bool("fullMatch", "Full Match")
                        .withDefault(true)
                        .withDescription("Require entire text to match")
                ))
                .requiresCredential(false)
                .outputDescription("Returns validation result")
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
            return switch (resource) {
                case "match" -> switch (operation) {
                    case "test" -> test(params);
                    case "find" -> find(params);
                    case "extract" -> extract(params);
                    case "split" -> split(params);
                    default -> NodeExecutionResult.failure("Unknown match operation: " + operation);
                };
                case "replace" -> switch (operation) {
                    case "replaceAll" -> replaceAll(params);
                    case "replaceFirst" -> replaceFirst(params);
                    case "remove" -> remove(params);
                    default -> NodeExecutionResult.failure("Unknown replace operation: " + operation);
                };
                case "validate" -> switch (operation) {
                    case "email" -> validateEmail(params);
                    case "url" -> validateUrl(params);
                    case "phone" -> validatePhone(params);
                    case "ipAddress" -> validateIpAddress(params);
                    case "custom" -> validateCustom(params);
                    default -> NodeExecutionResult.failure("Unknown validate operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (PatternSyntaxException e) {
            return NodeExecutionResult.failure("Invalid regex pattern: " + e.getMessage());
        } catch (Exception e) {
            log.error("Regex operation error: {}", e.getMessage());
            return NodeExecutionResult.failure("Regex error: " + e.getMessage());
        }
    }

    private int buildFlags(Map<String, Object> params) {
        int flags = 0;
        if (getBoolParam(params, "ignoreCase", false)) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (getBoolParam(params, "multiline", false)) {
            flags |= Pattern.MULTILINE;
        }
        return flags;
    }

    private NodeExecutionResult test(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        int flags = buildFlags(params);

        Pattern pattern = Pattern.compile(patternStr, flags);
        Matcher matcher = pattern.matcher(text);
        boolean matches = matcher.find();

        return NodeExecutionResult.success(Map.of(
            "matches", matches,
            "pattern", patternStr
        ));
    }

    private NodeExecutionResult find(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        int flags = buildFlags(params);

        Pattern pattern = Pattern.compile(patternStr, flags);
        Matcher matcher = pattern.matcher(text);

        List<Map<String, Object>> matches = new ArrayList<>();
        while (matcher.find()) {
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("match", matcher.group());
            match.put("start", matcher.start());
            match.put("end", matcher.end());
            matches.add(match);
        }

        return NodeExecutionResult.success(Map.of(
            "matches", matches,
            "count", matches.size(),
            "found", !matches.isEmpty()
        ));
    }

    private NodeExecutionResult extract(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        boolean allMatches = getBoolParam(params, "allMatches", false);

        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(text);

        if (allMatches) {
            List<List<String>> allGroups = new ArrayList<>();
            while (matcher.find()) {
                List<String> groups = new ArrayList<>();
                for (int i = 0; i <= matcher.groupCount(); i++) {
                    groups.add(matcher.group(i));
                }
                allGroups.add(groups);
            }
            return NodeExecutionResult.success(Map.of(
                "matches", allGroups,
                "count", allGroups.size()
            ));
        } else {
            if (matcher.find()) {
                List<String> groups = new ArrayList<>();
                for (int i = 0; i <= matcher.groupCount(); i++) {
                    groups.add(matcher.group(i));
                }
                return NodeExecutionResult.success(Map.of(
                    "groups", groups,
                    "found", true,
                    "fullMatch", groups.isEmpty() ? "" : groups.get(0)
                ));
            } else {
                return NodeExecutionResult.success(Map.of(
                    "groups", List.of(),
                    "found", false
                ));
            }
        }
    }

    private NodeExecutionResult split(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        int limit = getIntParam(params, "limit", 0);

        Pattern pattern = Pattern.compile(patternStr);
        String[] parts = limit > 0 ? pattern.split(text, limit) : pattern.split(text);

        return NodeExecutionResult.success(Map.of(
            "parts", Arrays.asList(parts),
            "count", parts.length
        ));
    }

    private NodeExecutionResult replaceAll(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        String replacement = getRequiredParam(params, "replacement");
        int flags = buildFlags(params);

        Pattern pattern = Pattern.compile(patternStr, flags);
        String result = pattern.matcher(text).replaceAll(replacement);

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult replaceFirst(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        String replacement = getRequiredParam(params, "replacement");
        int flags = buildFlags(params);

        Pattern pattern = Pattern.compile(patternStr, flags);
        String result = pattern.matcher(text).replaceFirst(replacement);

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult remove(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        int flags = buildFlags(params);

        Pattern pattern = Pattern.compile(patternStr, flags);
        String result = pattern.matcher(text).replaceAll("");

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult validateEmail(Map<String, Object> params) {
        String email = getRequiredParam(params, "email");

        // RFC 5322 compliant email pattern (simplified)
        String pattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        boolean valid = Pattern.matches(pattern, email);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("email", email);

        if (valid) {
            String[] parts = email.split("@");
            result.put("local", parts[0]);
            result.put("domain", parts[1]);
        }

        return NodeExecutionResult.success(result);
    }

    private NodeExecutionResult validateUrl(Map<String, Object> params) {
        String url = getRequiredParam(params, "url");
        boolean requireProtocol = getBoolParam(params, "requireProtocol", true);

        String pattern = requireProtocol
            ? "^https?://[\\w.-]+(?:\\.[a-zA-Z]{2,})+(?:/[\\w./-]*)?(?:\\?[\\w=&]*)?(?:#\\w*)?$"
            : "^(?:https?://)?[\\w.-]+(?:\\.[a-zA-Z]{2,})+(?:/[\\w./-]*)?(?:\\?[\\w=&]*)?(?:#\\w*)?$";

        boolean valid = Pattern.matches(pattern, url);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("url", url);

        return NodeExecutionResult.success(result);
    }

    private NodeExecutionResult validatePhone(Map<String, Object> params) {
        String phone = getRequiredParam(params, "phone");
        String format = getParam(params, "format", "any");

        String pattern = switch (format) {
            case "international" -> "^\\+[1-9]\\d{1,14}$";
            case "us" -> "^(?:\\+1)?[2-9]\\d{2}[2-9]\\d{6}$";
            case "uk" -> "^(?:\\+44)?[1-9]\\d{9,10}$";
            case "jp" -> "^(?:\\+81)?[1-9]\\d{8,9}$";
            case "tw" -> "^(?:\\+886)?[1-9]\\d{8}$";
            default -> "^\\+?[\\d\\s()-]{7,20}$";
        };

        // Normalize phone for matching
        String normalized = phone.replaceAll("[\\s()-]", "");
        boolean valid = Pattern.matches(pattern, normalized);

        return NodeExecutionResult.success(Map.of(
            "valid", valid,
            "phone", phone,
            "normalized", normalized,
            "format", format
        ));
    }

    private NodeExecutionResult validateIpAddress(Map<String, Object> params) {
        String ip = getRequiredParam(params, "ip");
        String version = getParam(params, "version", "both");

        String ipv4Pattern = "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";
        String ipv6Pattern = "^(?:[A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}$|^::(?:[A-Fa-f0-9]{1,4}:){0,6}[A-Fa-f0-9]{1,4}$|^(?:[A-Fa-f0-9]{1,4}:){1,6}::$|^(?:[A-Fa-f0-9]{1,4}:){1,7}:$";

        boolean isV4 = Pattern.matches(ipv4Pattern, ip);
        boolean isV6 = Pattern.matches(ipv6Pattern, ip);

        boolean valid = switch (version) {
            case "v4" -> isV4;
            case "v6" -> isV6;
            default -> isV4 || isV6;
        };

        return NodeExecutionResult.success(Map.of(
            "valid", valid,
            "ip", ip,
            "isIPv4", isV4,
            "isIPv6", isV6,
            "version", isV4 ? "v4" : (isV6 ? "v6" : "unknown")
        ));
    }

    private NodeExecutionResult validateCustom(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        boolean fullMatch = getBoolParam(params, "fullMatch", true);

        Pattern pattern = Pattern.compile(patternStr);
        boolean valid = fullMatch
            ? pattern.matcher(text).matches()
            : pattern.matcher(text).find();

        return NodeExecutionResult.success(Map.of(
            "valid", valid,
            "text", text,
            "pattern", patternStr
        ));
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
}
