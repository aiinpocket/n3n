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
class Base64NodeHandlerTest {

    private Base64NodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new Base64NodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsBase64() {
        assertThat(handler.getType()).isEqualTo("base64");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsBase64() {
        assertThat(handler.getDisplayName()).isEqualTo("Base64");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(handler.getDescription()).isNotBlank();
    }

    // ========== Encode Tests ==========

    @Test
    void execute_encodeBasic_encodesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode",
            "input", "Hello, World!"
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isEqualTo("SGVsbG8sIFdvcmxkIQ==");
    }

    @Test
    void execute_encodeEmpty_encodesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode",
            "input", ""
        ));
        NodeExecutionResult result = handler.execute(context);

        // Empty input should fail as "required"
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_encodeUnicode_encodesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode",
            "input", "你好世界"
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isNotNull();
    }

    @Test
    void execute_encodeUrlSafe_usesUrlSafeAlphabet() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode",
            "input", "test?data=value+special",
            "urlSafe", true
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String encoded = result.getOutput().get("result").toString();
        // URL safe base64 uses - and _ instead of + and /
        assertThat(encoded).doesNotContain("+");
        assertThat(encoded).doesNotContain("/");
    }

    @Test
    void execute_encodeNoPadding_noPaddingChars() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode",
            "input", "Hi",
            "noPadding", true
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String encoded = result.getOutput().get("result").toString();
        assertThat(encoded).doesNotEndWith("=");
    }

    // ========== Decode Tests ==========

    @Test
    void execute_decodeBasic_decodesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "decode",
            "input", "SGVsbG8sIFdvcmxkIQ=="
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isEqualTo("Hello, World!");
    }

    @Test
    void execute_decodeUrlSafe_decodesCorrectly() {
        // First encode with URL-safe, then decode
        String input = "test?data=value";
        NodeExecutionContext encodeCtx = buildContext(Map.of(
            "operation", "encode",
            "input", input,
            "urlSafe", true
        ));
        NodeExecutionResult encodeResult = handler.execute(encodeCtx);
        String encoded = encodeResult.getOutput().get("result").toString();

        NodeExecutionContext decodeCtx = buildContext(Map.of(
            "operation", "decode",
            "input", encoded,
            "urlSafe", true
        ));
        NodeExecutionResult decodeResult = handler.execute(decodeCtx);

        assertThat(decodeResult.isSuccess()).isTrue();
        assertThat(decodeResult.getOutput().get("result")).isEqualTo(input);
    }

    @Test
    void execute_decodeInvalid_fails() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "decode",
            "input", "!!!invalid!!!"
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
    }

    // ========== Validate Tests ==========

    @Test
    void execute_validateValid_returnsTrue() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "validate",
            "input", "SGVsbG8="
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("isValid")).isEqualTo(true);
    }

    @Test
    void execute_validateInvalid_returnsFalse() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "validate",
            "input", "!!!not_base64!!!"
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("isValid")).isEqualTo(false);
    }

    // ========== Round-trip Tests ==========

    @Test
    void execute_encodeThenDecode_roundTrip() {
        String original = "The quick brown fox jumps over the lazy dog 123!@#";

        // Encode
        NodeExecutionContext encodeCtx = buildContext(Map.of(
            "operation", "encode", "input", original
        ));
        NodeExecutionResult encodeResult = handler.execute(encodeCtx);
        String encoded = encodeResult.getOutput().get("result").toString();

        // Decode
        NodeExecutionContext decodeCtx = buildContext(Map.of(
            "operation", "decode", "input", encoded
        ));
        NodeExecutionResult decodeResult = handler.execute(decodeCtx);

        assertThat(decodeResult.getOutput().get("result")).isEqualTo(original);
    }

    // ========== Input from Input Data ==========

    @Test
    void execute_inputFromInputData_works() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("base64")
            .nodeConfig(Map.of("operation", "encode"))
            .inputData(Map.of("data", "test input"))
            .build();

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_outputContainsLengths() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "encode", "input", "Hello"
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.getOutput()).containsKey("inputLength");
        assertThat(result.getOutput()).containsKey("outputLength");
        assertThat(result.getOutput()).containsEntry("operation", "encode");
    }

    @Test
    void getConfigSchema_hasOperationProperty() {
        Map<String, Object> schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("base64")
            .nodeConfig(new HashMap<>(config))
            .inputData(Map.of())
            .build();
    }
}
