package com.aiinpocket.n3n.component.dto;

import lombok.Data;

@Data
public class UpdateComponentRequest {
    private String displayName;
    private String description;
    private String category;
    private String icon;
}
