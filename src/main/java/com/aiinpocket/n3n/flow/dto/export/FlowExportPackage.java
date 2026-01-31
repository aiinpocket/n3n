package com.aiinpocket.n3n.flow.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 流程匯出包
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowExportPackage {

    /**
     * 匯出格式版本
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * 匯出時間
     */
    private Instant exportedAt;

    /**
     * 匯出者（遮罩後的 email）
     */
    private String exportedBy;

    /**
     * 流程資料
     */
    private FlowData flow;

    /**
     * 依賴項
     */
    private FlowDependencies dependencies;

    /**
     * SHA-256 checksum
     */
    private String checksum;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowData {
        private String name;
        private String description;
        private Map<String, Object> definition;
        private Map<String, Object> settings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowDependencies {
        private List<ComponentDependency> components;
        private List<CredentialPlaceholder> credentialPlaceholders;
    }
}
