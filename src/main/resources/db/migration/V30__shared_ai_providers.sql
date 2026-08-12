-- AI Provider 設定改為平台共用（由管理員統一管理）
-- 既有設定實質上就是平台在用的金鑰，直接標記為共用
ALTER TABLE ai_provider_configs ADD COLUMN IF NOT EXISTS is_shared BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE ai_provider_configs SET is_shared = TRUE;

CREATE INDEX IF NOT EXISTS idx_ai_provider_configs_shared
    ON ai_provider_configs (is_shared) WHERE is_shared = TRUE;
