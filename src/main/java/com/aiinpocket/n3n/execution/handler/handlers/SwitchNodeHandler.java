package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for Switch node (multi-way conditional routing).
 *
 * Unlike Condition (which is binary true/false), Switch supports multiple
 * named branches (2-10) with conditions. Each case has a name and condition.
 *
 * Modes:
 * - first: Routes to the first matching case (stops at first match)
 * - all: Routes to all matching cases (can follow multiple branches)
 *
 * Config options:
 * - mode: "first" or "all"
 * - cases: Array of case definitions
 *   - branch: Name of the output branch
 *   - condition: Expression to evaluate
 *   - operator: Comparison operator
 *   - value: Value to compare against
 * - enableFallback: Whether to use fallback when no case matches
 * - fallbackBranch: Name of fallback branch (default: "default")
 */
@Component
@Slf4j
public class SwitchNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "switch";
    }

    @Override
    public String getDisplayName() {
        return "Switch";
    }

    @Override
    public String getDescription() {
        return "Multi-way conditional routing. Routes data to different branches based on multiple conditions.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "git-fork";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        Map<String, Object> config = context.getNodeConfig();

        String mode = getStringConfig(context, "mode", "first");
        boolean enableFallback = getBooleanConfig(context, "enableFallback", true);
        String fallbackBranch = getStringConfig(context, "fallbackBranch", "default");

        // Get cases configuration
        List<Map<String, Object>> cases = new ArrayList<>();
        Object casesObj = config.get("cases");
        if (casesObj instanceof List) {
            cases = (List<Map<String, Object>>) casesObj;
        }

        log.debug("Switch node executing: mode={}, caseCount={}", mode, cases.size());

        List<String> matchedBranches = new ArrayList<>();
        Map<String, Object> output = inputData != null ? new HashMap<>(inputData) : new HashMap<>();

        // Evaluate each case
        for (Map<String, Object> caseConfig : cases) {
            String branch = (String) caseConfig.getOrDefault("branch", "case_" + matchedBranches.size());
            String field = (String) caseConfig.get("field");
            String operator = (String) caseConfig.getOrDefault("operator", "equals");
            Object compareValue = caseConfig.get("value");

            // Get field value from input
            Object fieldValue = field != null && inputData != null
                ? getNestedValue(inputData, field)
                : null;

            // Evaluate condition
            boolean matches = evaluateCondition(fieldValue, operator, compareValue);

            log.debug("Switch case '{}': {} {} {} = {}", branch, field, operator, compareValue, matches);

            if (matches) {
                matchedBranches.add(branch);

                // In "first" mode, stop after first match
                if ("first".equals(mode)) {
                    break;
                }
            }
        }

        // Handle no matches
        if (matchedBranches.isEmpty()) {
            if (enableFallback) {
                log.debug("No cases matched, using fallback branch: {}", fallbackBranch);
                matchedBranches.add(fallbackBranch);
            } else {
                log.warn("No cases matched and fallback disabled");
                return NodeExecutionResult.failure("No switch cases matched");
            }
        }

        // Add routing metadata
        output.put("_switchInfo", Map.of(
            "mode", mode,
            "matchedBranches", matchedBranches,
            "totalCases", cases.size()
        ));

        log.info("Switch routing to branches: {}", matchedBranches);

        return NodeExecutionResult.withBranches(output, matchedBranches);
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> data, String path) {
        if (path == null || path.isEmpty()) {
            return data;
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else if (current instanceof List && part.matches("\\d+")) {
                List<?> list = (List<?>) current;
                int index = Integer.parseInt(part);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }

        return current;
    }

    private boolean evaluateCondition(Object fieldValue, String operator, Object compareValue) {
        // Handle null cases
        if (fieldValue == null && compareValue == null) {
            return "equals".equals(operator) || "isNull".equals(operator);
        }

        if (fieldValue == null) {
            return "notEquals".equals(operator) || "isNull".equals(operator);
        }

        String strValue = fieldValue.toString();
        String strCompare = compareValue != null ? compareValue.toString() : "";

        switch (operator) {
            case "equals":
                return Objects.equals(strValue, strCompare);

            case "notEquals":
                return !Objects.equals(strValue, strCompare);

            case "contains":
                return strValue.contains(strCompare);

            case "notContains":
                return !strValue.contains(strCompare);

            case "startsWith":
                return strValue.startsWith(strCompare);

            case "endsWith":
                return strValue.endsWith(strCompare);

            case "matches":
                try {
                    return strValue.matches(strCompare);
                } catch (Exception e) {
                    log.warn("Invalid regex pattern: {}", strCompare);
                    return false;
                }

            case "greaterThan":
                return compareNumbers(fieldValue, compareValue) > 0;

            case "lessThan":
                return compareNumbers(fieldValue, compareValue) < 0;

            case "greaterOrEqual":
                return compareNumbers(fieldValue, compareValue) >= 0;

            case "lessOrEqual":
                return compareNumbers(fieldValue, compareValue) <= 0;

            case "in":
                // Check if value is in a comma-separated list
                if (compareValue instanceof List) {
                    return ((List<?>) compareValue).contains(strValue);
                }
                return Arrays.asList(strCompare.split(","))
                    .stream()
                    .map(String::trim)
                    .anyMatch(strValue::equals);

            case "notIn":
                if (compareValue instanceof List) {
                    return !((List<?>) compareValue).contains(strValue);
                }
                return Arrays.asList(strCompare.split(","))
                    .stream()
                    .map(String::trim)
                    .noneMatch(strValue::equals);

            case "isEmpty":
                return strValue.isEmpty();

            case "isNotEmpty":
                return !strValue.isEmpty();

            case "isNull":
                return false; // fieldValue is not null at this point

            case "isNotNull":
                return true; // fieldValue is not null at this point

            case "isTrue":
                return Boolean.parseBoolean(strValue) || "1".equals(strValue);

            case "isFalse":
                return !Boolean.parseBoolean(strValue) && !"1".equals(strValue);

            default:
                log.warn("Unknown operator: {}", operator);
                return false;
        }
    }

    private int compareNumbers(Object a, Object b) {
        try {
            double numA = Double.parseDouble(a.toString());
            double numB = b != null ? Double.parseDouble(b.toString()) : 0;
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            return a.toString().compareTo(b != null ? b.toString() : "");
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "mode", Map.of(
                    "type", "string",
                    "title", "Mode",
                    "enum", List.of("first", "all"),
                    "enumNames", List.of("First Match (stop at first matching case)", "All Matches (follow all matching branches)"),
                    "default", "first"
                ),
                "cases", Map.of(
                    "type", "array",
                    "title", "Cases",
                    "description", "Define the conditions for each branch",
                    "minItems", 2,
                    "maxItems", 10,
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "branch", Map.of(
                                "type", "string",
                                "title", "Branch Name",
                                "description", "Name of this output branch"
                            ),
                            "field", Map.of(
                                "type", "string",
                                "title", "Field",
                                "description", "Field path to evaluate (e.g., data.type)"
                            ),
                            "operator", Map.of(
                                "type", "string",
                                "title", "Operator",
                                "enum", List.of(
                                    "equals", "notEquals", "contains", "notContains",
                                    "startsWith", "endsWith", "matches",
                                    "greaterThan", "lessThan", "greaterOrEqual", "lessOrEqual",
                                    "in", "notIn", "isEmpty", "isNotEmpty",
                                    "isNull", "isNotNull", "isTrue", "isFalse"
                                ),
                                "default", "equals"
                            ),
                            "value", Map.of(
                                "type", "string",
                                "title", "Value",
                                "description", "Value to compare against (for 'in' operator, use comma-separated list)"
                            )
                        )
                    )
                ),
                "enableFallback", Map.of(
                    "type", "boolean",
                    "title", "Enable Fallback",
                    "description", "Use fallback branch when no cases match",
                    "default", true
                ),
                "fallbackBranch", Map.of(
                    "type", "string",
                    "title", "Fallback Branch",
                    "description", "Branch name when no cases match",
                    "default", "default"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "case_0", "type", "any", "description", "First case output"),
                Map.of("name", "case_1", "type", "any", "description", "Second case output"),
                Map.of("name", "case_2", "type", "any", "description", "Third case output"),
                Map.of("name", "default", "type", "any", "description", "Fallback output")
            ),
            "dynamicOutputs", true,
            "maxOutputs", 10
        );
    }
}
