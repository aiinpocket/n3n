package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for Form Trigger node.
 *
 * This is a trigger node that starts flow execution when a form is submitted.
 * The form is accessible via a public URL with a secure token.
 *
 * When the flow is triggered, the form data is available as output.
 *
 * Config options:
 * - formTitle: Title displayed on the form page
 * - formDescription: Description text shown below the title
 * - fields: Array of field definitions
 * - submitButtonText: Custom submit button text
 * - successMessage: Message shown after successful submission
 * - redirectUrl: Optional URL to redirect after submission
 */
@Component
@Slf4j
public class FormTriggerHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "formTrigger";
    }

    @Override
    public String getDisplayName() {
        return "Form Trigger";
    }

    @Override
    public String getDescription() {
        return "Triggers workflow execution when a public form is submitted. Creates a shareable form URL.";
    }

    @Override
    public String getCategory() {
        return "Triggers";
    }

    @Override
    public String getIcon() {
        return "file-text";
    }

    @Override
    public boolean isTrigger() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Form trigger receives its input from the form submission
        // The execution service passes form data as trigger input
        Map<String, Object> inputData = context.getInputData();

        log.info("Form trigger executing: executionId={}, nodeId={}, inputKeys={}",
            context.getExecutionId(), context.getNodeId(),
            inputData != null ? inputData.keySet() : "null");

        // Build output from form data
        Map<String, Object> output = new HashMap<>();

        // Include all form data in output
        if (inputData != null) {
            output.putAll(inputData);
        }

        // Add metadata
        output.put("_formTrigger", Map.of(
            "nodeId", context.getNodeId(),
            "triggeredAt", System.currentTimeMillis()
        ));

        return NodeExecutionResult.success(output);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "formTitle", Map.of(
                    "type", "string",
                    "title", "Form Title",
                    "description", "Title displayed at the top of the form",
                    "default", "Submit Form"
                ),
                "formDescription", Map.of(
                    "type", "string",
                    "title", "Form Description",
                    "description", "Description text shown below the title"
                ),
                "fields", Map.of(
                    "type", "array",
                    "title", "Form Fields",
                    "description", "Define the fields for your form",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "name", Map.of("type", "string", "title", "Field Name"),
                            "label", Map.of("type", "string", "title", "Label"),
                            "type", Map.of(
                                "type", "string",
                                "title", "Field Type",
                                "enum", List.of("text", "email", "number", "textarea", "select", "checkbox", "date", "file"),
                                "default", "text"
                            ),
                            "required", Map.of("type", "boolean", "title", "Required", "default", false),
                            "placeholder", Map.of("type", "string", "title", "Placeholder"),
                            "options", Map.of(
                                "type", "array",
                                "title", "Options (for select)",
                                "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                        "value", Map.of("type", "string"),
                                        "label", Map.of("type", "string")
                                    )
                                )
                            ),
                            "validation", Map.of(
                                "type", "object",
                                "title", "Validation Rules",
                                "properties", Map.of(
                                    "minLength", Map.of("type", "integer"),
                                    "maxLength", Map.of("type", "integer"),
                                    "pattern", Map.of("type", "string")
                                )
                            )
                        )
                    )
                ),
                "submitButtonText", Map.of(
                    "type", "string",
                    "title", "Submit Button Text",
                    "default", "Submit"
                ),
                "successMessage", Map.of(
                    "type", "string",
                    "title", "Success Message",
                    "description", "Message shown after successful submission",
                    "default", "Thank you for your submission!"
                ),
                "redirectUrl", Map.of(
                    "type", "string",
                    "title", "Redirect URL",
                    "description", "Optional URL to redirect after submission"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(),  // Trigger has no inputs
            "outputs", List.of(
                Map.of("name", "formData", "type", "object", "description", "Submitted form data")
            )
        );
    }
}
