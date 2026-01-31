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

/**
 * Text manipulation node handler.
 *
 * Supports:
 * - Split: Split string into array
 * - Join: Join array into string
 * - Replace: Replace text
 * - Template: Template rendering with variables
 * - Transform: Case conversion, trim, etc.
 * - Extract: Substring, regex extraction
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TextNodeHandler extends MultiOperationNodeHandler {

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public String getDisplayName() {
        return "Text";
    }

    @Override
    public String getDescription() {
        return "Manipulate, transform, and format text strings.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "text";
    }

    @Override
    public String getCredentialType() {
        return null;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("transform", ResourceDef.of("transform", "Transform", "Transform and modify text"));
        resources.put("split", ResourceDef.of("split", "Split & Join", "Split and join text"));
        resources.put("extract", ResourceDef.of("extract", "Extract", "Extract parts of text"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Transform operations
        operations.put("transform", List.of(
            OperationDef.create("replace", "Replace")
                .description("Replace text within a string")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.string("search", "Search")
                        .withDescription("Text to search for")
                        .required(),
                    FieldDef.string("replaceWith", "Replace With")
                        .withDescription("Replacement text")
                        .required(),
                    FieldDef.bool("replaceAll", "Replace All")
                        .withDefault(true)
                        .withDescription("Replace all occurrences"),
                    FieldDef.bool("ignoreCase", "Ignore Case")
                        .withDefault(false)
                        .withDescription("Case-insensitive matching")
                ))
                .requiresCredential(false)
                .outputDescription("Returns modified text")
                .build(),

            OperationDef.create("template", "Template")
                .description("Render a template with variables")
                .fields(List.of(
                    FieldDef.textarea("template", "Template")
                        .withDescription("Template with {{variable}} placeholders")
                        .withPlaceholder("Hello {{name}}, welcome to {{place}}!")
                        .required(),
                    FieldDef.textarea("variables", "Variables")
                        .withDescription("Variables as JSON object")
                        .withPlaceholder("{\"name\": \"John\", \"place\": \"N3N\"}")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns rendered text")
                .build(),

            OperationDef.create("case", "Change Case")
                .description("Change text case (upper, lower, title, etc.)")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.select("caseType", "Case Type", List.of(
                            "uppercase", "lowercase", "titlecase", "sentencecase", "camelcase", "snakecase", "kebabcase"
                        ))
                        .withDefault("uppercase")
                        .withDescription("Type of case conversion")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns case-converted text")
                .build(),

            OperationDef.create("trim", "Trim")
                .description("Remove whitespace from text")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.select("trimType", "Trim Type", List.of(
                            "both", "start", "end", "all"
                        ))
                        .withDefault("both")
                        .withDescription("Where to trim (all removes all spaces)")
                ))
                .requiresCredential(false)
                .outputDescription("Returns trimmed text")
                .build(),

            OperationDef.create("pad", "Pad")
                .description("Pad text to a specific length")
                .fields(List.of(
                    FieldDef.string("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.integer("length", "Length")
                        .withDescription("Target length")
                        .required(),
                    FieldDef.string("padChar", "Pad Character")
                        .withDefault(" ")
                        .withDescription("Character to pad with"),
                    FieldDef.select("position", "Position", List.of("start", "end"))
                        .withDefault("start")
                        .withDescription("Where to add padding")
                ))
                .requiresCredential(false)
                .outputDescription("Returns padded text")
                .build()
        ));

        // Split/Join operations
        operations.put("split", List.of(
            OperationDef.create("split", "Split")
                .description("Split a string into an array")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.string("separator", "Separator")
                        .withDefault(",")
                        .withDescription("Separator to split on")
                        .required(),
                    FieldDef.bool("trim", "Trim Items")
                        .withDefault(true)
                        .withDescription("Trim whitespace from each item"),
                    FieldDef.bool("removeEmpty", "Remove Empty")
                        .withDefault(true)
                        .withDescription("Remove empty items")
                ))
                .requiresCredential(false)
                .outputDescription("Returns array of strings")
                .build(),

            OperationDef.create("join", "Join")
                .description("Join an array into a string")
                .fields(List.of(
                    FieldDef.textarea("array", "Array")
                        .withDescription("Array as JSON (e.g., [\"a\", \"b\", \"c\"])")
                        .withPlaceholder("[\"item1\", \"item2\", \"item3\"]")
                        .required(),
                    FieldDef.string("separator", "Separator")
                        .withDefault(", ")
                        .withDescription("Separator between items")
                ))
                .requiresCredential(false)
                .outputDescription("Returns joined string")
                .build(),

            OperationDef.create("lines", "Split Lines")
                .description("Split text into lines")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.bool("removeEmpty", "Remove Empty Lines")
                        .withDefault(false)
                        .withDescription("Remove empty lines")
                ))
                .requiresCredential(false)
                .outputDescription("Returns array of lines")
                .build()
        ));

        // Extract operations
        operations.put("extract", List.of(
            OperationDef.create("substring", "Substring")
                .description("Extract a substring")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.integer("start", "Start Index")
                        .withDefault(0)
                        .withDescription("Start index (0-based)")
                        .required(),
                    FieldDef.integer("end", "End Index")
                        .withDescription("End index (optional, defaults to end of string)")
                ))
                .requiresCredential(false)
                .outputDescription("Returns substring")
                .build(),

            OperationDef.create("regex", "Regex Extract")
                .description("Extract text using regular expression")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Regular expression pattern")
                        .withPlaceholder("(\\d+)")
                        .required(),
                    FieldDef.bool("all", "Find All")
                        .withDefault(false)
                        .withDescription("Find all matches"),
                    FieldDef.bool("groups", "Include Groups")
                        .withDefault(true)
                        .withDescription("Include capture groups")
                ))
                .requiresCredential(false)
                .outputDescription("Returns matched text or array")
                .build(),

            OperationDef.create("between", "Extract Between")
                .description("Extract text between two markers")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required(),
                    FieldDef.string("start", "Start Marker")
                        .withDescription("Start marker text")
                        .required(),
                    FieldDef.string("end", "End Marker")
                        .withDescription("End marker text")
                        .required(),
                    FieldDef.bool("all", "Find All")
                        .withDefault(false)
                        .withDescription("Find all occurrences")
                ))
                .requiresCredential(false)
                .outputDescription("Returns extracted text")
                .build(),

            OperationDef.create("length", "Length")
                .description("Get the length of text")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("The input text")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns character count")
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
                case "transform" -> switch (operation) {
                    case "replace" -> replace(params);
                    case "template" -> template(params);
                    case "case" -> changeCase(params);
                    case "trim" -> trim(params);
                    case "pad" -> pad(params);
                    default -> NodeExecutionResult.failure("Unknown transform operation: " + operation);
                };
                case "split" -> switch (operation) {
                    case "split" -> split(params);
                    case "join" -> join(params);
                    case "lines" -> splitLines(params);
                    default -> NodeExecutionResult.failure("Unknown split operation: " + operation);
                };
                case "extract" -> switch (operation) {
                    case "substring" -> substring(params);
                    case "regex" -> regexExtract(params);
                    case "between" -> extractBetween(params);
                    case "length" -> length(params);
                    default -> NodeExecutionResult.failure("Unknown extract operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Text operation error: {}", e.getMessage());
            return NodeExecutionResult.failure("Text error: " + e.getMessage());
        }
    }

    private NodeExecutionResult replace(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String search = getRequiredParam(params, "search");
        String replaceWith = getParam(params, "replaceWith", "");
        boolean replaceAll = getBoolParam(params, "replaceAll", true);
        boolean ignoreCase = getBoolParam(params, "ignoreCase", false);

        String result;
        if (ignoreCase) {
            int flags = replaceAll ? Pattern.CASE_INSENSITIVE : Pattern.CASE_INSENSITIVE | Pattern.LITERAL;
            result = replaceAll
                ? text.replaceAll("(?i)" + Pattern.quote(search), Matcher.quoteReplacement(replaceWith))
                : text.replaceFirst("(?i)" + Pattern.quote(search), Matcher.quoteReplacement(replaceWith));
        } else {
            result = replaceAll
                ? text.replace(search, replaceWith)
                : text.replaceFirst(Pattern.quote(search), Matcher.quoteReplacement(replaceWith));
        }

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult template(Map<String, Object> params) throws Exception {
        String templateStr = getRequiredParam(params, "template");
        String variablesStr = getRequiredParam(params, "variables");

        // Parse variables as JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = mapper.readValue(variablesStr, Map.class);

        // Replace {{variable}} placeholders
        String result = templateStr;
        Pattern pattern = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
        Matcher matcher = pattern.matcher(templateStr);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        result = sb.toString();

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult changeCase(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String caseType = getParam(params, "caseType", "uppercase");

        String result = switch (caseType) {
            case "uppercase" -> text.toUpperCase();
            case "lowercase" -> text.toLowerCase();
            case "titlecase" -> toTitleCase(text);
            case "sentencecase" -> toSentenceCase(text);
            case "camelcase" -> toCamelCase(text);
            case "snakecase" -> toSnakeCase(text);
            case "kebabcase" -> toKebabCase(text);
            default -> text;
        };

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private String toTitleCase(String text) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    private String toSentenceCase(String text) {
        if (text.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : text.toCharArray()) {
            if (c == '.' || c == '!' || c == '?') {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    private String toCamelCase(String text) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : text.toCharArray()) {
            if (c == ' ' || c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    private String toSnakeCase(String text) {
        return text.replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("[\\s-]+", "_")
            .toLowerCase();
    }

    private String toKebabCase(String text) {
        return text.replaceAll("([a-z])([A-Z])", "$1-$2")
            .replaceAll("[\\s_]+", "-")
            .toLowerCase();
    }

    private NodeExecutionResult trim(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String trimType = getParam(params, "trimType", "both");

        String result = switch (trimType) {
            case "start" -> text.stripLeading();
            case "end" -> text.stripTrailing();
            case "all" -> text.replaceAll("\\s+", "");
            default -> text.strip();
        };

        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult pad(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        int length = getIntParam(params, "length", 0);
        String padChar = getParam(params, "padChar", " ");
        String position = getParam(params, "position", "start");

        if (text.length() >= length) {
            return NodeExecutionResult.success(Map.of("text", text));
        }

        int padLength = length - text.length();
        String padding = String.valueOf(padChar.charAt(0)).repeat(padLength);

        String result = "start".equals(position) ? padding + text : text + padding;
        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult split(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String separator = getParam(params, "separator", ",");
        boolean trim = getBoolParam(params, "trim", true);
        boolean removeEmpty = getBoolParam(params, "removeEmpty", true);

        String[] parts = text.split(Pattern.quote(separator));
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            String item = trim ? part.trim() : part;
            if (!removeEmpty || !item.isEmpty()) {
                result.add(item);
            }
        }

        return NodeExecutionResult.success(Map.of(
            "items", result,
            "count", result.size()
        ));
    }

    private NodeExecutionResult join(Map<String, Object> params) throws Exception {
        String arrayStr = getRequiredParam(params, "array");
        String separator = getParam(params, "separator", ", ");

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<?> array = mapper.readValue(arrayStr, List.class);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) result.append(separator);
            result.append(array.get(i));
        }

        return NodeExecutionResult.success(Map.of("text", result.toString()));
    }

    private NodeExecutionResult splitLines(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        boolean removeEmpty = getBoolParam(params, "removeEmpty", false);

        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();

        for (String line : lines) {
            if (!removeEmpty || !line.trim().isEmpty()) {
                result.add(line);
            }
        }

        return NodeExecutionResult.success(Map.of(
            "lines", result,
            "count", result.size()
        ));
    }

    private NodeExecutionResult substring(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        int start = getIntParam(params, "start", 0);
        int end = getIntParam(params, "end", -1);

        start = Math.max(0, Math.min(start, text.length()));
        end = end < 0 ? text.length() : Math.min(end, text.length());

        String result = text.substring(start, end);
        return NodeExecutionResult.success(Map.of("text", result));
    }

    private NodeExecutionResult regexExtract(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String patternStr = getRequiredParam(params, "pattern");
        boolean findAll = getBoolParam(params, "all", false);
        boolean includeGroups = getBoolParam(params, "groups", true);

        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(text);

        if (findAll) {
            List<Object> matches = new ArrayList<>();
            while (matcher.find()) {
                if (includeGroups && matcher.groupCount() > 0) {
                    List<String> groups = new ArrayList<>();
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        groups.add(matcher.group(i));
                    }
                    matches.add(groups);
                } else {
                    matches.add(matcher.group());
                }
            }
            return NodeExecutionResult.success(Map.of(
                "matches", matches,
                "count", matches.size()
            ));
        } else {
            if (matcher.find()) {
                if (includeGroups && matcher.groupCount() > 0) {
                    List<String> groups = new ArrayList<>();
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        groups.add(matcher.group(i));
                    }
                    return NodeExecutionResult.success(Map.of(
                        "match", matcher.group(),
                        "groups", groups,
                        "found", true
                    ));
                } else {
                    return NodeExecutionResult.success(Map.of(
                        "match", matcher.group(),
                        "found", true
                    ));
                }
            } else {
                return NodeExecutionResult.success(Map.of(
                    "match", "",
                    "found", false
                ));
            }
        }
    }

    private NodeExecutionResult extractBetween(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        String startMarker = getRequiredParam(params, "start");
        String endMarker = getRequiredParam(params, "end");
        boolean findAll = getBoolParam(params, "all", false);

        String patternStr = Pattern.quote(startMarker) + "(.*?)" + Pattern.quote(endMarker);
        Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (findAll) {
            List<String> matches = new ArrayList<>();
            while (matcher.find()) {
                matches.add(matcher.group(1));
            }
            return NodeExecutionResult.success(Map.of(
                "matches", matches,
                "count", matches.size()
            ));
        } else {
            if (matcher.find()) {
                return NodeExecutionResult.success(Map.of(
                    "text", matcher.group(1),
                    "found", true
                ));
            } else {
                return NodeExecutionResult.success(Map.of(
                    "text", "",
                    "found", false
                ));
            }
        }
    }

    private NodeExecutionResult length(Map<String, Object> params) {
        String text = getRequiredParam(params, "text");
        return NodeExecutionResult.success(Map.of(
            "length", text.length(),
            "words", text.split("\\s+").length,
            "lines", text.split("\\r?\\n").length
        ));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
