package com.aiinpocket.n3n.marketplace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plugin review/rating entity.
 */
@Entity
@Table(name = "plugin_reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"plugin_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plugin_id", nullable = false)
    private Plugin plugin;

    /**
     * User who wrote the review
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * User display name
     */
    @Column(name = "user_name", length = 200)
    private String userName;

    /**
     * Rating (1-5 stars)
     */
    @Column(nullable = false)
    private int rating;

    /**
     * Review title
     */
    @Column(length = 200)
    private String title;

    /**
     * Review content
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Version this review is for
     */
    @Column(length = 50)
    private String version;

    /**
     * Number of helpful votes
     */
    @Column(name = "helpful_count")
    @Builder.Default
    private int helpfulCount = 0;

    /**
     * Whether this is a verified purchase
     */
    @Column(name = "verified_purchase")
    @Builder.Default
    private boolean verifiedPurchase = false;

    /**
     * Whether author has replied
     */
    @Column(name = "author_replied")
    @Builder.Default
    private boolean authorReplied = false;

    /**
     * Author's reply
     */
    @Column(name = "author_reply", columnDefinition = "TEXT")
    private String authorReply;

    /**
     * Author reply timestamp
     */
    @Column(name = "author_reply_at")
    private LocalDateTime authorReplyAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
