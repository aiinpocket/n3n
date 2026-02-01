package com.aiinpocket.n3n.plugin.dto;

import lombok.Data;

import java.util.Map;

@Data
public class InstallPluginRequest {
    private String version; // Optional, defaults to latest
    private Map<String, Object> config; // Optional initial configuration
}
