package com.aiinpocket.n3n.flow.dto.import_;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流程匯入預覽回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowImportPreviewResponse {

    /**
     * 流程名稱
     */
    private String flowName;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 節點數量
     */
    private int nodeCount;

    /**
     * 邊數量
     */
    private int edgeCount;

    /**
     * 元件狀態
     */
    private List<ComponentStatus> componentStatuses;

    /**
     * 憑證需求
     */
    private List<CredentialRequirement> credentialRequirements;

    /**
     * 是否可以匯入
     */
    private boolean canImport;

    /**
     * 阻擋原因
     */
    private List<String> blockers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentStatus {
        private String name;
        private String version;
        private String image;
        private boolean installed;
        private boolean versionMatch;
        private String installedVersion;
        private boolean canAutoInstall;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredentialRequirement {
        private String nodeId;
        private String nodeName;
        private String credentialType;
        private String originalCredentialName;
        private List<CompatibleCredential> compatibleCredentials;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompatibleCredential {
        private String id;
        private String name;
        private String type;
    }
}
