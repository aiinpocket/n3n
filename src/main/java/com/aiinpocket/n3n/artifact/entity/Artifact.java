package com.aiinpocket.n3n.artifact.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 節點執行產出的檔案（artifact）：TTS 音訊、AI 生成影片/圖片、AI 撰寫的文件等。
 * 檔案本體存於本機檔案系統，此 entity 僅保存 metadata 與相對儲存路徑。
 */
@Entity
@Table(name = "artifacts", indexes = {
        @Index(name = "idx_artifacts_owner", columnList = "owner_id"),
        @Index(name = "idx_artifacts_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Artifact {

    /**
     * ID 由 ArtifactService 產生（檔案路徑需要在寫入前確定）。
     */
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "flow_id")
    private UUID flowId;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "node_id", length = 255)
    private String nodeId;

    /**
     * 產生此檔案的節點類型（例如 aiTts / falAi / saveArtifact）。
     */
    @Column(name = "source_node_type", length = 100)
    private String sourceNodeType;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 255)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /**
     * 相對於 artifact 根目錄的儲存路徑，例如 "{ownerId}/{artifactId}.mp3"。
     */
    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
