package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TextNodeHandlerTest {

    private TextNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TextNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsText() {
        assertThat(handler.getType()).isEqualTo("text");
    }

    @Test
    void getCategory_returnsData() {
        assertThat(handler.getCategory()).isEqualTo("Data");
    }

    @Test
    void getDisplayName_returnsText() {
        assertThat(handler.getDisplayName()).isEqualTo("Text");
    }

    @Test
    void getCredentialType_returnsNull() {
        assertThat(handler.getCredentialType()).isNull();
    }

    @Test
    void getResources_containsExpectedResources() {
        var resources = handler.getResources();
        assertThat(resources).containsKey("transform");
        assertThat(resources).containsKey("split");
        assertThat(resources).containsKey("extract");
    }

    @Test
    void getOperations_transformHasExpectedOps() {
        var operations = handler.getOperations();
        var transformOps = operations.get("transform");
        assertThat(transformOps).isNotNull();
        var opNames = transformOps.stream().map(op -> op.getName()).toList();
        assertThat(opNames).containsExactly("replace", "template", "case", "trim", "pad");
    }

    // ========== Transform: Replace ==========

    @Test
    void execute_replaceAll_replacesAllOccurrences() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "replace",
                "text", "hello world hello",
                "search", "hello",
                "replaceWith", "hi",
                "replaceAll", true,
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hi world hi");
    }

    @Test
    void execute_replaceIgnoreCase_replacesRegardlessOfCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "replace",
                "text", "Hello HELLO hello",
                "search", "hello",
                "replaceWith", "hi",
                "replaceAll", true,
                "ignoreCase", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hi hi hi");
    }

    @Test
    void execute_replaceFirstOnly_replacesOnlyFirst() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "replace",
                "text", "aaa bbb aaa",
                "search", "aaa",
                "replaceWith", "ccc",
                "replaceAll", false,
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "ccc bbb aaa");
    }

    // ========== Transform: Template ==========

    @Test
    void execute_templateRendersVariables() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "template",
                "template", "Hello {{name}}, welcome to {{place}}!",
                "variables", "{\"name\": \"Alice\", \"place\": \"N3N\"}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "Hello Alice, welcome to N3N!");
    }

    @Test
    void execute_templateMissingVariable_replacesWithEmpty() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "template",
                "template", "Hello {{name}}, your id is {{id}}",
                "variables", "{\"name\": \"Bob\"}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("text").toString()).contains("Hello Bob");
        // Missing {{id}} should be replaced with empty string
        assertThat(result.getOutput().get("text").toString()).contains("your id is ");
    }

    // ========== Transform: Case ==========

    @Test
    void execute_caseUppercase_convertsToUppercase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "case",
                "text", "hello world",
                "caseType", "uppercase"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "HELLO WORLD");
    }

    @Test
    void execute_caseLowercase_convertsToLowercase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "case",
                "text", "HELLO WORLD",
                "caseType", "lowercase"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello world");
    }

    @Test
    void execute_caseTitlecase_convertsToTitleCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "case",
                "text", "hello world example",
                "caseType", "titlecase"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "Hello World Example");
    }

    @Test
    void execute_caseCamelcase_convertsToCamelCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "case",
                "text", "hello world example",
                "caseType", "camelcase"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "helloWorldExample");
    }

    @Test
    void execute_caseSnakecase_convertsToSnakeCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "case",
                "text", "hello world example",
                "caseType", "snakecase"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello_world_example");
    }

    @Test
    void execute_caseKebabcase_convertsToKebabCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "case",
                "text", "hello world example",
                "caseType", "kebabcase"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello-world-example");
    }

    // ========== Transform: Trim ==========

    @Test
    void execute_trimBoth_trimsWhitespace() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "trim",
                "text", "  hello world  ",
                "trimType", "both"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello world");
    }

    @Test
    void execute_trimStart_trimsLeadingWhitespace() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "trim",
                "text", "  hello  ",
                "trimType", "start"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello  ");
    }

    @Test
    void execute_trimEnd_trimsTrailingWhitespace() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "trim",
                "text", "  hello  ",
                "trimType", "end"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "  hello");
    }

    @Test
    void execute_trimAll_removesAllWhitespace() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "trim",
                "text", "  hello   world  ",
                "trimType", "all"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "helloworld");
    }

    // ========== Transform: Pad ==========

    @Test
    void execute_padStart_padsFromLeft() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "pad",
                "text", "42",
                "length", 5,
                "padChar", "0",
                "position", "start"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "00042");
    }

    @Test
    void execute_padEnd_padsFromRight() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "pad",
                "text", "hi",
                "length", 5,
                "padChar", ".",
                "position", "end"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hi...");
    }

    @Test
    void execute_padAlreadyLongerThanTarget_returnsOriginal() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "pad",
                "text", "hello world",
                "length", 5,
                "padChar", "0",
                "position", "start"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello world");
    }

    // ========== Split: Split ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitByComma_splitsCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "split",
                "text", "apple, banana, cherry",
                "separator", ",",
                "trim", true,
                "removeEmpty", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> items = (List<String>) result.getOutput().get("items");
        assertThat(items).containsExactly("apple", "banana", "cherry");
        assertThat(result.getOutput()).containsEntry("count", 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitWithTrimAndRemoveEmpty_filtersCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "split",
                "text", "a,,b, ,c",
                "separator", ",",
                "trim", true,
                "removeEmpty", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> items = (List<String>) result.getOutput().get("items");
        assertThat(items).containsExactly("a", "b", "c");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitKeepEmpty_includesEmptyItems() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "split",
                "text", "a,,b",
                "separator", ",",
                "trim", false,
                "removeEmpty", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> items = (List<String>) result.getOutput().get("items");
        assertThat(items).containsExactly("a", "", "b");
    }

    // ========== Split: Join ==========

    @Test
    void execute_joinArray_joinsWithSeparator() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "join",
                "array", "[\"apple\", \"banana\", \"cherry\"]",
                "separator", " - "
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "apple - banana - cherry");
    }

    @Test
    void execute_joinArrayDefaultSeparator_joinsWithCommaSpace() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "join",
                "array", "[\"a\", \"b\", \"c\"]"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "a, b, c");
    }

    // ========== Split: Lines ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitLines_splitsOnNewlines() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "lines",
                "text", "line1\nline2\nline3",
                "removeEmpty", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> lines = (List<String>) result.getOutput().get("lines");
        assertThat(lines).containsExactly("line1", "line2", "line3");
        assertThat(result.getOutput()).containsEntry("count", 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitLinesRemoveEmpty_removesBlankLines() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "split",
                "operation", "lines",
                "text", "line1\n\nline2\n\nline3",
                "removeEmpty", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> lines = (List<String>) result.getOutput().get("lines");
        assertThat(lines).containsExactly("line1", "line2", "line3");
    }

    // ========== Extract: Substring ==========

    @Test
    void execute_substring_extractsCorrectSubstring() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "substring",
                "text", "Hello World",
                "start", 6,
                "end", 11
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "World");
    }

    @Test
    void execute_substringNoEnd_extractsToEndOfString() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "substring",
                "text", "Hello World",
                "start", 6
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "World");
    }

    // ========== Extract: Regex ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_regexExtractSingleMatch_returnsSingleMatch() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "regex",
                "text", "Order #12345 received",
                "pattern", "(\\d+)",
                "all", false,
                "groups", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("match", "12345");
        assertThat(result.getOutput()).containsEntry("found", true);
        List<String> groups = (List<String>) result.getOutput().get("groups");
        assertThat(groups).contains("12345");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_regexExtractAllMatches_returnsAllMatches() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "regex",
                "text", "nums: 11, 22, 33",
                "pattern", "(\\d+)",
                "all", true,
                "groups", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> matches = (List<Object>) result.getOutput().get("matches");
        assertThat(matches).hasSize(3);
        assertThat(result.getOutput()).containsEntry("count", 3);
    }

    @Test
    void execute_regexNoMatch_returnsNotFound() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "regex",
                "text", "no numbers here",
                "pattern", "(\\d+)",
                "all", false,
                "groups", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("found", false);
    }

    // ========== Extract: Between ==========

    @Test
    void execute_extractBetween_extractsTextBetweenMarkers() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "between",
                "text", "start[hello]end",
                "start", "[",
                "end", "]",
                "all", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello");
        assertThat(result.getOutput()).containsEntry("found", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_extractBetweenAll_findsAllOccurrences() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "between",
                "text", "[a] and [b] and [c]",
                "start", "[",
                "end", "]",
                "all", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> matches = (List<String>) result.getOutput().get("matches");
        assertThat(matches).containsExactly("a", "b", "c");
        assertThat(result.getOutput()).containsEntry("count", 3);
    }

    @Test
    void execute_extractBetweenNotFound_returnsNotFound() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "between",
                "text", "no markers here",
                "start", "[",
                "end", "]",
                "all", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("found", false);
    }

    // ========== Extract: Length ==========

    @Test
    void execute_length_returnsCharWordLineCount() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "length",
                "text", "Hello World"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("length", 11);
        assertThat(result.getOutput()).containsEntry("words", 2);
        assertThat(result.getOutput()).containsEntry("lines", 1);
    }

    @Test
    void execute_lengthMultiline_countsLinesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "extract",
                "operation", "length",
                "text", "line1\nline2\nline3"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("lines", 3);
    }

    // ========== Validation Errors ==========

    @Test
    void execute_missingResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("operation", "replace"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Resource not selected");
    }

    @Test
    void execute_missingOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("resource", "transform"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Operation not selected");
    }

    @Test
    void execute_unknownResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "unknown",
                "operation", "replace"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown resource");
    }

    @Test
    void execute_unknownOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "unknownOp"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown operation");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsMultiOperationMarker() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsEntry("x-multi-operation", true);
        assertThat(schema).containsKey("properties");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("text-1")
                .nodeType("text")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
