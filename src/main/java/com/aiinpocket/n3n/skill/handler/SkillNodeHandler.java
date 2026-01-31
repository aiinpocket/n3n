package com.aiinpocket.n3n.skill.handler;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.service.SkillExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node handler for executing skills in flows.
 * This is pure code execution - no AI involvement at runtime.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillNodeHandler extends AbstractNodeHandler {

    private final SkillExecutor skillExecutor;

    @Override
    public String getType() {
        return "skill";
    }

    @Override
    public String getDisplayName() {
        return "Skill";
    }

    @Override
    public String getDescription() {
        return "Execute a pre-built automation skill (no AI token consumption)";
    }

    @Override
    public String getCategory() {
        return "actions";
    }

    @Override
    public String getIcon() {
        return "tool";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Get skill configuration
        String skillName = getStringConfig(context, "skillName", null);
        if (skillName == null || skillName.isBlank()) {
            return NodeExecutionResult.failure("Skill name is required");
        }

        // Get input mapping from node config
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMapping = (Map<String, Object>) context.getNodeConfig().get("inputMapping");

        // Build skill input from mapping and node inputs
        Map<String, Object> skillInput = buildSkillInput(inputMapping, context);

        log.debug("Executing skill '{}' with input: {}", skillName, skillInput);

        // Execute the skill
        SkillResult result = skillExecutor.executeByName(
            skillName,
            skillInput,
            context.getExecutionId(),
            null, // nodeExecutionId will be set by the engine
            context.getUserId()
        );

        if (result.isSuccess()) {
            return NodeExecutionResult.success(result.getData());
        } else {
            return NodeExecutionResult.failure(
                result.getErrorCode() != null
                    ? result.getErrorCode() + ": " + result.getErrorMessage()
                    : result.getErrorMessage()
            );
        }
    }

    private Map<String, Object> buildSkillInput(Map<String, Object> inputMapping, NodeExecutionContext context) {
        Map<String, Object> skillInput = new HashMap<>();

        if (inputMapping != null) {
            for (Map.Entry<String, Object> entry : inputMapping.entrySet()) {
                String paramName = entry.getKey();
                Object valueSource = entry.getValue();

                Object value = resolveValue(valueSource, context);
                skillInput.put(paramName, value);
            }
        }

        // Also include any direct input data
        if (context.getInputData() != null) {
            Map<String, Object> input = context.getInput("input", Map.of());
            if (input != null) {
                for (Map.Entry<String, Object> entry : input.entrySet()) {
                    if (!skillInput.containsKey(entry.getKey())) {
                        skillInput.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        return skillInput;
    }

    private Object resolveValue(Object valueSource, NodeExecutionContext context) {
        if (valueSource instanceof String strValue) {
            // Check if it's an expression (e.g., "{{nodes.node1.output.data}}")
            if (strValue.startsWith("{{") && strValue.endsWith("}}")) {
                return context.evaluateExpression(strValue);
            }
            return strValue;
        }
        return valueSource;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "skillName", Map.of(
                    "type", "string",
                    "title", "Skill Name",
                    "description", "Name of the skill to execute"
                ),
                "inputMapping", Map.of(
                    "type", "object",
                    "title", "Input Mapping",
                    "description", "Map skill input parameters to values or expressions",
                    "additionalProperties", true
                )
            ),
            "required", List.of("skillName")
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "object", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
