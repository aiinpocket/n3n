package com.aiinpocket.n3n.plugin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PluginVersionDto {
    private UUID id;
    private UUID pluginId;
    private String version;
    private String releaseNotes;
    private String minPlatformVersion;
    private Map<String, Object> configSchema;
    private Map<String, Object> nodeDefinitions;
    private List<String> capabilities;
    private Map<String, Object> dependencies;
    private Integer downloadCount;
    private LocalDateTime publishedAt;
}
