package com.aiinpocket.n3n.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 對話摘要 DTO（用於列表顯示）
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationSummary {
    private UUID id;
    private String title;
    private UUID flowId;
    private int messageCount;
    private String createdAt;
    private String updatedAt;
}
