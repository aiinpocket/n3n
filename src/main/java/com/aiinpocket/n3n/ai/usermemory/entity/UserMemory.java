package com.aiinpocket.n3n.ai.usermemory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 使用者長期記憶。
 * AI 助手跨對話記住的偏好、事實與習慣，每位使用者各自獨立。
 */
@Entity
@Table(name = "user_memories", indexes = {
    @Index(name = "idx_user_memories_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** preference | fact | project | style | general */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String category = "general";

    /** assistant | user */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String source = "assistant";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
