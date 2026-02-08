package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regular expression tool
 * Supports matching, extracting, replacing, and more
 */
@Component
@Slf4j
public class RegexTool implements AgentNodeTool {

    // Security limits
    private static final int MAX_INPUT_LENGTH = 100000;
    private static final int MAX_MATCHES = 1000;

    @Override
    public String getId() {
        return "regex";
    }

    @Override
    public String getName() {
        return "Regex";
    }

    @Override
    public String getDescription() {
        return """
                Regular expression tool, supports multiple operations:
                - match: Check if text matches a pattern
                - find: Find all matching parts
                - extract: Extract capture groups
                - replace: Replace matched parts
                - split: Split text by pattern

                Parameters:
                - text: Text to process
                - pattern: Regular expression pattern
                - operation: Operation type
                - replacement: Replacement text (for replace only)
                - flags: Regex flags (i=case-insensitive, m=multiline, s=dotall)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to process"
                        ),
                        "pattern", Map.of(
                                "type", "string",
                                "description", "Regular expression pattern"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("match", "find", "extract", "replace", "split"),
                                "description", "Operation type",
                                "default", "find"
                        ),
                        "replacement", Map.of(
                                "type", "string",
                                "description", "Replacement text (for replace operation only)"
                        ),
                        "flags", Map.of(
                                "type", "string",
                                "description", "Regex flags (i, m, s)",
                                "default", ""
                        )
                ),
                "required", List.of("text", "pattern")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = (String) parameters.get("text");
                String patternStr = (String) parameters.get("pattern");

                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("Text cannot be empty");
                }
                if (patternStr == null || patternStr.isEmpty()) {
                    return ToolResult.failure("Regex pattern cannot be empty");
                }
                if (text.length() > MAX_INPUT_LENGTH) {
                    return ToolResult.failure("Input text too long (maximum " + MAX_INPUT_LENGTH + " characters)");
                }

                String operation = (String) parameters.getOrDefault("operation", "find");
                String replacement = (String) parameters.get("replacement");
                String flags = (String) parameters.getOrDefault("flags", "");

                // Compile regex
                int patternFlags = 0;
                if (flags.contains("i")) patternFlags |= Pattern.CASE_INSENSITIVE;
                if (flags.contains("m")) patternFlags |= Pattern.MULTILINE;
                if (flags.contains("s")) patternFlags |= Pattern.DOTALL;

                Pattern pattern;
                try {
                    pattern = Pattern.compile(patternStr, patternFlags);
                } catch (PatternSyntaxException e) {
                    return ToolResult.failure("Invalid regular expression");
                }

                return switch (operation) {
                    case "match" -> executeMatch(text, pattern);
                    case "find" -> executeFind(text, pattern);
                    case "extract" -> executeExtract(text, pattern);
                    case "replace" -> executeReplace(text, pattern, replacement);
                    case "split" -> executeSplit(text, pattern);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("Regex operation failed", e);
                return ToolResult.failure("Regex operation failed");
            }
        });
    }

    private ToolResult executeMatch(String text, Pattern pattern) {
        boolean matches = pattern.matcher(text).matches();
        return ToolResult.success(
                matches ? "Text fully matches the pattern" : "Text does not match the pattern",
                Map.of("matches", matches)
        );
    }

    private ToolResult executeFind(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        List<Map<String, Object>> matches = new ArrayList<>();
        int count = 0;

        while (matcher.find() && count < MAX_MATCHES) {
            matches.add(Map.of(
                    "match", matcher.group(),
                    "start", matcher.start(),
                    "end", matcher.end()
            ));
            count++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d matches:\n", matches.size()));
        for (int i = 0; i < Math.min(matches.size(), 10); i++) {
            sb.append(String.format("%d. \"%s\" (position %d-%d)\n",
                    i + 1, matches.get(i).get("match"),
                    matches.get(i).get("start"), matches.get(i).get("end")));
        }
        if (matches.size() > 10) {
            sb.append(String.format("... %d more matches\n", matches.size() - 10));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", matches.size(),
                "matches", matches
        ));
    }

    private ToolResult executeExtract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        List<Map<String, Object>> groups = new ArrayList<>();

        while (matcher.find() && groups.size() < MAX_MATCHES) {
            Map<String, Object> groupMap = new HashMap<>();
            groupMap.put("full", matcher.group());
            for (int i = 1; i <= matcher.groupCount(); i++) {
                groupMap.put("group" + i, matcher.group(i));
            }
            groups.add(groupMap);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Extracted %d capture groups:\n", groups.size()));
        for (int i = 0; i < Math.min(groups.size(), 5); i++) {
            sb.append(String.format("%d. %s\n", i + 1, groups.get(i)));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", groups.size(),
                "groups", groups
        ));
    }

    private ToolResult executeReplace(String text, Pattern pattern, String replacement) {
        if (replacement == null) {
            return ToolResult.failure("The replace operation requires a replacement parameter");
        }
        String result = pattern.matcher(text).replaceAll(replacement);
        return ToolResult.success(
                "Replacement result:\n" + result,
                Map.of("result", result, "original", text)
        );
    }

    private ToolResult executeSplit(String text, Pattern pattern) {
        String[] parts = pattern.split(text);
        List<String> partsList = List.of(parts);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Split into %d parts:\n", parts.length));
        for (int i = 0; i < Math.min(parts.length, 10); i++) {
            sb.append(String.format("%d. \"%s\"\n", i + 1, parts[i]));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", parts.length,
                "parts", partsList
        ));
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
