package com.aiinpocket.n3n.flow.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 憑證佔位符（不含實際憑證資料）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialPlaceholder {

    /**
     * 節點 ID
     */
    private String nodeId;

    /**
     * 節點名稱/標籤
     */
    private String nodeName;

    /**
     * 憑證類型
     */
    private String credentialType;

    /**
     * 原始憑證名稱（供參考）
     */
    private String credentialName;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否必須
     */
    @Builder.Default
    private boolean required = true;
}
