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

class WriteFileNodeHandlerTest {

    private WriteFileNodeHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new WriteFileNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType() {
            assertThat(handler.getType()).isEqualTo("writeFile");
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
    @DisplayName("Write Text")
    class WriteText {

        @Test
        void writesTextContent() throws IOException {
            Path file = tempDir.resolve("output.txt");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "Hello, World!"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file)).isEqualTo("Hello, World!");
        }

        @Test
        void writesEmptyContent() throws IOException {
            Path file = tempDir.resolve("empty.txt");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", ""
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file)).isEmpty();
        }

        @Test
        void overwritesExistingFile() throws IOException {
            Path file = tempDir.resolve("overwrite.txt");
            Files.writeString(file, "Original content");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "New content"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file)).isEqualTo("New content");
        }

        @Test
        void writesWithSpecificEncoding() throws IOException {
            Path file = tempDir.resolve("encoded.txt");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "Encoded content",
                    "encoding", "UTF-8"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("Encoded content");
        }

        @Test
        void createsParentDirectories() throws IOException {
            Path file = tempDir.resolve("sub/dir/nested/output.txt");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "Nested content",
                    "createDirectories", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.exists(file)).isTrue();
            assertThat(Files.readString(file)).isEqualTo("Nested content");
        }
    }

    @Nested
    @DisplayName("Write Binary")
    class WriteBinary {

        @Test
        void writesDecodedBase64Content() throws IOException {
            byte[] originalBytes = {0x48, 0x65, 0x6C, 0x6C, 0x6F}; // "Hello"
            String base64 = Base64.getEncoder().encodeToString(originalBytes);
            Path file = tempDir.resolve("binary.dat");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeBinary",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", base64
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readAllBytes(file)).isEqualTo(originalBytes);
        }
    }

    @Nested
    @DisplayName("Write JSON")
    class WriteJson {

        @Test
        void writesFormattedJsonWithPrettyPrint() throws IOException {
            Path file = tempDir.resolve("data.json");
            String jsonContent = "{\"name\":\"test\",\"value\":42}";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeJson",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", jsonContent,
                    "prettyPrint", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String written = Files.readString(file);
            assertThat(written).contains("\n");
            assertThat(written).contains("name");
            assertThat(written).contains("test");
        }

        @Test
        void wrapsNonJsonContentAsJsonString() throws IOException {
            Path file = tempDir.resolve("string.json");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeJson",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "plain text value"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String written = Files.readString(file);
            assertThat(written).contains("plain text value");
        }
    }

    @Nested
    @DisplayName("Write CSV")
    class WriteCsv {

        @Test
        void writesJsonArrayAsCsv() throws IOException {
            Path file = tempDir.resolve("output.csv");
            String jsonArray = "[{\"name\":\"Alice\",\"age\":\"30\"},{\"name\":\"Bob\",\"age\":\"25\"}]";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", jsonArray,
                    "includeHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String csv = Files.readString(file);
            assertThat(csv).contains("name");
            assertThat(csv).contains("Alice");
            assertThat(csv).contains("Bob");
        }

        @Test
        void handlesSpecialCharactersWithQuoting() throws IOException {
            Path file = tempDir.resolve("special.csv");
            String jsonArray = "[{\"name\":\"O'Brien, Jr.\",\"note\":\"has \\\"quotes\\\"\"}]";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", jsonArray,
                    "includeHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String csv = Files.readString(file);
            assertThat(csv).contains("name");
        }

        @Test
        void emptyArrayCreatesEmptyFile() throws IOException {
            Path file = tempDir.resolve("empty.csv");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "[]"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Append")
    class Append {

        @Test
        void appendsToExistingFile() throws IOException {
            Path file = tempDir.resolve("append.txt");
            Files.writeString(file, "First line\n");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "append",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "Second line\n"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String content = Files.readString(file);
            assertThat(content).isEqualTo("First line\nSecond line\n");
        }

        @Test
        void createsNewFileIfNotExists() throws IOException {
            Path file = tempDir.resolve("new-append.txt");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "append",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "Created via append"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file)).isEqualTo("Created via append");
        }
    }

    @Nested
    @DisplayName("Output Metadata")
    class OutputMetadata {

        @Test
        void containsFilePathAndSize() throws IOException {
            Path file = tempDir.resolve("sizecheck.txt");
            String content = "Measure this content";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", content
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("filePath");
            assertThat(result.getOutput()).containsKey("size");
            assertThat(result.getOutput()).containsKey("fileName");
            assertThat(result.getOutput().get("fileName")).isEqualTo("sizecheck.txt");
            assertThat((long) result.getOutput().get("size")).isEqualTo(content.length());
        }

        @Test
        void csvOutputContainsRowCountAndHeaders() throws IOException {
            Path file = tempDir.resolve("meta.csv");
            String jsonArray = "[{\"x\":\"1\",\"y\":\"2\"}]";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeCsv",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", jsonArray,
                    "includeHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("rowCount");
            assertThat(result.getOutput()).containsKey("headers");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void missingFilePathReturnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "writeText");
            config.put("content", "test");

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("File path is required");
        }

        @Test
        void pathTraversalIsBlocked() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeText",
                    "filePath", "../../../etc/test.txt",
                    "content", "attack"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Path traversal is not allowed");
        }

        @Test
        void unknownOperationReturnsFailure() {
            Path file = tempDir.resolve("unknown.txt");

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "writeXml",
                    "filePath", file.toAbsolutePath().toString(),
                    "content", "test"
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
        void readsContentFromInputData() throws IOException {
            Path file = tempDir.resolve("from-input.txt");

            Map<String, Object> config = new HashMap<>();
            config.put("operation", "writeText");
            config.put("filePath", file.toAbsolutePath().toString());

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("content", "Content from input");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("write-1")
                    .nodeType("writeFile")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(Files.readString(file)).isEqualTo("Content from input");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("write-1")
                .nodeType("writeFile")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
