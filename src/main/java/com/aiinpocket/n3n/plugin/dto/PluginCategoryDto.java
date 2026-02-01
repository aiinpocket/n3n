package com.aiinpocket.n3n.plugin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PluginCategoryDto {
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String icon;
    private Long count;

    public static PluginCategoryDto fromName(String name, Long count) {
        String displayName = switch (name) {
            case "ai" -> "AI & ML";
            case "data" -> "Data Processing";
            case "messaging" -> "Messaging";
            case "storage" -> "Storage";
            case "utility" -> "Utility";
            case "integration" -> "Integration";
            case "database" -> "Database";
            case "cloud" -> "Cloud Services";
            default -> capitalize(name);
        };

        String icon = switch (name) {
            case "ai" -> "robot";
            case "data" -> "database";
            case "messaging" -> "message";
            case "storage" -> "cloud-upload";
            case "utility" -> "tool";
            case "integration" -> "api";
            case "database" -> "table";
            case "cloud" -> "cloud";
            default -> "appstore";
        };

        return PluginCategoryDto.builder()
                .id(name)
                .name(name)
                .displayName(displayName)
                .description("Plugins for " + displayName.toLowerCase())
                .icon(icon)
                .count(count)
                .build();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
