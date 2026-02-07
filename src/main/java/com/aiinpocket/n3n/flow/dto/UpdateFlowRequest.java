package com.aiinpocket.n3n.flow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFlowRequest {
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;
}
