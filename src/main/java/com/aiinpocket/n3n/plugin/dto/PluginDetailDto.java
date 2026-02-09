package com.aiinpocket.n3n.plugin.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class PluginDetailDto {
    @JsonUnwrapped
    private PluginDto plugin;
    private String readme;
    private String changelog;
    private List<String> capabilities;
    private Map<String, Object> configSchema;
    private Map<String, Object> nodeDefinitions;
    private List<PluginVersionDto> versions;
}
