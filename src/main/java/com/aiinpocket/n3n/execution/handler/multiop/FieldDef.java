package com.aiinpocket.n3n.execution.handler.multiop;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Definition for a dynamic field in multi-operation nodes.
 * Used to generate JSON Schema and render UI components.
 */
@Data
@Builder
public class FieldDef {

    /**
     * Field name (used as key in config).
     */
    private String name;

    /**
     * Display label for the field.
     */
    private String displayName;

    /**
     * Field description.
     */
    private String description;

    /**
     * Field type: string, integer, number, boolean, array, object.
     */
    private String type;

    /**
     * Format hint for UI: textarea, code, uri, password, date, datetime.
     */
    private String format;

    /**
     * Whether this field is required.
     */
    @Builder.Default
    private boolean required = false;

    /**
     * Default value for the field.
     */
    private Object defaultValue;

    /**
     * Enum options for select fields.
     */
    private List<String> options;

    /**
     * Option labels (parallel to options, for display).
     */
    private List<String> optionLabels;

    /**
     * Minimum value for number fields.
     */
    private Number minimum;

    /**
     * Maximum value for number fields.
     */
    private Number maximum;

    /**
     * Placeholder text.
     */
    private String placeholder;

    /**
     * For array fields, the item type definition.
     */
    private FieldDef items;

    /**
     * For object fields, nested properties.
     */
    private List<FieldDef> properties;

    // ==================== Factory Methods ====================

    public static FieldDef string(String name) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("string")
            .build();
    }

    public static FieldDef string(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("string")
            .build();
    }

    public static FieldDef integer(String name) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("integer")
            .build();
    }

    public static FieldDef integer(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("integer")
            .build();
    }

    public static FieldDef number(String name) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("number")
            .build();
    }

    public static FieldDef number(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("number")
            .build();
    }

    public static FieldDef bool(String name) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("boolean")
            .build();
    }

    public static FieldDef bool(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("boolean")
            .build();
    }

    public static FieldDef select(String name, List<String> options) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("string")
            .options(options)
            .build();
    }

    public static FieldDef select(String name, String displayName, List<String> options) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("string")
            .options(options)
            .build();
    }

    public static FieldDef textarea(String name) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("string")
            .format("textarea")
            .build();
    }

    public static FieldDef textarea(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("string")
            .format("textarea")
            .build();
    }

    public static FieldDef code(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("string")
            .format("code")
            .build();
    }

    public static FieldDef password(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("string")
            .format("password")
            .build();
    }

    public static FieldDef credential(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("string")
            .format("credential")
            .build();
    }

    public static FieldDef array(String name, FieldDef items) {
        return FieldDef.builder()
            .name(name)
            .displayName(name)
            .type("array")
            .items(items)
            .build();
    }

    public static FieldDef json(String name, String displayName) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("object")
            .format("json")
            .build();
    }

    public static FieldDef multiSelect(String name, String displayName, List<String> options) {
        return FieldDef.builder()
            .name(name)
            .displayName(displayName)
            .type("array")
            .format("multiselect")
            .options(options)
            .build();
    }

    // ==================== Fluent Modifiers ====================

    public FieldDef required() {
        this.required = true;
        return this;
    }

    public FieldDef optional() {
        this.required = false;
        return this;
    }

    public FieldDef withDefault(Object value) {
        this.defaultValue = value;
        return this;
    }

    public FieldDef withDescription(String desc) {
        this.description = desc;
        return this;
    }

    public FieldDef withPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public FieldDef withRange(Number min, Number max) {
        this.minimum = min;
        this.maximum = max;
        return this;
    }

    public FieldDef withOptions(List<String> options) {
        this.options = options;
        return this;
    }

    public FieldDef withOptionLabels(List<String> labels) {
        this.optionLabels = labels;
        return this;
    }

    public FieldDef withFormat(String format) {
        this.format = format;
        return this;
    }

    // ==================== Schema Generation ====================

    /**
     * Convert to JSON Schema representation.
     */
    public Map<String, Object> toJsonSchema() {
        var schema = new java.util.LinkedHashMap<String, Object>();
        schema.put("type", type);
        schema.put("title", displayName);

        if (description != null) {
            schema.put("description", description);
        }
        if (format != null) {
            schema.put("format", format);
        }
        if (defaultValue != null) {
            schema.put("default", defaultValue);
        }
        if (options != null && !options.isEmpty()) {
            schema.put("enum", options);
            if (optionLabels != null && !optionLabels.isEmpty()) {
                schema.put("enumNames", optionLabels);
            }
        }
        if (minimum != null) {
            schema.put("minimum", minimum);
        }
        if (maximum != null) {
            schema.put("maximum", maximum);
        }
        if (placeholder != null) {
            schema.put("x-placeholder", placeholder);
        }
        if (items != null) {
            schema.put("items", items.toJsonSchema());
        }
        if (properties != null && !properties.isEmpty()) {
            var props = new java.util.LinkedHashMap<String, Object>();
            var required = new java.util.ArrayList<String>();
            for (FieldDef prop : properties) {
                props.put(prop.getName(), prop.toJsonSchema());
                if (prop.isRequired()) {
                    required.add(prop.getName());
                }
            }
            schema.put("properties", props);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
        }

        return schema;
    }
}
