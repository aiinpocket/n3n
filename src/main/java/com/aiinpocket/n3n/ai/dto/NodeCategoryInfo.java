package com.aiinpocket.n3n.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for node category information.
 */
@Data
@Builder
public class NodeCategoryInfo {
    private String id;
    private String displayName;
    private String icon;
    private int installedCount;
    private int availableCount;

    public static NodeCategoryInfo of(String id, String displayName, String icon, int installed, int available) {
        return NodeCategoryInfo.builder()
                .id(id)
                .displayName(displayName)
                .icon(icon)
                .installedCount(installed)
                .availableCount(available)
                .build();
    }
}
