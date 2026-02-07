package com.aiinpocket.n3n.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchDeleteRequest {
    @NotEmpty(message = "No IDs provided")
    @Size(max = 100, message = "Maximum 100 items per batch")
    private List<UUID> ids;
}
