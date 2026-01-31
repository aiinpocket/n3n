package com.aiinpocket.n3n.flow.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 元件依賴資訊
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentDependency {

    /**
     * 元件名稱
     */
    private String name;

    /**
     * 元件版本
     */
    private String version;

    /**
     * 顯示名稱
     */
    private String displayName;

    /**
     * 元件分類
     */
    private String category;

    /**
     * Docker image URI
     */
    private String image;

    /**
     * Docker registry URL (e.g., docker.io, ghcr.io)
     */
    private String registryUrl;

    /**
     * 介面定義（inputs/outputs）
     */
    private Map<String, Object> interfaceDef;

    /**
     * 設定 Schema
     */
    private Map<String, Object> configSchema;

    /**
     * 資源需求
     */
    private Map<String, Object> resources;

    /**
     * 是否為官方元件
     */
    @Builder.Default
    private boolean official = false;
}
