package com.aiinpocket.n3n.template.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO for official templates loaded from JSON
 */
@Data
public class OfficialTemplateDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String complexity;
    private int estimatedNodes;
    private List<String> useCases;
    private Map<String, Object> definition;

    /**
     * Category DTO for official template categories
     */
    @Data
    public static class CategoryDto {
        private String id;
        private String name;
        private String description;
        private String icon;
    }
}
