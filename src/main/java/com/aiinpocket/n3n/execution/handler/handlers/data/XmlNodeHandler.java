package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * Handler for XML data transformation nodes.
 * Supports parsing XML to JSON-like maps, converting maps to XML,
 * and extracting values using XPath expressions.
 */
@Component
@Slf4j
public class XmlNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "xml";
    }

    @Override
    public String getDisplayName() {
        return "XML";
    }

    @Override
    public String getDescription() {
        return "Parse, generate, and query XML data.";
    }

    @Override
    public String getCategory() {
        return NodeCategory.DATA_TRANSFORM;
    }

    @Override
    public String getIcon() {
        return "code";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "parse");
        String input = getStringConfig(context, "input", "");

        // If input is empty, try to get from input data
        if (input.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data != null) {
                input = data.toString();
            }
        }

        try {
            return switch (operation) {
                case "parse" -> parseXml(input, context);
                case "stringify" -> stringifyToXml(context);
                case "xpath" -> extractXpath(input, context);
                default -> NodeExecutionResult.failure("Unknown XML operation: " + operation);
            };
        } catch (Exception e) {
            log.error("XML operation '{}' failed: {}", operation, e.getMessage(), e);
            return NodeExecutionResult.failure("XML operation failed: " + e.getMessage());
        }
    }

    /**
     * Parse an XML string into a JSON-like map structure.
     */
    private NodeExecutionResult parseXml(String xmlString, NodeExecutionContext context) throws Exception {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return NodeExecutionResult.failure("XML input is empty");
        }

        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
        doc.getDocumentElement().normalize();

        Map<String, Object> result = elementToMap(doc.getDocumentElement());

        Map<String, Object> output = new HashMap<>();
        output.put("data", result);
        output.put("rootElement", doc.getDocumentElement().getNodeName());

        return NodeExecutionResult.success(output);
    }

    /**
     * Convert a JSON-like map structure to an XML string.
     */
    @SuppressWarnings("unchecked")
    private NodeExecutionResult stringifyToXml(NodeExecutionContext context) throws Exception {
        String rootElement = getStringConfig(context, "rootElement", "root");
        boolean pretty = getBooleanConfig(context, "pretty", true);
        String xmlDeclaration = getStringConfig(context, "xmlDeclaration", "true");

        // Get the object to convert from config or input
        Map<String, Object> data = getMapConfig(context, "data");
        if (data.isEmpty() && context.getInputData() != null) {
            Object inputObj = context.getInputData().get("data");
            if (inputObj instanceof Map) {
                data = (Map<String, Object>) inputObj;
            }
        }

        if (data.isEmpty()) {
            return NodeExecutionResult.failure("No data provided to convert to XML");
        }

        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement(rootElement);
        doc.appendChild(root);

        mapToElement(doc, root, data);

        // Convert to string
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        // Security: limit external access
        transformerFactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        transformerFactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "");

        Transformer transformer = transformerFactory.newTransformer();
        if (pretty) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        }
        if ("false".equalsIgnoreCase(xmlDeclaration)) {
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        String xmlOutput = writer.toString();

        Map<String, Object> output = new HashMap<>();
        output.put("xml", xmlOutput);
        output.put("length", xmlOutput.length());

        return NodeExecutionResult.success(output);
    }

    /**
     * Extract values from XML using XPath expressions.
     */
    private NodeExecutionResult extractXpath(String xmlString, NodeExecutionContext context) throws Exception {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return NodeExecutionResult.failure("XML input is empty");
        }

        String xpathExpression = getStringConfig(context, "xpath", "");
        if (xpathExpression.isEmpty()) {
            return NodeExecutionResult.failure("XPath expression is required");
        }

        String returnType = getStringConfig(context, "returnType", "auto");

        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlString)));

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        XPathExpression expr = xpath.compile(xpathExpression);

        Map<String, Object> output = new HashMap<>();

        switch (returnType) {
            case "string":
                String stringResult = (String) expr.evaluate(doc, XPathConstants.STRING);
                output.put("value", stringResult);
                output.put("type", "string");
                break;

            case "number":
                Double numberResult = (Double) expr.evaluate(doc, XPathConstants.NUMBER);
                output.put("value", numberResult);
                output.put("type", "number");
                break;

            case "boolean":
                Boolean boolResult = (Boolean) expr.evaluate(doc, XPathConstants.BOOLEAN);
                output.put("value", boolResult);
                output.put("type", "boolean");
                break;

            case "nodeset":
            case "auto":
            default:
                // Try nodeset first, fall back to string
                try {
                    NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                    List<Object> results = new ArrayList<>();

                    for (int i = 0; i < nodeList.getLength(); i++) {
                        Node node = nodeList.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            results.add(elementToMap((Element) node));
                        } else if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                            results.add(node.getNodeValue());
                        } else {
                            results.add(node.getTextContent());
                        }
                    }

                    if (results.size() == 1) {
                        output.put("value", results.get(0));
                    } else {
                        output.put("value", results);
                    }
                    output.put("count", results.size());
                    output.put("type", "nodeset");
                } catch (XPathExpressionException e) {
                    // Fall back to string evaluation
                    String fallback = (String) expr.evaluate(doc, XPathConstants.STRING);
                    output.put("value", fallback);
                    output.put("type", "string");
                }
                break;
        }

        output.put("xpath", xpathExpression);
        output.put("found", output.get("value") != null);

        return NodeExecutionResult.success(output);
    }

    /**
     * Convert a DOM Element to a Map recursively.
     */
    private Map<String, Object> elementToMap(Element element) {
        Map<String, Object> map = new LinkedHashMap<>();

        // Add attributes
        NamedNodeMap attributes = element.getAttributes();
        if (attributes.getLength() > 0) {
            Map<String, String> attrMap = new LinkedHashMap<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                attrMap.put(attr.getName(), attr.getValue());
            }
            map.put("@attributes", attrMap);
        }

        // Process child nodes
        NodeList children = element.getChildNodes();
        Map<String, List<Object>> childMap = new LinkedHashMap<>();
        boolean hasElementChildren = false;
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                String childName = child.getNodeName();
                Object childValue = elementToMap((Element) child);

                childMap.computeIfAbsent(childName, k -> new ArrayList<>()).add(childValue);

            } else if (child.getNodeType() == Node.TEXT_NODE
                       || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }

        if (hasElementChildren) {
            // Add child elements to map
            for (Map.Entry<String, List<Object>> entry : childMap.entrySet()) {
                List<Object> values = entry.getValue();
                if (values.size() == 1) {
                    map.put(entry.getKey(), values.get(0));
                } else {
                    map.put(entry.getKey(), values);
                }
            }
            // Also add text content if mixed content
            if (textContent.length() > 0) {
                map.put("#text", textContent.toString());
            }
        } else {
            // Leaf element - just text content
            if (textContent.length() > 0) {
                // If there are attributes, include text as #text
                if (attributes.getLength() > 0) {
                    map.put("#text", textContent.toString());
                } else {
                    // Simple text-only element, return the text directly
                    // But wrap in map for consistency
                    map.put("#text", textContent.toString());
                    return map.size() == 1 ? Map.of("#text", textContent.toString()) : map;
                }
            }
        }

        return map;
    }

    /**
     * Convert a Map to DOM elements recursively.
     */
    @SuppressWarnings("unchecked")
    private void mapToElement(Document doc, Element parent, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("@attributes".equals(key) && value instanceof Map) {
                // Set attributes
                Map<String, Object> attrs = (Map<String, Object>) value;
                for (Map.Entry<String, Object> attr : attrs.entrySet()) {
                    parent.setAttribute(attr.getKey(),
                        attr.getValue() != null ? attr.getValue().toString() : "");
                }
            } else if ("#text".equals(key)) {
                // Set text content
                parent.setTextContent(value != null ? value.toString() : "");
            } else if (value instanceof Map) {
                Element child = doc.createElement(sanitizeElementName(key));
                parent.appendChild(child);
                mapToElement(doc, child, (Map<String, Object>) value);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    Element child = doc.createElement(sanitizeElementName(key));
                    parent.appendChild(child);
                    if (item instanceof Map) {
                        mapToElement(doc, child, (Map<String, Object>) item);
                    } else {
                        child.setTextContent(item != null ? item.toString() : "");
                    }
                }
            } else {
                Element child = doc.createElement(sanitizeElementName(key));
                child.setTextContent(value != null ? value.toString() : "");
                parent.appendChild(child);
            }
        }
    }

    /**
     * Sanitize a string to be a valid XML element name.
     */
    private String sanitizeElementName(String name) {
        if (name == null || name.isEmpty()) {
            return "element";
        }
        // Replace invalid characters with underscore
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Ensure it starts with a letter or underscore
        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    /**
     * Create a secure DocumentBuilderFactory with XXE prevention.
     */
    private DocumentBuilderFactory createSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // Prevent XXE attacks
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        return factory;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string",
            "title", "Operation",
            "description", "XML operation to perform",
            "enum", List.of("parse", "stringify", "xpath"),
            "enumNames", List.of(
                "Parse (XML to JSON)",
                "Stringify (JSON to XML)",
                "XPath (Extract via XPath)"
            ),
            "default", "parse"
        ));

        properties.put("input", Map.of(
            "type", "string",
            "title", "XML Input",
            "description", "XML string to parse or query (for 'parse' and 'xpath' operations)"
        ));

        properties.put("data", Map.of(
            "type", "object",
            "title", "Data Object",
            "description", "JSON object to convert to XML (for 'stringify' operation)"
        ));

        properties.put("rootElement", Map.of(
            "type", "string",
            "title", "Root Element",
            "description", "Root element name for generated XML",
            "default", "root"
        ));

        properties.put("pretty", Map.of(
            "type", "boolean",
            "title", "Pretty Print",
            "description", "Format output XML with indentation",
            "default", true
        ));

        properties.put("xmlDeclaration", Map.of(
            "type", "string",
            "title", "XML Declaration",
            "description", "Include XML declaration (<?xml...?>)",
            "enum", List.of("true", "false"),
            "default", "true"
        ));

        properties.put("xpath", Map.of(
            "type", "string",
            "title", "XPath Expression",
            "description", "XPath expression to evaluate (for 'xpath' operation)"
        ));

        properties.put("returnType", Map.of(
            "type", "string",
            "title", "Return Type",
            "description", "Expected return type for XPath",
            "enum", List.of("auto", "string", "number", "boolean", "nodeset"),
            "default", "auto"
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
