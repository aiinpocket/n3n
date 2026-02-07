package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ReadFileNodeHandlerTest {

    private ReadFileNodeHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new ReadFileNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType() {
            assertThat(handler.getType()).isEqualTo("readFile");
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
    @DisplayName("Read Text")
    class ReadText {

        @Test
        void readsTextFileContent() throws IOException {
            Path file = tempDir.resolve("test.txt");
            Files.writeString(file, "Hello, World!");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("Hello, World!");
        }

        @Test
        void readsWithSpecificEncoding() throws IOException {
            Path file = tempDir.resolve("encoded.txt");
            Files.write(file, "UTF-8 text content".getBytes(StandardCharsets.UTF_8));

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", file.toAbsolutePath().toString(),
                    "encoding", "UTF-8"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("UTF-8 text content");
        }

        @Test
        void readsEmptyFile() throws IOException {
            Path file = tempDir.resolve("empty.txt");
            Files.writeString(file, "");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("");
        }

        @Test
        void defaultsToReadTextWhenNoOperation() throws IOException {
            Path file = tempDir.resolve("default.txt");
            Files.writeString(file, "Default operation test");

            Map<String, Object> config = new HashMap<>();
            config.put("filePath", file.toAbsolutePath().toString());

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("Default operation test");
        }

        @Test
        void readsFileWithSpecialCharactersInName() throws IOException {
            Path file = tempDir.resolve("test file (1).txt");
            Files.writeString(file, "Special chars content");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("Special chars content");
        }
    }

    @Nested
    @DisplayName("Read Lines")
    class ReadLines {

        @Test
        void readsFileAsLineArray() throws IOException {
            Path file = tempDir.resolve("lines.txt");
            Files.writeString(file, "line1\nline2\nline3");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readLines",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) result.getOutput().get("content");
            assertThat(lines).hasSize(3);
            assertThat(lines).containsExactly("line1", "line2", "line3");
            assertThat(result.getOutput().get("lineCount")).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Read JSON")
    class ReadJson {

        @Test
        void readsParsedJsonObject() throws IOException {
            Path file = tempDir.resolve("data.json");
            Files.writeString(file, "{\"name\":\"test\",\"value\":42}");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readJson",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content").toString()).contains("name");
        }

        @Test
        void readsBooleanJsonValue() throws IOException {
            Path file = tempDir.resolve("bool.json");
            Files.writeString(file, "true");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readJson",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo(true);
        }

        @Test
        void readsNumberJsonValue() throws IOException {
            Path file = tempDir.resolve("number.json");
            Files.writeString(file, "42");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readJson",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("Read CSV")
    class ReadCsv {

        @Test
        void readsCsvWithHeaders() throws IOException {
            Path file = tempDir.resolve("data.csv");
            Files.writeString(file, "name,age,city\nAlice,30,NYC\nBob,25,LA");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getOutput().get("content");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).get("name")).isEqualTo("Alice");
            assertThat(result.getOutput().get("rowCount")).isEqualTo(2);
        }

        @Test
        void includesHeaderInfo() throws IOException {
            Path file = tempDir.resolve("headers.csv");
            Files.writeString(file, "id,product,price\n1,Widget,9.99\n2,Gadget,19.99");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("headers");
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) result.getOutput().get("headers");
            assertThat(headers).contains("id", "product", "price");
        }

        @Test
        void withoutHeadersUsesColumnIndices() throws IOException {
            Path file = tempDir.resolve("noheader.csv");
            Files.writeString(file, "Alice,30,NYC\nBob,25,LA");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "hasHeader", false
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getOutput().get("content");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsKey("column_0");
        }

        @Test
        void emptyCsvReturnsEmptyList() throws IOException {
            Path file = tempDir.resolve("empty.csv");
            Files.writeString(file, "");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readCsv",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<?> content = (List<?>) result.getOutput().get("content");
            assertThat(content).isEmpty();
            assertThat(result.getOutput().get("rowCount")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Read Binary")
    class ReadBinary {

        @Test
        void readsBinaryAsBase64() throws IOException {
            byte[] binaryData = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
            Path file = tempDir.resolve("binary.dat");
            Files.write(file, binaryData);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readBinary",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String base64 = (String) result.getOutput().get("content");
            byte[] decoded = Base64.getDecoder().decode(base64);
            assertThat(decoded).isEqualTo(binaryData);
        }
    }

    @Nested
    @DisplayName("File Metadata")
    class FileMetadata {

        @Test
        void includesMetadataInOutput() throws IOException {
            Path file = tempDir.resolve("meta.txt");
            Files.writeString(file, "Hello metadata");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.getOutput().get("metadata");
            assertThat(metadata).containsKey("name");
            assertThat(metadata).containsKey("path");
            assertThat(metadata).containsKey("size");
            assertThat(metadata).containsKey("extension");
            assertThat(metadata).containsKey("lastModified");
            assertThat(metadata).containsKey("createdAt");
            assertThat(metadata.get("name")).isEqualTo("meta.txt");
            assertThat(metadata.get("extension")).isEqualTo("txt");
            assertThat((long) metadata.get("size")).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void nonExistentFileReturnsFailure() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", tempDir.resolve("nonexistent.txt").toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("File not found");
        }

        @Test
        void missingFilePathReturnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "readText");

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("File path is required");
        }

        @Test
        void pathTraversalIsBlocked() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", "../../../etc/passwd"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Path traversal is not allowed");
        }

        @Test
        void unknownOperationReturnsFailure() throws IOException {
            Path file = tempDir.resolve("unknown-op.txt");
            Files.writeString(file, "test");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readXml",
                    "filePath", file.toAbsolutePath().toString()
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown operation");
        }

        @Test
        void invalidEncodingFallsBackToUtf8() throws IOException {
            Path file = tempDir.resolve("fallback.txt");
            Files.writeString(file, "Fallback encoding test");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "readText",
                    "filePath", file.toAbsolutePath().toString(),
                    "encoding", "INVALID-CHARSET-XYZ"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("Fallback encoding test");
        }
    }

    @Nested
    @DisplayName("Input from InputData")
    class InputFromInputData {

        @Test
        void readsFilePathFromInputData() throws IOException {
            Path file = tempDir.resolve("input-path.txt");
            Files.writeString(file, "From input data");

            Map<String, Object> config = new HashMap<>();
            config.put("operation", "readText");

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("filePath", file.toAbsolutePath().toString());

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("read-1")
                    .nodeType("readFile")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("content")).isEqualTo("From input data");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("read-1")
                .nodeType("readFile")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
