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
class SpreadsheetNodeHandlerTest {

    private SpreadsheetNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SpreadsheetNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSpreadsheet() {
        assertThat(handler.getType()).isEqualTo("spreadsheet");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsSpreadsheetCSV() {
        assertThat(handler.getDisplayName()).contains("Spreadsheet");
    }

    // ========== CSV to JSON Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_csvToJson_parsesWithHeaders() {
        String csv = "name,age,city\nAlice,25,Tokyo\nBob,30,Osaka";
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "csvToJson",
            "csv", csv,
            "hasHeader", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("name", "Alice");
        assertThat(items.get(0).get("age")).isEqualTo(25);
        assertThat(items.get(1)).containsEntry("city", "Osaka");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_csvToJson_parsesWithoutHeaders() {
        String csv = "Alice,25,Tokyo\nBob,30,Osaka";
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "csvToJson",
            "csv", csv,
            "hasHeader", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsKey("column0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_csvToJson_handlesQuotedFields() {
        String csv = "name,description\nAlice,\"Hello, World\"\nBob,\"She said \"\"hi\"\"\"";
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "csvToJson",
            "csv", csv,
            "hasHeader", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items.get(0).get("description")).isEqualTo("Hello, World");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_csvToJson_customDelimiter() {
        String csv = "name;age;city\nAlice;25;Tokyo";
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "csvToJson",
            "csv", csv,
            "delimiter", ";",
            "hasHeader", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items.get(0)).containsEntry("name", "Alice");
    }

    @Test
    void execute_csvToJson_emptyCsv_fails() {
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "csvToJson"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
    }

    // ========== JSON to CSV Tests ==========

    @Test
    void execute_jsonToCsv_generatesCorrectOutput() {
        List<Map<String, Object>> items = List.of(
            new LinkedHashMap<>(Map.of("name", "Alice", "age", 25)),
            new LinkedHashMap<>(Map.of("name", "Bob", "age", 30))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "jsonToCsv", "includeHeader", true),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String csv = result.getOutput().get("csv").toString();
        assertThat(csv).contains("name");
        assertThat(csv).contains("Alice");
        assertThat(csv).contains("Bob");
    }

    @Test
    void execute_jsonToCsv_withoutHeader() {
        List<Map<String, Object>> items = List.of(
            new LinkedHashMap<>(Map.of("name", "Alice"))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "jsonToCsv", "includeHeader", false),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String csv = result.getOutput().get("csv").toString();
        assertThat(csv).doesNotContain("name\n");
    }

    @Test
    void execute_jsonToCsv_customDelimiter() {
        List<Map<String, Object>> items = List.of(
            new LinkedHashMap<>(Map.of("a", "1", "b", "2"))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "jsonToCsv", "delimiter", "|"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String csv = result.getOutput().get("csv").toString();
        assertThat(csv).contains("|");
    }

    // ========== Transpose Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_transpose_transposesData() {
        List<Map<String, Object>> items = List.of(
            new LinkedHashMap<>(Map.of("name", "Alice", "age", 25)),
            new LinkedHashMap<>(Map.of("name", "Bob", "age", 30))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "transpose"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> transposed = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(transposed).hasSize(2); // name row and age row
    }

    // ========== AddColumn Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_addColumn_addsNewColumn() {
        List<Map<String, Object>> items = List.of(
            new HashMap<>(Map.of("name", "Alice"))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "addColumn", "columnName", "status", "defaultValue", "active"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> resultItems = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(resultItems.get(0)).containsEntry("status", "active");
    }

    // ========== RemoveColumn Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_removeColumn_removesColumn() {
        List<Map<String, Object>> items = List.of(
            new HashMap<>(Map.of("name", "Alice", "age", 25, "secret", "hidden"))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "removeColumn", "columnName", "secret"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> resultItems = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(resultItems.get(0)).doesNotContainKey("secret");
    }

    // ========== RenameColumn Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_renameColumn_renamesColumn() {
        List<Map<String, Object>> items = List.of(
            new HashMap<>(Map.of("old_name", "Alice"))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "renameColumn", "oldName", "old_name", "newName", "name"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> resultItems = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(resultItems.get(0)).containsEntry("name", "Alice");
        assertThat(resultItems.get(0)).doesNotContainKey("old_name");
    }

    // ========== Pivot Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_pivot_pivotsData() {
        List<Map<String, Object>> items = List.of(
            new HashMap<>(Map.of("date", "2024-01", "metric", "sales", "value", 100)),
            new HashMap<>(Map.of("date", "2024-01", "metric", "returns", "value", 5)),
            new HashMap<>(Map.of("date", "2024-02", "metric", "sales", "value", 150))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "pivot", "rowField", "date",
                   "columnField", "metric", "valueField", "value"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> pivoted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(pivoted).hasSize(2); // 2 dates
    }

    // ========== Unpivot Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_unpivot_unpivotsData() {
        List<Map<String, Object>> items = List.of(
            new HashMap<>(Map.of("id", "1", "sales", 100, "returns", 5))
        );

        NodeExecutionContext context = buildContext(
            Map.of("operation", "unpivot", "idField", "id"),
            Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> unpivoted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(unpivoted).hasSize(2); // sales and returns
    }

    // ========== Round-trip Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_csvToJsonAndBack_roundTrip() {
        String originalCsv = "name,age\nAlice,25\nBob,30";

        // CSV to JSON
        NodeExecutionContext toJsonCtx = buildContext(Map.of(
            "operation", "csvToJson", "csv", originalCsv, "hasHeader", true
        ));
        NodeExecutionResult jsonResult = handler.execute(toJsonCtx);
        List<Map<String, Object>> items = (List<Map<String, Object>>) jsonResult.getOutput().get("items");

        // JSON to CSV
        NodeExecutionContext toCsvCtx = buildContext(
            Map.of("operation", "jsonToCsv", "includeHeader", true),
            Map.of("items", items)
        );
        NodeExecutionResult csvResult = handler.execute(toCsvCtx);

        assertThat(csvResult.isSuccess()).isTrue();
        String resultCsv = csvResult.getOutput().get("csv").toString();
        assertThat(resultCsv).contains("Alice");
        assertThat(resultCsv).contains("Bob");
    }

    // ========== Edge Cases ==========

    @Test
    void execute_unknownOperation_fails() {
        NodeExecutionContext context = buildContext(Map.of("operation", "invalid"));
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_csvToJson_numericParsing() {
        String csv = "val\n42\n3.14\ntrue\ntext";
        NodeExecutionContext context = buildContext(Map.of(
            "operation", "csvToJson", "csv", csv, "hasHeader", true
        ));

        @SuppressWarnings("unchecked")
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items.get(0).get("val")).isEqualTo(42);
        assertThat(items.get(1).get("val")).isEqualTo(3.14);
        assertThat(items.get(2).get("val")).isEqualTo(true);
        assertThat(items.get(3).get("val")).isEqualTo("text");
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
            .nodeType("spreadsheet")
            .nodeConfig(new HashMap<>(config))
            .inputData(Map.of())
            .build();
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("spreadsheet")
            .nodeConfig(new HashMap<>(config))
            .inputData(new HashMap<>(inputData))
            .build();
    }
}
