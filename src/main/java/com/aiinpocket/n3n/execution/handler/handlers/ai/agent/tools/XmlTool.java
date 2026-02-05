package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * XML 處理工具
 * 支援 XML 解析、XPath 查詢、XML 與 JSON 轉換
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XmlTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return "xml";
    }

    @Override
    public String getName() {
        return "XML";
    }

    @Override
    public String getDescription() {
        return """
                XML 處理工具，支援多種操作：
                - parse: 解析 XML 文字
                - xpath: 使用 XPath 查詢 XML
                - toJson: XML 轉換為 JSON
                - validate: 驗證 XML 格式

                參數：
                - data: XML 文字
                - operation: 操作類型
                - xpath: XPath 表達式（用於 xpath 操作）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "XML 文字"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "xpath", "toJson", "validate"),
                                "description", "操作類型",
                                "default", "parse"
                        ),
                        "xpath", Map.of(
                                "type", "string",
                                "description", "XPath 表達式"
                        )
                ),
                "required", List.of("data")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String data = (String) parameters.get("data");
                if (data == null || data.isBlank()) {
                    return ToolResult.failure("資料不能為空");
                }

                // Security: limit input size
                if (data.length() > 1_000_000) {
                    return ToolResult.failure("資料過大，最大限制 1MB");
                }

                String operation = (String) parameters.getOrDefault("operation", "parse");

                return switch (operation) {
                    case "parse" -> parseXml(data);
                    case "xpath" -> xpathQuery(data, (String) parameters.get("xpath"));
                    case "toJson" -> xmlToJson(data);
                    case "validate" -> validateXml(data);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("XML operation failed", e);
                return ToolResult.failure("XML 操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            doc.normalizeDocument();

            String rootElement = doc.getDocumentElement().getNodeName();
            int elementCount = doc.getElementsByTagName("*").getLength();

            // Format the XML
            String formatted = formatXml(doc);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("XML 解析成功\n根元素: %s\n元素數量: %d\n\n", rootElement, elementCount));
            sb.append("格式化 XML：\n");
            sb.append(formatted.length() > 2000 ? formatted.substring(0, 2000) + "..." : formatted);

            return ToolResult.success(sb.toString(), Map.of(
                    "rootElement", rootElement,
                    "elementCount", elementCount,
                    "valid", true
            ));
        } catch (Exception e) {
            return ToolResult.failure("XML 解析失敗: " + e.getMessage());
        }
    }

    private String formatXml(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute("indent-number", 2);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private ToolResult xpathQuery(String xml, String xpathExpr) {
        if (xpathExpr == null || xpathExpr.isBlank()) {
            return ToolResult.failure("xpath 操作需要提供 xpath 參數");
        }

        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);

            List<String> results = new ArrayList<>();
            for (int i = 0; i < Math.min(nodes.getLength(), 100); i++) {
                results.add(nodes.item(i).getTextContent());
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("XPath 查詢結果：找到 %d 個匹配\n", nodes.getLength()));
            sb.append(String.format("表達式：%s\n\n", xpathExpr));
            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                sb.append(String.format("%d. %s\n", i + 1, results.get(i)));
            }

            return ToolResult.success(sb.toString(), Map.of(
                    "count", nodes.getLength(),
                    "results", results
            ));
        } catch (Exception e) {
            return ToolResult.failure("XPath 查詢失敗: " + e.getMessage());
        }
    }

    private ToolResult xmlToJson(String xml) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            JsonNode jsonNode = convertXmlToJson(doc.getDocumentElement());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);

            return ToolResult.success(
                    "XML 轉 JSON 成功：\n" + (json.length() > 1000 ? json.substring(0, 1000) + "..." : json),
                    Map.of("json", json)
            );
        } catch (Exception e) {
            return ToolResult.failure("XML 轉 JSON 失敗: " + e.getMessage());
        }
    }

    private JsonNode convertXmlToJson(Element element) {
        ObjectNode result = objectMapper.createObjectNode();

        // Add attributes
        NamedNodeMap attrs = element.getAttributes();
        if (attrs.getLength() > 0) {
            ObjectNode attrsNode = objectMapper.createObjectNode();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                attrsNode.put(attr.getNodeName(), attr.getNodeValue());
            }
            result.set("@attributes", attrsNode);
        }

        // Add children
        NodeList children = element.getChildNodes();
        java.util.Map<String, List<JsonNode>> childMap = new java.util.LinkedHashMap<>();
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                childMap.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(convertXmlToJson((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }

        // Add child nodes to result
        for (Map.Entry<String, List<JsonNode>> entry : childMap.entrySet()) {
            if (entry.getValue().size() == 1) {
                result.set(entry.getKey(), entry.getValue().get(0));
            } else {
                ArrayNode arrayNode = objectMapper.createArrayNode();
                entry.getValue().forEach(arrayNode::add);
                result.set(entry.getKey(), arrayNode);
            }
        }

        // Add text content if any
        if (textContent.length() > 0 && childMap.isEmpty()) {
            return objectMapper.valueToTree(textContent.toString());
        } else if (textContent.length() > 0) {
            result.put("#text", textContent.toString());
        }

        return result;
    }

    private ToolResult validateXml(String xml) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            return ToolResult.success("XML 格式有效", Map.of("valid", true));
        } catch (Exception e) {
            return ToolResult.success(
                    "XML 格式無效: " + e.getMessage(),
                    Map.of("valid", false, "error", e.getMessage())
            );
        }
    }

    private DocumentBuilderFactory createSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Security: disable XXE attacks
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    @Override
    public String getCategory() {
        return "data";
    }
}
