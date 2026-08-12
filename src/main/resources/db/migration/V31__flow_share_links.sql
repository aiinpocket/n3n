-- 流程分享連結：透過連結邀請夥伴共同編輯流程
-- 任何登入使用者持有效 token 即可換取對應權限的 flow_shares 記錄

CREATE TABLE IF NOT EXISTS flow_share_links (
    id          UUID        PRIMARY KEY,
    flow_id     UUID        NOT NULL,
    token       VARCHAR(64) NOT NULL UNIQUE,
    permission  VARCHAR(16) NOT NULL DEFAULT 'view',
    created_by  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_flow_share_links_token ON flow_share_links (token);
CREATE INDEX IF NOT EXISTS idx_flow_share_links_flow_id ON flow_share_links (flow_id);
