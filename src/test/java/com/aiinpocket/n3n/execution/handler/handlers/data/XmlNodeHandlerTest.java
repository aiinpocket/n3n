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
class XmlNodeHandlerTest {

    private XmlNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new XmlNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsXml() {
        assertThat(handler.getType()).isEqualTo("xml");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsXML() {
        assertThat(handler.getDisplayName()).isEqualTo("XML");
    }

    // ========== Parse: Simple XML ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseSimpleXml_returnsMapStructure() {
        String xml = "<root><name>Alice</name><age>30</age></root>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("rootElement", "root");

        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).isNotNull();
        // name and age should be nested maps with #text
        Map<String, Object> nameNode = (Map<String, Object>) data.get("name");
        assertThat(nameNode).containsEntry("#text", "Alice");
    }

    // ========== Parse: XML With Attributes ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseXmlWithAttributes_attributesCaptured() {
        String xml = "<person id=\"42\" role=\"admin\"><name>Bob</name></person>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        Map<String, String> attrs = (Map<String, String>) data.get("@attributes");
        assertThat(attrs).containsEntry("id", "42");
        assertThat(attrs).containsEntry("role", "admin");
    }

    // ========== Parse: Nested XML ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseNestedXml_structurePreserved() {
        String xml = "<root><user><name>Alice</name><address><city>Taipei</city></address></user></root>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertThat(user).isNotNull();
        Map<String, Object> address = (Map<String, Object>) user.get("address");
        assertThat(address).isNotNull();
        Map<String, Object> city = (Map<String, Object>) address.get("city");
        assertThat(city).containsEntry("#text", "Taipei");
    }

    // ========== Parse: Text Content ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseTextOnlyElement_textExtracted() {
        String xml = "<message>Hello World</message>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).containsEntry("#text", "Hello World");
    }

    // ========== Parse: Multiple Same-Named Children ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseMultipleSameNamedChildren_returnsList() {
        String xml = "<items><item>one</item><item>two</item><item>three</item></items>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        Object itemObj = data.get("item");
        assertThat(itemObj).isInstanceOf(List.class);
        List<Object> items = (List<Object>) itemObj;
        assertThat(items).hasSize(3);
    }

    // ========== Parse: CDATA ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseXmlWithCdata_cdataContentExtracted() {
        String xml = "<root><script><![CDATA[function() { return 1 < 2; }]]></script></root>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        Map<String, Object> script = (Map<String, Object>) data.get("script");
        assertThat(script.get("#text").toString()).contains("function()");
    }

    // ========== Parse: Empty XML Input ==========

    @Test
    void execute_parseEmptyInput_returnsFailure() {
        NodeExecutionResult result = executeParse("");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("empty");
    }

    @Test
    void execute_parseNullInput_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "parse");
        config.put("input", "");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(null)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
    }

    // ========== Parse: Invalid XML ==========

    @Test
    void execute_parseInvalidXml_returnsFailure() {
        String xml = "<root><unclosed>";

        NodeExecutionResult result = executeParse(xml);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("XML operation failed");
    }

    // ========== XXE Prevention ==========

    @Test
    void execute_parseXxeAttempt_blocked() {
        String xxeXml = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<root>&xxe;</root>";

        NodeExecutionResult result = executeParse(xxeXml);

        // Should fail because DOCTYPE declarations are disallowed
        assertThat(result.isSuccess()).isFalse();
    }

    // ========== Stringify ==========

    @Test
    void execute_stringifySimpleMap_returnsXmlString() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Alice");
        data.put("age", "30");

        NodeExecutionResult result = executeStringify(data, "person");

        assertThat(result.isSuccess()).isTrue();
        String xml = (String) result.getOutput().get("xml");
        assertThat(xml).contains("<person>");
        assertThat(xml).contains("<name>Alice</name>");
        assertThat(xml).contains("<age>30</age>");
        assertThat(xml).contains("</person>");
    }

    @Test
    void execute_stringifyWithCustomRoot_usesSpecifiedRoot() {
        Map<String, Object> data = Map.of("value", "test");

        NodeExecutionResult result = executeStringify(data, "custom-root");

        assertThat(result.isSuccess()).isTrue();
        String xml = (String) result.getOutput().get("xml");
        assertThat(xml).contains("<custom-root>");
    }

    @Test
    void execute_stringifyEmptyData_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "stringify");
        config.put("rootElement", "root");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("No data provided");
    }

    @Test
    void execute_stringifyOutputContainsLength() {
        Map<String, Object> data = Map.of("item", "value");

        NodeExecutionResult result = executeStringify(data, "root");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("length");
        assertThat((int) result.getOutput().get("length")).isGreaterThan(0);
    }

    // ========== XPath: String ==========

    @Test
    void execute_xpathStringExtraction_returnsStringValue() {
        String xml = "<root><name>Alice</name><age>30</age></root>";

        NodeExecutionResult result = executeXpath(xml, "/root/name", "string");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("value", "Alice");
        assertThat(result.getOutput()).containsEntry("type", "string");
    }

    // ========== XPath: Number ==========

    @Test
    void execute_xpathNumberExtraction_returnsNumberValue() {
        String xml = "<root><count>42</count></root>";

        NodeExecutionResult result = executeXpath(xml, "/root/count", "number");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("value", 42.0);
        assertThat(result.getOutput()).containsEntry("type", "number");
    }

    // ========== XPath: Boolean ==========

    @Test
    void execute_xpathBooleanExtraction_returnsBooleanValue() {
        String xml = "<root><name>Alice</name></root>";

        NodeExecutionResult result = executeXpath(xml, "boolean(/root/name)", "boolean");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("value", true);
        assertThat(result.getOutput()).containsEntry("type", "boolean");
    }

    // ========== XPath: Nodeset ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_xpathNodesetExtraction_returnsNodes() {
        String xml = "<root><item>one</item><item>two</item></root>";

        NodeExecutionResult result = executeXpath(xml, "/root/item", "nodeset");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("type", "nodeset");
        assertThat(result.getOutput()).containsEntry("count", 2);

        Object value = result.getOutput().get("value");
        assertThat(value).isInstanceOf(List.class);
        List<Object> nodes = (List<Object>) value;
        assertThat(nodes).hasSize(2);
    }

    // ========== XPath: Auto Mode ==========

    @Test
    void execute_xpathAutoMode_singleNodeReturnsDirectly() {
        String xml = "<root><name>Alice</name></root>";

        NodeExecutionResult result = executeXpath(xml, "/root/name", "auto");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("type", "nodeset");
        assertThat(result.getOutput()).containsEntry("count", 1);
        // Single result returned directly, not as list
        assertThat(result.getOutput().get("value")).isNotInstanceOf(List.class);
    }

    // ========== XPath: Empty Expression ==========

    @Test
    void execute_xpathEmptyExpression_returnsFailure() {
        String xml = "<root><name>Alice</name></root>";

        Map<String, Object> config = new HashMap<>();
        config.put("operation", "xpath");
        config.put("input", xml);
        config.put("xpath", "");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("XPath expression is required");
    }

    // ========== XPath: Found flag ==========

    @Test
    void execute_xpathResult_containsFoundFlag() {
        String xml = "<root><name>Alice</name></root>";

        NodeExecutionResult result = executeXpath(xml, "/root/name", "string");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("found", true);
        assertThat(result.getOutput()).containsEntry("xpath", "/root/name");
    }

    // ========== Invalid Operation ==========

    @Test
    void execute_unknownOperation_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "invalid");
        config.put("input", "<root/>");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown XML operation");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("operation");
        assertThat(properties).containsKey("input");
        assertThat(properties).containsKey("xpath");
        assertThat(properties).containsKey("rootElement");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Input from inputData ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseFromInputData_worksCorrectly() {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "parse");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("data", "<root><value>from-input</value></root>");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).isNotNull();
    }

    // ========== Helpers ==========

    private NodeExecutionResult executeParse(String xml) {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "parse");
        config.put("input", xml);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }

    private NodeExecutionResult executeStringify(Map<String, Object> data, String rootElement) {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "stringify");
        config.put("data", data);
        config.put("rootElement", rootElement);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }

    private NodeExecutionResult executeXpath(String xml, String xpathExpr, String returnType) {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "xpath");
        config.put("input", xml);
        config.put("xpath", xpathExpr);
        config.put("returnType", returnType);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("xml-1")
                .nodeType("xml")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }
}
