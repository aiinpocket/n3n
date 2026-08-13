-- Hosted Apps：沙盒動態應用（使用者上傳 zip，平台以 Docker 容器託管）
-- manifest 為 zip 解析結果（服務、參數、埠），params 為使用者填寫的參數值
-- （秘密類參數以平台主金鑰 AES-256-GCM 加密後存放，見 AppParamCrypto）。
-- zip_data 保留原始 zip 以供重新部署 / 重建映像（上限由 n3n.apps.max-zip-mb 控制）。

CREATE TABLE IF NOT EXISTS hosted_apps (
    id             UUID         PRIMARY KEY,
    owner_id       UUID         NOT NULL,
    name           VARCHAR(200) NOT NULL,
    slug           VARCHAR(64)  NOT NULL UNIQUE,
    app_type       VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'created',
    manifest       JSONB,
    params         JSONB,
    container_ids  JSONB,
    host_port      INT,
    internal_port  INT,
    error_message  TEXT,
    zip_data       BYTEA,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_hosted_apps_owner ON hosted_apps (owner_id);
CREATE INDEX IF NOT EXISTS idx_hosted_apps_slug ON hosted_apps (slug);
