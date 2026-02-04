package com.aiinpocket.n3n.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI 程式碼生成請求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateCodeRequest {

    /**
     * 使用者描述的程式邏輯需求
     */
    private String description;

    /**
     * 程式語言 (目前僅支援 javascript)
     */
    @Builder.Default
    private String language = "javascript";

    /**
     * 輸入資料的 Schema（用於 AI 理解資料結構）
     */
    private Map<String, Object> inputSchema;

    /**
     * 預期輸出的 Schema
     */
    private Map<String, Object> outputSchema;

    /**
     * 輸入資料範例（JSON 格式的字串）
     */
    private String sampleInput;
}
