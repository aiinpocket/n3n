package com.aiinpocket.n3n.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 類似流程 DTO
 * 用於推薦與用戶描述相似的現有流程
 */
@Data
@Builder
public class SimilarFlow {

    /**
     * 流程 ID
     */
    private UUID flowId;

    /**
     * 流程名稱
     */
    private String name;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 相似度分數 (0-1)
     */
    private double similarity;

    /**
     * 節點數量
     */
    private int nodeCount;

    /**
     * 使用的節點類型
     */
    private List<String> nodeTypes;

    /**
     * 建立時間
     */
    private Instant createdAt;

    /**
     * 匹配的關鍵字
     */
    private List<String> matchedKeywords;

    /**
     * 是否為模板
     */
    private boolean isTemplate;
}
