package com.aiinpocket.n3n.site.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * AI Site Builder 的網站本體：使用者以自然語言描述、AI 生成多檔靜態網站，
 * 平台以 /sites/{slug}/ 直接託管。ID 由 SiteService 產生。
 */
@Entity
@Table(name = "sites", indexes = {
        @Index(name = "idx_sites_owner", columnList = "owner_id"),
        @Index(name = "idx_sites_slug", columnList = "slug")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(name = "is_published", nullable = false)
    private boolean isPublished = true;

    /** 使用者自訂網域（小寫 hostname，全站唯一；null = 未設定） */
    @Column(name = "custom_domain", unique = true, length = 255)
    private String customDomain;

    /** DNS TXT 驗證通過後才會由 Host 路由對外提供 */
    @Builder.Default
    @Column(name = "custom_domain_verified", nullable = false)
    private boolean customDomainVerified = false;

    /** 驗證用 token（n3n-verify-xxxx），須放在 _n3n-verify.{domain} 的 TXT 記錄 */
    @Column(name = "custom_domain_token", length = 64)
    private String customDomainToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
