package com.aiinpocket.n3n.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;

/**
 * 對話訊息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /** 訊息角色 */
    private String role;  // user, assistant, system

    /** 訊息內容 */
    private String content;

    /** 時間戳記 */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** 結構化資料（如流程定義） */
    private Map<String, Object> structuredData;

    /**
     * 快速建立使用者訊息
     */
    public static Message user(String content) {
        return Message.builder()
            .role("user")
            .content(content)
            .build();
    }

    /**
     * 快速建立助手訊息
     */
    public static Message assistant(String content) {
        return Message.builder()
            .role("assistant")
            .content(content)
            .build();
    }

    /**
     * 快速建立系統訊息
     */
    public static Message system(String content) {
        return Message.builder()
            .role("system")
            .content(content)
            .build();
    }
}
