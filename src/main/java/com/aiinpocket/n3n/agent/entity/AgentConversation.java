package com.aiinpocket.n3n.agent.entity;

import com.aiinpocket.n3n.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Agent 對話
 */
@Entity
@Table(name = "agent_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    /**
     * 對話開始時的元件上下文快照
     * 用於確保整個對話過程中 AI 理解的元件清單一致
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> contextSnapshot;

    /**
     * 如果 AI 生成了流程草稿，關聯到該流程
     */
    @Column(name = "draft_flow_id")
    private UUID draftFlowId;

    /**
     * 對話的訊息列表
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<AgentMessage> messages = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 新增訊息到對話
     */
    public AgentMessage addMessage(MessageRole role, String content) {
        AgentMessage message = AgentMessage.builder()
            .conversation(this)
            .role(role)
            .content(content)
            .build();
        messages.add(message);
        return message;
    }

    /**
     * 取得最後 N 則訊息（用於建構 AI 請求的 context）
     */
    public List<AgentMessage> getLastMessages(int count) {
        if (messages.size() <= count) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - count, messages.size()));
    }

    /**
     * 計算對話的總 token 數（預估）
     */
    public int estimateTotalTokens() {
        return messages.stream()
            .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
            .sum();
    }

    /**
     * 對話狀態
     */
    public enum ConversationStatus {
        /** 進行中 */
        ACTIVE,
        /** 已完成（使用者確認流程或結束對話） */
        COMPLETED,
        /** 已取消 */
        CANCELLED,
        /** 已封存 */
        ARCHIVED
    }

    /**
     * 訊息角色
     */
    public enum MessageRole {
        /** 系統提示（包含元件上下文） */
        SYSTEM,
        /** 使用者訊息 */
        USER,
        /** AI 助手回應 */
        ASSISTANT
    }
}
