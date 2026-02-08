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
 * XML processing tool
 * Supports XML parsing, XPath queries, and XML-JSON conversion
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
                XML processing tool, supports multiple operations:
                - parse: Parse XML text
                - xpath: Query XML using XPath
                - toJson: Convert XML to JSON
                - validate: Validate XML format

                Parameters:
                - data: XML text
                - operation: Operation type
                - xpath: XPath expression (for xpath operation)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "XML text"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "xpath", "toJson", "validate"),
                                "description", "Operation type",
                                "default", "parse"
                        ),
                        "xpath", Map.of(
                                "type", "string",
                                "description", "XPath expression"
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
                    return ToolResult.failure("Data cannot be empty");
                }

                // Security: limit input size
                if (data.length() > 1_000_000) {
                    return ToolResult.failure("Data too large, maximum limit is 1MB");
                }

                String operation = (String) parameters.getOrDefault("operation", "parse");

                return switch (operation) {
                    case "parse" -> parseXml(data);
                    case "xpath" -> xpathQuery(data, (String) parameters.get("xpath"));
                    case "toJson" -> xmlToJson(data);
                    case "validate" -> validateXml(data);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("XML operation failed", e);
                return ToolResult.failure("XML operation failed");
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
            sb.append(String.format("XML parsing successful\nRoot element: %s\nElement count: %d\n\n", rootElement, elementCount));
            sb.append("Formatted XML:\n");
            sb.append(formatted.length() > 2000 ? formatted.substring(0, 2000) + "..." : formatted);

            return ToolResult.success(sb.toString(), Map.of(
                    "rootElement", rootElement,
                    "elementCount", elementCount,
                    "valid", true
            ));
        } catch (Exception e) {
            return ToolResult.failure("XML parsing failed");
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
            return ToolResult.failure("The xpath operation requires an xpath parameter");
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
            sb.append(String.format("XPath query results: found %d matches\n", nodes.getLength()));
            sb.append(String.format("Expression: %s\n\n", xpathExpr));
            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                sb.append(String.format("%d. %s\n", i + 1, results.get(i)));
            }

            return ToolResult.success(sb.toString(), Map.of(
                    "count", nodes.getLength(),
                    "results", results
            ));
        } catch (Exception e) {
            return ToolResult.failure("XPath query failed");
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
                    "XML to JSON conversion successful:\n" + (json.length() > 1000 ? json.substring(0, 1000) + "..." : json),
                    Map.of("json", json)
            );
        } catch (Exception e) {
            return ToolResult.failure("XML to JSON conversion failed");
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

            return ToolResult.success("XML format is valid", Map.of("valid", true));
        } catch (Exception e) {
            return ToolResult.success(
                    "XML format is invalid: " + e.getMessage(),
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
