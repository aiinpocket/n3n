package com.aiinpocket.n3n.flow.dto.import_;

import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * 流程匯入請求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowImportRequest {

    /**
     * 匯出包資料
     */
    @NotNull(message = "Package data cannot be null")
    private FlowExportPackage packageData;

    /**
     * 新的流程名稱（可選）
     */
    @Size(max = 255, message = "Flow name must not exceed 255 characters")
    private String newFlowName;

    /**
     * 憑證映射（nodeId -> 新的 credentialId）
     */
    private Map<String, UUID> credentialMappings;

    /**
     * 是否自動安裝缺失的元件
     */
    @Builder.Default
    private boolean autoInstallMissingComponents = false;
}
