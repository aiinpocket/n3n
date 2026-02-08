package com.aiinpocket.n3n.plugin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class InstallPluginRequest {
    @Size(max = 50, message = "Version must be at most 50 characters")
    private String version; // Optional, defaults to latest
    @Size(max = 50, message = "Config must have at most 50 fields")
    private Map<String, Object> config; // Optional initial configuration
}
