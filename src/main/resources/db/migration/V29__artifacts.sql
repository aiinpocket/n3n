-- Per-user artifact (generated file) library
-- 節點執行產出的檔案（TTS 音訊、AI 影片/圖片、AI 文件等）metadata；檔案本體存於檔案系統

CREATE TABLE IF NOT EXISTS artifacts (
    id               UUID         PRIMARY KEY,
    owner_id         UUID         NOT NULL,
    flow_id          UUID,
    execution_id     UUID,
    node_id          VARCHAR(255),
    source_node_type VARCHAR(100),
    filename         VARCHAR(255) NOT NULL,
    mime_type        VARCHAR(255) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_path     VARCHAR(1024) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_artifacts_owner ON artifacts (owner_id);
CREATE INDEX IF NOT EXISTS idx_artifacts_created_at ON artifacts (created_at);
