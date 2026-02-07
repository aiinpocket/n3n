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

class ConvertFileNodeHandlerTest {

    private ConvertFileNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConvertFileNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType() {
            assertThat(handler.getType()).isEqualTo("convertFile");
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
    @DisplayName("CSV to JSON")
    class CsvToJson {

        @Test
        void convertsSimpleCsvWithHeaders() {
            String csv = "name,age,city\nAlice,30,NYC\nBob,25,LA";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data).hasSize(2);
            assertThat(data.get(0).get("name")).isEqualTo("Alice");
            assertThat(data.get(1).get("name")).isEqualTo("Bob");
            assertThat(result.getOutput().get("rowCount")).isEqualTo(2);
        }

        @Test
        void convertsWithCustomDelimiter() {
            String csv = "name;age;city\nAlice;30;NYC\nBob;25;LA";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
                    "hasHeader", true,
                    "delimiter", ";"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data).hasSize(2);
            assertThat(data.get(0).get("name")).isEqualTo("Alice");
            assertThat(data.get(0).get("city")).isEqualTo("NYC");
        }

        @Test
        void includesHeaderInfo() {
            String csv = "id,product,price\n1,Widget,9.99\n2,Gadget,19.99";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
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
        void infersTypes() {
            String csv = "name,count,active,score\nItem,42,true,3.14";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data.get(0).get("name")).isEqualTo("Item");
            assertThat(data.get(0).get("count")).isEqualTo(42L);
            assertThat(data.get(0).get("active")).isEqualTo(true);
            assertThat(data.get(0).get("score")).isEqualTo(3.14);
        }

        @Test
        void handlesQuotedFields() {
            String csv = "name,description\n\"Smith, John\",\"Has \"\"quotes\"\" inside\"";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data).hasSize(1);
            assertThat(data.get(0).get("name")).isEqualTo("Smith, John");
        }

        @Test
        void withoutHeadersUsesColumnIndices() {
            String csv = "Alice,30,NYC\nBob,25,LA";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
                    "hasHeader", false
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data).hasSize(2);
            assertThat(data.get(0)).containsKey("column_0");
        }

        @Test
        void handlesLargeData() {
            StringBuilder csv = new StringBuilder("id,value\n");
            for (int i = 0; i < 100; i++) {
                csv.append(i).append(",value_").append(i).append("\n");
            }

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv.toString(),
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data).hasSize(100);
        }

        @Test
        void handlesNullValuesInCsv() {
            String csv = "name,value\nItem,null\nOther,";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "csvToJson",
                    "input", csv,
                    "hasHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data.get(0).get("value")).isNull();
        }
    }

    @Nested
    @DisplayName("JSON to CSV")
    class JsonToCsv {

        @Test
        void convertsSimpleJsonArray() {
            String json = "[{\"name\":\"Alice\",\"age\":\"30\"},{\"name\":\"Bob\",\"age\":\"25\"}]";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "jsonToCsv",
                    "input", json,
                    "includeHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String csvData = (String) result.getOutput().get("data");
            assertThat(csvData).contains("name");
            assertThat(csvData).contains("Alice");
            assertThat(csvData).contains("Bob");
            assertThat(result.getOutput().get("rowCount")).isEqualTo(2);
        }

        @Test
        void nonArrayInputReturnsEmptyData() {
            String json = "{\"not\":\"an array\"}";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "jsonToCsv",
                    "input", json
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("rowCount")).isEqualTo(0);
        }

        @Test
        void emptyArrayReturnsEmptyData() {
            String json = "[]";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "jsonToCsv",
                    "input", json
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("rowCount")).isEqualTo(0);
        }

        @Test
        void outputContainsHeadersList() {
            String json = "[{\"name\":\"Alice\",\"age\":\"30\"}]";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "jsonToCsv",
                    "input", json,
                    "includeHeader", true
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("headers");
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) result.getOutput().get("headers");
            assertThat(headers).contains("name", "age");
        }
    }

    @Nested
    @DisplayName("Text to Base64")
    class TextToBase64 {

        @Test
        void encodesPlainText() {
            String text = "Hello, World!";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "textToBase64",
                    "input", text
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String encoded = (String) result.getOutput().get("data");
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            assertThat(decoded).isEqualTo(text);
        }

        @Test
        void outputContainsSizeMetadata() {
            String text = "Size metadata test";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "textToBase64",
                    "input", text
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("originalSize");
            assertThat(result.getOutput()).containsKey("encodedSize");
        }

        @Test
        void handlesUnicodeText() {
            String unicode = "Unicode test: \u4F60\u597D\u4E16\u754C \u00E9\u00E8\u00EA";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "textToBase64",
                    "input", unicode
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String encoded = (String) result.getOutput().get("data");
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            assertThat(decoded).isEqualTo(unicode);
        }
    }

    @Nested
    @DisplayName("Base64 to Text")
    class Base64ToText {

        @Test
        void decodesBase64String() {
            String original = "Hello, World!";
            String base64 = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "base64ToText",
                    "input", base64
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("data")).isEqualTo(original);
            assertThat(result.getOutput()).containsKey("decodedSize");
        }

        @Test
        void roundtripPreservesData() {
            String original = "Hello, roundtrip!";

            // Encode
            NodeExecutionContext encodeCtx = buildContext(Map.of(
                    "operation", "textToBase64",
                    "input", original
            ));
            NodeExecutionResult encodeResult = handler.execute(encodeCtx);
            assertThat(encodeResult.isSuccess()).isTrue();
            String encoded = (String) encodeResult.getOutput().get("data");

            // Decode
            NodeExecutionContext decodeCtx = buildContext(Map.of(
                    "operation", "base64ToText",
                    "input", encoded
            ));
            NodeExecutionResult decodeResult = handler.execute(decodeCtx);
            assertThat(decodeResult.isSuccess()).isTrue();
            assertThat(decodeResult.getOutput().get("data")).isEqualTo(original);
        }

        @Test
        void unicodeRoundtripPreservesData() {
            String unicode = "\u4F60\u597D\u4E16\u754C \u00E9\u00E8\u00EA";

            NodeExecutionContext encodeCtx = buildContext(Map.of(
                    "operation", "textToBase64",
                    "input", unicode
            ));
            NodeExecutionResult encodeResult = handler.execute(encodeCtx);
            assertThat(encodeResult.isSuccess()).isTrue();

            NodeExecutionContext decodeCtx = buildContext(Map.of(
                    "operation", "base64ToText",
                    "input", encodeResult.getOutput().get("data").toString()
            ));
            NodeExecutionResult decodeResult = handler.execute(decodeCtx);
            assertThat(decodeResult.isSuccess()).isTrue();
            assertThat(decodeResult.getOutput().get("data")).isEqualTo(unicode);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void emptyInputReturnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "csvToJson");

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input data is required");
        }

        @Test
        void nullInputReturnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "textToBase64");
            config.put("input", null);

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input data is required");
        }

        @Test
        void invalidOperationReturnsFailure() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "xmlToJson",
                    "input", "<xml>test</xml>"
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
            String csv = "a,b\n1,2";

            Map<String, Object> config = new HashMap<>();
            config.put("operation", "csvToJson");
            config.put("hasHeader", true);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("data", csv);

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("convert-1")
                    .nodeType("convertFile")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.getOutput().get("data");
            assertThat(data).hasSize(1);
        }

        @Test
        void readsInputFromInputField() {
            String text = "fallback input";

            Map<String, Object> config = new HashMap<>();
            config.put("operation", "textToBase64");

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("input", text);

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("convert-1")
                    .nodeType("convertFile")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            String encoded = (String) result.getOutput().get("data");
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            assertThat(decoded).isEqualTo(text);
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("convert-1")
                .nodeType("convertFile")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
