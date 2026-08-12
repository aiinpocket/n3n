package com.aiinpocket.n3n.ai.provider.impl;

import com.aiinpocket.n3n.ai.provider.AiContent;
import com.aiinpocket.n3n.ai.provider.AiImageSource;
import com.aiinpocket.n3n.ai.provider.AiMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 將 AiMessage（含 multiContent 圖片內容）轉換為 OpenAI Chat Completions
 * 相容的 message 格式。OpenAI 與 OpenRouter 共用此格式。
 */
final class OpenAiContentMapper {

    private OpenAiContentMapper() {
    }

    /**
     * 純文字訊息 → {"role":..,"content":"..."}
     * 多模態訊息 → {"role":..,"content":[{"type":"text",...},{"type":"image_url",...}]}
     */
    static Map<String, Object> toOpenAiMessage(AiMessage msg) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", msg.getRole());

        if (msg.getMultiContent() == null || msg.getMultiContent().isEmpty()) {
            message.put("content", msg.getContent() != null ? msg.getContent() : "");
            return message;
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        for (AiContent content : msg.getMultiContent()) {
            if ("text".equals(content.getType()) && content.getText() != null) {
                parts.add(Map.of("type", "text", "text", content.getText()));
            } else if ("image".equals(content.getType()) && content.getImage() != null) {
                parts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", toImageUrl(content.getImage()))
                ));
            }
        }
        message.put("content", parts);
        return message;
    }

    private static String toImageUrl(AiImageSource image) {
        if ("base64".equals(image.getType())) {
            String mediaType = image.getMediaType() != null ? image.getMediaType() : "image/png";
            return "data:" + mediaType + ";base64," + image.getData();
        }
        return image.getUrl() != null ? image.getUrl() : "";
    }
}
