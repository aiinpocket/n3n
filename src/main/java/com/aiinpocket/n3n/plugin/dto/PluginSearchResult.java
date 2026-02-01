package com.aiinpocket.n3n.plugin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PluginSearchResult {
    private List<PluginDto> plugins;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
}
