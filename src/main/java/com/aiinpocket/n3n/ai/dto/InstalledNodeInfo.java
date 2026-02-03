package com.aiinpocket.n3n.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for installed node information.
 */
@Data
@Builder
public class InstalledNodeInfo {
    private String nodeType;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private String source;      // builtin, plugin
    private String pluginId;    // if source is plugin
}
