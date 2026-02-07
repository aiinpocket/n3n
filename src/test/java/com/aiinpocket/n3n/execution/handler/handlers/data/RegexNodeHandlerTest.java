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
class RegexNodeHandlerTest {

    private RegexNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegexNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsRegex() {
        assertThat(handler.getType()).isEqualTo("regex");
    }

    @Test
    void getCategory_returnsData() {
        assertThat(handler.getCategory()).isEqualTo("Data");
    }

    @Test
    void getDisplayName_returnsRegex() {
        assertThat(handler.getDisplayName()).isEqualTo("Regex");
    }

    @Test
    void getCredentialType_returnsNull() {
        assertThat(handler.getCredentialType()).isNull();
    }

    @Test
    void getResources_containsExpectedResources() {
        var resources = handler.getResources();
        assertThat(resources).containsKey("match");
        assertThat(resources).containsKey("replace");
        assertThat(resources).containsKey("validate");
    }

    @Test
    void getOperations_matchHasExpectedOps() {
        var operations = handler.getOperations();
        var matchOps = operations.get("match");
        assertThat(matchOps).isNotNull();
        var opNames = matchOps.stream().map(op -> op.getName()).toList();
        assertThat(opNames).containsExactly("test", "find", "extract", "split");
    }

    // ========== Match: Test ==========

    @Test
    void execute_testMatchesPattern_returnsTrue() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "test",
                "text", "Hello123",
                "pattern", "\\d+",
                "ignoreCase", false,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("matches", true);
    }

    @Test
    void execute_testDoesNotMatch_returnsFalse() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "test",
                "text", "Hello World",
                "pattern", "^\\d+$",
                "ignoreCase", false,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("matches", false);
    }

    @Test
    void execute_testCaseInsensitive_matchesRegardlessOfCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "test",
                "text", "HELLO WORLD",
                "pattern", "hello",
                "ignoreCase", true,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("matches", true);
    }

    @Test
    void execute_testCaseSensitive_doesNotMatchDifferentCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "test",
                "text", "HELLO WORLD",
                "pattern", "hello",
                "ignoreCase", false,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("matches", false);
    }

    // ========== Match: Find ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_findAllMatches_returnsAllOccurrences() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "find",
                "text", "cat and dog and cat again",
                "pattern", "cat|dog",
                "ignoreCase", false,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.getOutput().get("matches");
        assertThat(matches).hasSize(3);
        assertThat(result.getOutput()).containsEntry("count", 3);
        assertThat(result.getOutput()).containsEntry("found", true);
        // First match should be "cat"
        assertThat(matches.get(0)).containsEntry("match", "cat");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_findNoMatches_returnsEmptyList() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "find",
                "text", "hello world",
                "pattern", "\\d+",
                "ignoreCase", false,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.getOutput().get("matches");
        assertThat(matches).isEmpty();
        assertThat(result.getOutput()).containsEntry("found", false);
    }

    // ========== Match: Extract Groups ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_extractGroups_returnsCaptureGroups() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "extract",
                "text", "2024-06-15",
                "pattern", "(\\d{4})-(\\d{2})-(\\d{2})",
                "allMatches", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("found", true);
        List<String> groups = (List<String>) result.getOutput().get("groups");
        // Group 0 = full match, Group 1 = year, Group 2 = month, Group 3 = day
        assertThat(groups).hasSize(4);
        assertThat(groups.get(0)).isEqualTo("2024-06-15");
        assertThat(groups.get(1)).isEqualTo("2024");
        assertThat(groups.get(2)).isEqualTo("06");
        assertThat(groups.get(3)).isEqualTo("15");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_extractGroupsAllMatches_returnsAllGroupSets() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "extract",
                "text", "2024-06-15 and 2025-01-01",
                "pattern", "(\\d{4})-(\\d{2})-(\\d{2})",
                "allMatches", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<List<String>> allGroups = (List<List<String>>) result.getOutput().get("matches");
        assertThat(allGroups).hasSize(2);
        assertThat(allGroups.get(0).get(1)).isEqualTo("2024");
        assertThat(allGroups.get(1).get(1)).isEqualTo("2025");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_extractGroupsNoMatch_returnsNotFound() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "extract",
                "text", "no date here",
                "pattern", "(\\d{4})-(\\d{2})-(\\d{2})",
                "allMatches", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("found", false);
        List<String> groups = (List<String>) result.getOutput().get("groups");
        assertThat(groups).isEmpty();
    }

    // ========== Match: Split ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitByRegex_splitsByPattern() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "split",
                "text", "one,two;three four",
                "pattern", "[,;\\s]+",
                "limit", 0
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> parts = (List<String>) result.getOutput().get("parts");
        assertThat(parts).containsExactly("one", "two", "three", "four");
        assertThat(result.getOutput()).containsEntry("count", 4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitWithLimit_limitsNumberOfParts() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "split",
                "text", "a:b:c:d:e",
                "pattern", ":",
                "limit", 3
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> parts = (List<String>) result.getOutput().get("parts");
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0)).isEqualTo("a");
        assertThat(parts.get(1)).isEqualTo("b");
        assertThat(parts.get(2)).isEqualTo("c:d:e");
    }

    // ========== Replace: ReplaceAll ==========

    @Test
    void execute_replaceAll_replacesAllPatternMatches() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "replace",
                "operation", "replaceAll",
                "text", "foo123bar456baz",
                "pattern", "\\d+",
                "replacement", "#",
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "foo#bar#baz");
    }

    @Test
    void execute_replaceAllWithGroups_usesBackReferences() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "replace",
                "operation", "replaceAll",
                "text", "John Smith, Jane Doe",
                "pattern", "(\\w+) (\\w+)",
                "replacement", "$2, $1",
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "Smith, John, Doe, Jane");
    }

    // ========== Replace: ReplaceFirst ==========

    @Test
    void execute_replaceFirst_replacesOnlyFirstMatch() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "replace",
                "operation", "replaceFirst",
                "text", "aaa bbb aaa",
                "pattern", "aaa",
                "replacement", "ccc",
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "ccc bbb aaa");
    }

    // ========== Replace: Remove ==========

    @Test
    void execute_removeMatches_removesAllMatchingPatterns() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "replace",
                "operation", "remove",
                "text", "hello 123 world 456",
                "pattern", "\\d+",
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "hello  world ");
    }

    @Test
    void execute_removeCaseInsensitive_removesRegardlessOfCase() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "replace",
                "operation", "remove",
                "text", "Hello hello HELLO world",
                "pattern", "hello\\s*",
                "ignoreCase", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("text", "world");
    }

    // ========== Validate: Email ==========

    @Test
    void execute_validateEmailValid_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "email",
                "email", "user@example.com"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
        assertThat(result.getOutput()).containsEntry("local", "user");
        assertThat(result.getOutput()).containsEntry("domain", "example.com");
    }

    @Test
    void execute_validateEmailInvalid_returnsInvalid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "email",
                "email", "not-an-email"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", false);
    }

    @Test
    void execute_validateEmailInvalidMissingDomain_returnsInvalid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "email",
                "email", "user@"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", false);
    }

    // ========== Validate: URL ==========

    @Test
    void execute_validateUrlValidWithProtocol_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "url",
                "url", "https://www.example.com",
                "requireProtocol", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
    }

    @Test
    void execute_validateUrlMissingProtocol_requireProtocol_returnsInvalid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "url",
                "url", "www.example.com",
                "requireProtocol", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", false);
    }

    @Test
    void execute_validateUrlMissingProtocol_notRequired_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "url",
                "url", "www.example.com",
                "requireProtocol", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
    }

    // ========== Validate: Phone ==========

    @Test
    void execute_validatePhoneInternational_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "phone",
                "phone", "+1234567890123",
                "format", "international"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
        assertThat(result.getOutput()).containsEntry("format", "international");
    }

    @Test
    void execute_validatePhoneAny_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "phone",
                "phone", "(555) 123-4567",
                "format", "any"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
    }

    // ========== Validate: IP Address ==========

    @Test
    void execute_validateIpV4_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "ipAddress",
                "ip", "192.168.1.1",
                "version", "v4"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
        assertThat(result.getOutput()).containsEntry("isIPv4", true);
        assertThat(result.getOutput()).containsEntry("version", "v4");
    }

    @Test
    void execute_validateIpV4_invalidAddress_returnsInvalid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "ipAddress",
                "ip", "999.999.999.999",
                "version", "v4"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", false);
    }

    @Test
    void execute_validateIpBoth_v4Address_returnsValid() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "ipAddress",
                "ip", "10.0.0.1",
                "version", "both"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
        assertThat(result.getOutput()).containsEntry("isIPv4", true);
    }

    // ========== Validate: Custom ==========

    @Test
    void execute_validateCustomFullMatch_matchesEntireText() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "custom",
                "text", "ABC-123",
                "pattern", "[A-Z]{3}-\\d{3}",
                "fullMatch", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
    }

    @Test
    void execute_validateCustomFullMatch_failsPartialMatch() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "custom",
                "text", "ABC-123-XYZ",
                "pattern", "[A-Z]{3}-\\d{3}",
                "fullMatch", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", false);
    }

    @Test
    void execute_validateCustomPartialMatch_succeedsOnPartialMatch() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "validate",
                "operation", "custom",
                "text", "prefix ABC-123 suffix",
                "pattern", "[A-Z]{3}-\\d{3}",
                "fullMatch", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
    }

    // ========== Invalid Regex Pattern ==========

    @Test
    void execute_invalidRegexPattern_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
                "operation", "test",
                "text", "hello",
                "pattern", "[invalid(",
                "ignoreCase", false,
                "multiline", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid regex pattern");
    }

    @Test
    void execute_invalidRegexInReplace_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "replace",
                "operation", "replaceAll",
                "text", "hello",
                "pattern", "(?invalid)",
                "replacement", "x",
                "ignoreCase", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid regex pattern");
    }

    // ========== Validation Errors ==========

    @Test
    void execute_missingResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("operation", "test"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Resource not selected");
    }

    @Test
    void execute_missingOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("resource", "match"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Operation not selected");
    }

    @Test
    void execute_unknownResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "unknown",
                "operation", "test"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown resource");
    }

    @Test
    void execute_unknownOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "match",
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
                .nodeId("regex-1")
                .nodeType("regex")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
