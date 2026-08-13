-- AI Site Builder：使用者靜態網站（AI 生成、平台即時託管）
-- sites 為網站本體，site_files 儲存每個檔案內容（bytea）

CREATE TABLE IF NOT EXISTS sites (
    id           UUID         PRIMARY KEY,
    owner_id     UUID         NOT NULL,
    slug         VARCHAR(64)  NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    is_published BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sites_owner ON sites (owner_id);
CREATE INDEX IF NOT EXISTS idx_sites_slug ON sites (slug);

CREATE TABLE IF NOT EXISTS site_files (
    id           UUID          PRIMARY KEY,
    site_id      UUID          NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    path         VARCHAR(400)  NOT NULL,
    content_type VARCHAR(100),
    data         BYTEA         NOT NULL,
    size_bytes   BIGINT        NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_site_files_site_path UNIQUE (site_id, path)
);

CREATE INDEX IF NOT EXISTS idx_site_files_site ON site_files (site_id);
