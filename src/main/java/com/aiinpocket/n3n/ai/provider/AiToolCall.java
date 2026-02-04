package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 工具調用
 * 當 AI 決定調用工具時返回此對象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCall {

    /**
     * 工具調用識別碼
     */
    private String id;

    /**
     * 工具/函數名稱
     */
    private String name;

    /**
     * 工具參數（JSON 字串）
     */
    private String arguments;

    /**
     * 工具類型（通常為 "function"）
     */
    @Builder.Default
    private String type = "function";
}
