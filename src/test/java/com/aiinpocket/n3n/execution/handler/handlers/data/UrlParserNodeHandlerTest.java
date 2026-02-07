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
class UrlParserNodeHandlerTest {

    private UrlParserNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UrlParserNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsUrlParser() {
        assertThat(handler.getType()).isEqualTo("urlParser");
    }

    @Test
    void getCategory_returnsDataTransformation() {
        assertThat(handler.getCategory()).isEqualTo("Data Transformation");
    }

    @Test
    void getDisplayName_returnsUrlParser() {
        assertThat(handler.getDisplayName()).contains("URL");
    }

    // ========== Parse Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseFullUrl_extractsAllParts() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "parse",
            "url", "https://example.com:8080/path/to/resource?key=value&foo=bar#section"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("protocol")).isEqualTo("https");
        assertThat(result.getOutput().get("host")).isEqualTo("example.com");
        assertThat(result.getOutput().get("port")).isEqualTo(8080);
        assertThat(result.getOutput().get("path")).isEqualTo("/path/to/resource");
        assertThat(result.getOutput().get("fragment")).isEqualTo("section");

        Map<String, String> params = (Map<String, String>) result.getOutput().get("params");
        assertThat(params).containsEntry("key", "value");
        assertThat(params).containsEntry("foo", "bar");
    }

    @Test
    void execute_parseSimpleUrl_works() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "parse",
            "url", "https://google.com"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("host")).isEqualTo("google.com");
    }

    @Test
    void execute_parseEmptyUrl_fails() {
        NodeExecutionContext context = buildContext(Map.of("operation", "parse"));
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
    }

    // ========== Build Tests ==========

    @Test
    void execute_buildUrl_constructsCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "build",
            "protocol", "https",
            "host", "api.example.com",
            "port", 443,
            "path", "/v1/users",
            "queryParams", Map.of("page", "1", "limit", "10")
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String url = result.getOutput().get("url").toString();
        assertThat(url).startsWith("https://api.example.com");
        assertThat(url).contains("/v1/users");
    }

    @Test
    void execute_buildUrl_noHost_fails() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "build",
            "protocol", "https"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
    }

    // ========== Encode/Decode Tests ==========

    @Test
    void execute_encode_encodesSpecialChars() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode",
            "input", "hello world&foo=bar"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String encoded = result.getOutput().get("result").toString();
        assertThat(encoded).doesNotContain(" ");
        assertThat(encoded).contains("%26");
    }

    @Test
    void execute_decode_decodesSpecialChars() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "decode",
            "input", "hello+world%26foo%3Dbar"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String decoded = result.getOutput().get("result").toString();
        assertThat(decoded).contains("hello world");
    }

    @Test
    void execute_encodeThenDecode_roundTrip() {
        String original = "test value with spaces & special=chars";

        NodeExecutionContext encCtx = buildContext(Map.of(
            "operation", "encode", "input", original
        ));
        NodeExecutionResult encResult = handler.execute(encCtx);
        String encoded = encResult.getOutput().get("result").toString();

        NodeExecutionContext decCtx = buildContext(Map.of(
            "operation", "decode", "input", encoded
        ));
        NodeExecutionResult decResult = handler.execute(decCtx);

        assertThat(decResult.getOutput().get("result")).isEqualTo(original);
    }

    // ========== AddParams Tests ==========

    @Test
    void execute_addParams_addsToUrl() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "addParams",
            "url", "https://example.com/path?existing=1",
            "queryParams", Map.of("newKey", "newValue")
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String url = result.getOutput().get("url").toString();
        assertThat(url).contains("newKey=newValue");
    }

    // ========== RemoveParams Tests ==========

    @Test
    void execute_removeParams_removesFromUrl() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "removeParams",
            "url", "https://example.com?keep=1&remove=2",
            "keys", "remove"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String url = result.getOutput().get("url").toString();
        assertThat(url).doesNotContain("remove=2");
    }

    // ========== ExtractDomain Tests ==========

    @Test
    void execute_extractDomain_extractsParts() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "extractDomain",
            "url", "https://api.sub.example.com/path"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("host")).isEqualTo("api.sub.example.com");
        assertThat(result.getOutput().get("domain")).isEqualTo("example.com");
        assertThat(result.getOutput().get("tld")).isEqualTo("com");
    }

    // ========== Edge Cases ==========

    @Test
    void execute_unknownOperation_fails() {
        NodeExecutionContext context = buildContext(Map.of("operation", "invalid"));
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_parseFromInputData_works() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("urlParser")
            .nodeConfig(Map.of("operation", "parse"))
            .inputData(Map.of("url", "https://test.com"))
            .build();

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void getConfigSchema_hasProperties() {
        Map<String, Object> schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("urlParser")
            .nodeConfig(new HashMap<>(config))
            .inputData(Map.of())
            .build();
    }
}
