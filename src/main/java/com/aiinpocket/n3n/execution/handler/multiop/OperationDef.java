package com.aiinpocket.n3n.execution.handler.multiop;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.Map;

/**
 * Definition for an operation within a resource.
 * Operations define the actual action to perform and its required parameters.
 */
@Data
@Builder
public class OperationDef {

    /**
     * Operation identifier (used in config).
     */
    private String name;

    /**
     * Display name for the operation.
     */
    private String displayName;

    /**
     * Description of what this operation does.
     */
    private String description;

    /**
     * The resource this operation belongs to.
     */
    private String resource;

    /**
     * Fields/parameters for this operation.
     */
    @Singular
    private List<FieldDef> fields;

    /**
     * Whether this operation requires a credential.
     */
    @Builder.Default
    private boolean requiresCredential = true;

    /**
     * Output schema description (for documentation).
     */
    private String outputDescription;

    // ==================== Factory Methods ====================

    public static OperationDefBuilder create(String name, String displayName) {
        return OperationDef.builder()
            .name(name)
            .displayName(displayName);
    }

    // ==================== Schema Generation ====================

    /**
     * Convert to operation definition for x-operation-definitions.
     */
    public Map<String, Object> toDefinition() {
        var def = new java.util.LinkedHashMap<String, Object>();
        def.put("name", name);
        def.put("displayName", displayName);
        def.put("resource", resource);

        if (description != null) {
            def.put("description", description);
        }

        // Convert fields to schema format
        if (fields != null && !fields.isEmpty()) {
            var fieldsList = new java.util.ArrayList<Map<String, Object>>();
            for (FieldDef field : fields) {
                var fieldDef = new java.util.LinkedHashMap<String, Object>();
                fieldDef.put("name", field.getName());
                fieldDef.put("displayName", field.getDisplayName());
                fieldDef.put("type", field.getType());
                fieldDef.put("required", field.isRequired());

                if (field.getFormat() != null) {
                    fieldDef.put("format", field.getFormat());
                }
                if (field.getDefaultValue() != null) {
                    fieldDef.put("default", field.getDefaultValue());
                }
                if (field.getOptions() != null) {
                    fieldDef.put("options", field.getOptions());
                }
                if (field.getOptionLabels() != null) {
                    fieldDef.put("optionLabels", field.getOptionLabels());
                }
                if (field.getDescription() != null) {
                    fieldDef.put("description", field.getDescription());
                }
                if (field.getPlaceholder() != null) {
                    fieldDef.put("placeholder", field.getPlaceholder());
                }
                if (field.getMinimum() != null) {
                    fieldDef.put("minimum", field.getMinimum());
                }
                if (field.getMaximum() != null) {
                    fieldDef.put("maximum", field.getMaximum());
                }

                fieldsList.add(fieldDef);
            }
            def.put("fields", fieldsList);
        }

        def.put("requiresCredential", requiresCredential);

        if (outputDescription != null) {
            def.put("outputDescription", outputDescription);
        }

        return def;
    }
}
