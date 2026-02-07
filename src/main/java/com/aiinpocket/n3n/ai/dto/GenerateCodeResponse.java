package com.aiinpocket.n3n.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 程式碼生成回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateCodeResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * AI 服務是否可用
     */
    private boolean aiAvailable;

    /**
     * 生成的程式碼
     */
    private String code;

    /**
     * AI 對程式碼的解釋說明
     */
    private String explanation;

    /**
     * 使用的程式語言
     */
    private String language;

    /**
     * 錯誤訊息（如果失敗）
     */
    private String error;

    /**
     * 建立成功回應
     */
    public static GenerateCodeResponse success(String code, String explanation, String language) {
        return GenerateCodeResponse.builder()
            .success(true)
            .aiAvailable(true)
            .code(code)
            .explanation(explanation)
            .language(language)
            .build();
    }

    /**
     * 建立 AI 不可用回應
     */
    public static GenerateCodeResponse aiUnavailable() {
        return GenerateCodeResponse.builder()
            .success(false)
            .aiAvailable(false)
            .error("AI service currently unavailable, please check AI Provider settings")
            .build();
    }

    /**
     * 建立失敗回應
     */
    public static GenerateCodeResponse failure(String error) {
        return GenerateCodeResponse.builder()
            .success(false)
            .aiAvailable(true)
            .error(error)
            .build();
    }
}
