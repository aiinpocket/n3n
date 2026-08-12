package com.aiinpocket.n3n.flow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ClaimShareLinkResponse {

    private UUID flowId;

    /**
     * 使用者兌換後對此流程的有效權限（owner / admin / edit / view）
     */
    private String permission;

    private String flowName;
}
