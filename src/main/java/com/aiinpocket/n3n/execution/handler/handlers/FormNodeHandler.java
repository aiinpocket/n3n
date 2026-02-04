package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.entity.FormSubmission;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.service.FormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for Form node (in-flow form step).
 *
 * This node pauses execution and waits for a form to be submitted.
 * Unlike FormTrigger (which starts a flow), this node is used mid-flow
 * to collect additional input from users.
 *
 * Config options:
 * - formTitle: Title displayed on the form page
 * - formDescription: Description text
 * - fields: Array of field definitions (same format as FormTrigger)
 * - submitButtonText: Custom submit button text
 * - showPreviousData: Whether to show data from previous nodes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FormNodeHandler extends AbstractNodeHandler {

    private final FormService formService;

    @Override
    public String getType() {
        return "form";
    }

    @Override
    public String getDisplayName() {
        return "Form";
    }

    @Override
    public String getDescription() {
        return "Pauses workflow execution and displays a form for user input. Execution continues after form submission.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "clipboard-list";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String formTitle = getStringConfig(context, "formTitle", "Enter Information");
        String formDescription = getStringConfig(context, "formDescription", "");
        boolean showPreviousData = getBooleanConfig(context, "showPreviousData", true);

        // Check if we're resuming with form data
        Map<String, Object> resumeData = getResumeData(context);
        if (resumeData != null && !resumeData.isEmpty()) {
            return handleFormSubmission(context, resumeData);
        }

        // Check if form has already been submitted
        Optional<FormSubmission> existingSubmission = formService.getFormSubmission(
            context.getExecutionId(), context.getNodeId());

        if (existingSubmission.isPresent()) {
            // Form already submitted, use existing data
            log.info("Form already submitted: executionId={}, nodeId={}",
                context.getExecutionId(), context.getNodeId());
            return createOutputFromSubmission(existingSubmission.get());
        }

        // No submission yet - pause execution and wait for form
        log.info("Pausing for form input: executionId={}, nodeId={}, title={}",
            context.getExecutionId(), context.getNodeId(), formTitle);

        return createPauseResult(context, formTitle, formDescription, showPreviousData);
    }

    /**
     * Handle form submission from resume data.
     */
    private NodeExecutionResult handleFormSubmission(NodeExecutionContext context, Map<String, Object> resumeData) {
        log.info("Processing form submission: executionId={}, nodeId={}",
            context.getExecutionId(), context.getNodeId());

        Map<String, Object> output = new HashMap<>();

        // Include all form data
        if (resumeData.containsKey("formData")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> formData = (Map<String, Object>) resumeData.get("formData");
            output.putAll(formData);
        } else {
            // Resume data is the form data itself
            output.putAll(resumeData);
        }

        // Add metadata
        output.put("_formInfo", Map.of(
            "nodeId", context.getNodeId(),
            "submittedAt", resumeData.getOrDefault("submittedAt", System.currentTimeMillis())
        ));

        return NodeExecutionResult.success(output);
    }

    /**
     * Create output from existing form submission.
     */
    private NodeExecutionResult createOutputFromSubmission(FormSubmission submission) {
        Map<String, Object> output = new HashMap<>();

        if (submission.getData() != null) {
            output.putAll(submission.getData());
        }

        output.put("_formInfo", Map.of(
            "submissionId", submission.getId().toString(),
            "submittedAt", submission.getSubmittedAt() != null ? submission.getSubmittedAt().toString() : null,
            "submittedBy", submission.getSubmittedBy() != null ? submission.getSubmittedBy().toString() : null
        ));

        return NodeExecutionResult.success(output);
    }

    /**
     * Create pause result to wait for form submission.
     */
    private NodeExecutionResult createPauseResult(NodeExecutionContext context, String formTitle,
                                                   String formDescription, boolean showPreviousData) {
        // Build form schema from config
        Map<String, Object> config = context.getNodeConfig();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = config.get("fields") instanceof List
            ? (List<Map<String, Object>>) config.get("fields")
            : List.of();

        Map<String, Object> resumeCondition = new HashMap<>();
        resumeCondition.put("type", "form");
        resumeCondition.put("executionId", context.getExecutionId().toString());
        resumeCondition.put("nodeId", context.getNodeId());
        resumeCondition.put("formSchema", Map.of(
            "title", formTitle,
            "description", formDescription,
            "fields", fields,
            "submitButtonText", getStringConfig(context, "submitButtonText", "Submit")
        ));

        // Include previous data if requested
        if (showPreviousData && context.getPreviousOutputs() != null) {
            resumeCondition.put("previousData", context.getPreviousOutputs());
        }

        Map<String, Object> partialOutput = new HashMap<>();
        partialOutput.put("status", "waiting_for_input");
        partialOutput.put("formTitle", formTitle);

        String pauseReason = String.format("Waiting for form input: %s", formTitle);

        return NodeExecutionResult.pause(pauseReason, resumeCondition, partialOutput);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getResumeData(NodeExecutionContext context) {
        Map<String, Object> globalContext = context.getGlobalContext();
        if (globalContext != null && globalContext.containsKey("_resumeData")) {
            return (Map<String, Object>) globalContext.get("_resumeData");
        }
        return null;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "formTitle", Map.of(
                    "type", "string",
                    "title", "Form Title",
                    "default", "Enter Information"
                ),
                "formDescription", Map.of(
                    "type", "string",
                    "title", "Form Description"
                ),
                "fields", Map.of(
                    "type", "array",
                    "title", "Form Fields",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "name", Map.of("type", "string", "title", "Field Name"),
                            "label", Map.of("type", "string", "title", "Label"),
                            "type", Map.of(
                                "type", "string",
                                "title", "Field Type",
                                "enum", List.of("text", "email", "number", "textarea", "select", "checkbox", "date"),
                                "default", "text"
                            ),
                            "required", Map.of("type", "boolean", "title", "Required", "default", false),
                            "placeholder", Map.of("type", "string", "title", "Placeholder"),
                            "defaultValue", Map.of("type", "string", "title", "Default Value"),
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
                            )
                        )
                    )
                ),
                "submitButtonText", Map.of(
                    "type", "string",
                    "title", "Submit Button Text",
                    "default", "Continue"
                ),
                "showPreviousData", Map.of(
                    "type", "boolean",
                    "title", "Show Previous Data",
                    "description", "Display data from previous nodes in the form",
                    "default", true
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "formData", "type", "object", "description", "Submitted form data")
            )
        );
    }
}
