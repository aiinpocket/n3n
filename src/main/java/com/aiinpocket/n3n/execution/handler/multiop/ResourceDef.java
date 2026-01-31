package com.aiinpocket.n3n.execution.handler.multiop;

import lombok.Builder;
import lombok.Data;

/**
 * Definition for a resource category in multi-operation nodes.
 * Resources group related operations together (e.g., "Message", "Channel", "File").
 */
@Data
@Builder
public class ResourceDef {

    /**
     * Resource identifier (used in config).
     */
    private String name;

    /**
     * Display name for the resource.
     */
    private String displayName;

    /**
     * Description of this resource category.
     */
    private String description;

    /**
     * Icon for this resource (optional).
     */
    private String icon;

    // ==================== Factory Methods ====================

    public static ResourceDef of(String name, String displayName) {
        return ResourceDef.builder()
            .name(name)
            .displayName(displayName)
            .build();
    }

    public static ResourceDef of(String name, String displayName, String description) {
        return ResourceDef.builder()
            .name(name)
            .displayName(displayName)
            .description(description)
            .build();
    }

    public static ResourceDef of(String name, String displayName, String description, String icon) {
        return ResourceDef.builder()
            .name(name)
            .displayName(displayName)
            .description(description)
            .icon(icon)
            .build();
    }
}
