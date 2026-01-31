package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 圖片來源
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiImageSource {

    /**
     * 來源類型: url, base64
     */
    private String type;

    /**
     * 圖片 URL
     */
    private String url;

    /**
     * Base64 編碼資料
     */
    private String data;

    /**
     * 媒體類型 (e.g., image/png)
     */
    private String mediaType;
}
