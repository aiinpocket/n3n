package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 多模態內容（用於圖片等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContent {

    /**
     * 內容類型: text, image
     */
    private String type;

    /**
     * 文字內容
     */
    private String text;

    /**
     * 圖片來源
     */
    private AiImageSource image;

    public static AiContent text(String text) {
        return AiContent.builder().type("text").text(text).build();
    }

    public static AiContent imageUrl(String url) {
        return AiContent.builder()
                .type("image")
                .image(AiImageSource.builder().type("url").url(url).build())
                .build();
    }

    public static AiContent imageBase64(String base64Data, String mediaType) {
        return AiContent.builder()
                .type("image")
                .image(AiImageSource.builder()
                        .type("base64")
                        .data(base64Data)
                        .mediaType(mediaType)
                        .build())
                .build();
    }
}
