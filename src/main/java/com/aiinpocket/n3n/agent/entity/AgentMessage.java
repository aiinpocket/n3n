package com.aiinpocket.n3n.agent.entity;

import com.aiinpocket.n3n.agent.entity.AgentConversation.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AI Agent 對話訊息
 */
@Entity
@Table(name = "agent_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AgentConversation conversation;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /**
     * 結構化資料（AI 回應中的 JSON 物件）
     * 例如：推薦的元件、生成的流程定義等
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> structuredData;

    /**
     * 訊息的 token 數量
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    /**
     * AI 回應的延遲時間（毫秒）
     */
    @Column(name = "latency_ms")
    private Long latencyMs;

    /**
     * 使用的 AI 模型（僅 ASSISTANT 訊息）
     */
    @Column(name = "model_id")
    private String modelId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 預估 token 數量（簡易估算：每 4 個字元約 1 token）
     */
    public int estimateTokenCount() {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 中文字元約 1.5-2 tokens，英文約 4 字元 1 token
        // 這是粗略估計
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars / 4.0);
    }

    /**
     * 檢查是否包含結構化的流程定義
     */
    public boolean hasFlowDefinition() {
        return structuredData != null && structuredData.containsKey("flowDefinition");
    }

    /**
     * 檢查是否包含元件推薦
     */
    public boolean hasComponentRecommendations() {
        return structuredData != null &&
            (structuredData.containsKey("existingComponents") ||
             structuredData.containsKey("suggestedNewComponents"));
    }

    /**
     * 取得需求理解摘要
     */
    public String getUnderstanding() {
        if (structuredData != null && structuredData.containsKey("understanding")) {
            return (String) structuredData.get("understanding");
        }
        return null;
    }
}
