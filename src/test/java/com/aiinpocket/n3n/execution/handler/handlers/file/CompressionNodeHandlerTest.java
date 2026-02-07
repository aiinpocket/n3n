package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CompressionNodeHandlerTest {

    private CompressionNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CompressionNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType() {
            assertThat(handler.getType()).isEqualTo("compression");
        }

        @Test
        void getDisplayName() {
            assertThat(handler.getDisplayName()).isNotEmpty();
        }

        @Test
        void getDescription() {
            assertThat(handler.getDescription()).isNotEmpty();
        }

        @Test
        void getCategory() {
            assertThat(handler.getCategory()).isEqualTo("Files");
        }

        @Test
        void getIcon() {
            assertThat(handler.getIcon()).isNotEmpty();
        }

        @Test
        void getConfigSchema() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition() {
            assertThat(handler.getInterfaceDefinition())
                    .containsKey("inputs")
                    .containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("GZIP Compress")
    class GzipCompress {

        @Test
        void compressesDataSuccessfully() {
            String input = "Hello, World! This is a test of GZIP compression. Repeated data is good for compression.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "gzip",
                    "input", input
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("data");
            assertThat(result.getOutput()).containsKey("originalSize");
            assertThat(result.getOutput()).containsKey("compressedSize");
            assertThat(result.getOutput()).containsKey("compressionRatio");

            String base64 = (String) result.getOutput().get("data");
            assertThat(base64).isNotEmpty();
            assertThatCode(() -> Base64.getDecoder().decode(base64)).doesNotThrowAnyException();
        }

        @Test
        void largeRepeatingDataCompressesWell() {
            String repeated = "ABCDEFGHIJ".repeat(1000);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "gzip",
                    "input", repeated
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            int originalSize = ((Number) result.getOutput().get("originalSize")).intValue();
            int compressedSize = ((Number) result.getOutput().get("compressedSize")).intValue();
            assertThat(compressedSize).isLessThan(originalSize);
        }

        @Test
        void outputsPositiveCompressionRatio() {
            String input = "Data for compression ratio test. " + "Repeated. ".repeat(100);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "gzip",
                    "input", input
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            double ratio = ((Number) result.getOutput().get("compressionRatio")).doubleValue();
            assertThat(ratio).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("GZIP Decompress")
    class GzipDecompress {

        @Test
        void decompressesGzipData() {
            String original = "Hello, decompression test!";

            // First compress
            NodeExecutionContext compressCtx = buildContext(Map.of(
                    "operation", "gzip",
                    "input", original
            ));
            NodeExecutionResult compressResult = handler.execute(compressCtx);
            assertThat(compressResult.isSuccess()).isTrue();
            String compressed = (String) compressResult.getOutput().get("data");

            // Then decompress
            NodeExecutionContext decompressCtx = buildContext(Map.of(
                    "operation", "gunzip",
                    "input", compressed
            ));
            NodeExecutionResult result = handler.execute(decompressCtx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("data")).isEqualTo(original);
            assertThat(result.getOutput()).containsKey("compressedSize");
            assertThat(result.getOutput()).containsKey("decompressedSize");
        }

        @Test
        void roundtripPreservesData() {
            String original = "Roundtrip test: This data should survive gzip compression and decompression intact.";

            // Compress
            NodeExecutionContext compressCtx = buildContext(Map.of(
                    "operation", "gzip",
                    "input", original
            ));
            NodeExecutionResult compressResult = handler.execute(compressCtx);
            assertThat(compressResult.isSuccess()).isTrue();

            // Decompress
            NodeExecutionContext decompressCtx = buildContext(Map.of(
                    "operation", "gunzip",
                    "input", compressResult.getOutput().get("data").toString()
            ));
            NodeExecutionResult decompressResult = handler.execute(decompressCtx);

            assertThat(decompressResult.isSuccess()).isTrue();
            assertThat(decompressResult.getOutput().get("data")).isEqualTo(original);
        }

        @Test
        void invalidGzipDataReturnsFailure() {
            String notGzip = Base64.getEncoder().encodeToString("not gzip data".getBytes(StandardCharsets.UTF_8));

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "gunzip",
                    "input", notGzip
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("Base64 Encode")
    class Base64Encode {

        @Test
        void encodesTextCorrectly() {
            String input = "Hello, Base64!";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "base64Encode",
                    "input", input
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String encoded = (String) result.getOutput().get("data");
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            assertThat(decoded).isEqualTo(input);
        }

        @Test
        void outputContainsSizeMetadata() {
            String input = "Test metadata";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "base64Encode",
                    "input", input
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("originalSize");
            assertThat(result.getOutput()).containsKey("encodedSize");
            int originalSize = ((Number) result.getOutput().get("originalSize")).intValue();
            int encodedSize = ((Number) result.getOutput().get("encodedSize")).intValue();
            assertThat(originalSize).isEqualTo(input.getBytes(StandardCharsets.UTF_8).length);
            assertThat(encodedSize).isGreaterThan(originalSize);
        }
    }

    @Nested
    @DisplayName("Base64 Decode")
    class Base64Decode {

        @Test
        void decodesBase64Correctly() {
            String original = "Hello, Base64 decode!";
            String encoded = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "base64Decode",
                    "input", encoded
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("data")).isEqualTo(original);
            assertThat(result.getOutput()).containsKey("decodedSize");
        }

        @Test
        void roundtripPreservesUnicode() {
            String original = "Base64 roundtrip: \u4F60\u597D\u4E16\u754C";

            // Encode
            NodeExecutionContext encodeCtx = buildContext(Map.of(
                    "operation", "base64Encode",
                    "input", original
            ));
            NodeExecutionResult encodeResult = handler.execute(encodeCtx);
            assertThat(encodeResult.isSuccess()).isTrue();

            // Decode
            NodeExecutionContext decodeCtx = buildContext(Map.of(
                    "operation", "base64Decode",
                    "input", encodeResult.getOutput().get("data").toString()
            ));
            NodeExecutionResult decodeResult = handler.execute(decodeCtx);

            assertThat(decodeResult.isSuccess()).isTrue();
            assertThat(decodeResult.getOutput().get("data")).isEqualTo(original);
        }

        @Test
        void invalidBase64InputReturnsFailure() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "base64Decode",
                    "input", "This is not valid base64!!!@@@"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void emptyInputReturnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "gzip");

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input data is required");
        }

        @Test
        void nullInputReturnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "gzip");
            config.put("input", null);

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input data is required");
        }

        @Test
        void invalidOperationReturnsFailure() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "zip",
                    "input", "test"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown operation");
        }
    }

    @Nested
    @DisplayName("Input from InputData")
    class InputFromInputData {

        @Test
        void readsInputFromDataField() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "gzip");

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("data", "Data from input");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("compress-1")
                    .nodeType("compression")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("data");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("compress-1")
                .nodeType("compression")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
