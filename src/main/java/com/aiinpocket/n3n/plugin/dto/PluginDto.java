package com.aiinpocket.n3n.plugin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PluginDto {
    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String author;
    private String authorUrl;
    private String repositoryUrl;
    private String documentationUrl;
    private String iconUrl;
    private String pricing;
    private BigDecimal price;
    private List<String> tags;
    private String version;
    private Long downloads;
    private Double rating;
    private Long ratingCount;
    private Boolean isInstalled;
    private String installedVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
