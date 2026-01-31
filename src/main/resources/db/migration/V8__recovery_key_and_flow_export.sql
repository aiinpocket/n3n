-- =====================================================
-- N3N Flow Platform - V8 Migration
-- Features: Recovery Key System, Flow Export/Import
-- =====================================================

-- =====================================================
-- Part 1: Recovery Key 系統
-- =====================================================

-- 加密項目狀態追蹤
ALTER TABLE credentials
ADD COLUMN IF NOT EXISTS key_version INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS key_status VARCHAR(20) DEFAULT 'active';

COMMENT ON COLUMN credentials.key_version IS '加密使用的 key 版本';
COMMENT ON COLUMN credentials.key_status IS 'active=正常, mismatched=key不匹配, migrating=遷移中';

-- 使用者的 Recovery Key 備份確認
ALTER TABLE users
ADD COLUMN IF NOT EXISTS recovery_key_backed_up BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS recovery_key_backed_up_at TIMESTAMP WITH TIME ZONE;

-- 系統金鑰元資料（不儲存實際金鑰！）
CREATE TABLE IF NOT EXISTS encryption_key_metadata (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type        VARCHAR(50) NOT NULL,  -- 'recovery', 'master', 'instance_salt'
    key_version     INTEGER NOT NULL DEFAULT 1,
    key_hash        VARCHAR(64),           -- SHA-256 hash 用於驗證（僅 recovery key）
    source          VARCHAR(50) NOT NULL,  -- 'auto_generated', 'user_provided', 'environment'
    status          VARCHAR(20) NOT NULL DEFAULT 'active',  -- active, deprecated, compromised
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    rotated_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_key_type_version UNIQUE (key_type, key_version),
    CONSTRAINT chk_key_source CHECK (source IN ('auto_generated', 'user_provided', 'environment')),
    CONSTRAINT chk_key_status CHECK (status IN ('active', 'deprecated', 'compromised'))
);

CREATE INDEX IF NOT EXISTS idx_encryption_key_metadata_type ON encryption_key_metadata(key_type, status);

COMMENT ON TABLE encryption_key_metadata IS '加密金鑰元資料，不儲存實際金鑰';
COMMENT ON COLUMN encryption_key_metadata.key_hash IS 'Recovery Key 的 SHA-256 hash，用於驗證';

-- 金鑰遷移記錄
CREATE TABLE IF NOT EXISTS key_migration_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_version    INTEGER NOT NULL,
    to_version      INTEGER NOT NULL,
    credential_id   UUID REFERENCES credentials(id) ON DELETE SET NULL,
    migrated_by     UUID REFERENCES users(id),
    started_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at    TIMESTAMP WITH TIME ZONE,
    status          VARCHAR(20) DEFAULT 'in_progress',
    error_message   TEXT,

    CONSTRAINT chk_migration_status CHECK (status IN ('in_progress', 'completed', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_key_migration_logs_status ON key_migration_logs(status);
CREATE INDEX IF NOT EXISTS idx_key_migration_logs_credential ON key_migration_logs(credential_id);

COMMENT ON TABLE key_migration_logs IS '記錄金鑰遷移操作';

-- =====================================================
-- Part 2: Flow 匯出/匯入
-- =====================================================

-- 匯入記錄
CREATE TABLE IF NOT EXISTS flow_imports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id             UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    package_version     VARCHAR(20) NOT NULL,
    package_checksum    VARCHAR(64) NOT NULL,
    original_flow_name  VARCHAR(500),
    source_system       VARCHAR(255),
    imported_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    imported_by         UUID NOT NULL REFERENCES users(id),
    missing_components  JSONB DEFAULT '[]',
    credential_mappings JSONB DEFAULT '{}',
    status              VARCHAR(20) NOT NULL DEFAULT 'resolved',

    CONSTRAINT chk_import_status CHECK (status IN ('pending', 'resolved', 'partial', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_flow_imports_flow ON flow_imports(flow_id);
CREATE INDEX IF NOT EXISTS idx_flow_imports_user ON flow_imports(imported_by);
CREATE INDEX IF NOT EXISTS idx_flow_imports_status ON flow_imports(status);

COMMENT ON TABLE flow_imports IS '流程匯入記錄';
COMMENT ON COLUMN flow_imports.package_checksum IS 'SHA-256 checksum 用於驗證完整性';
COMMENT ON COLUMN flow_imports.missing_components IS '匯入時缺少的元件清單';
COMMENT ON COLUMN flow_imports.credential_mappings IS '原始 credentialId -> 新 credentialId 映射';

-- 元件自動安裝記錄
CREATE TABLE IF NOT EXISTS component_installations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id       UUID REFERENCES flow_imports(id) ON DELETE SET NULL,
    component_name  VARCHAR(255) NOT NULL,
    version         VARCHAR(50) NOT NULL,
    image           VARCHAR(500) NOT NULL,
    registry_url    VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_install_status CHECK (status IN ('pending', 'pulling', 'installed', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_component_installations_import ON component_installations(import_id);
CREATE INDEX IF NOT EXISTS idx_component_installations_status ON component_installations(status);

COMMENT ON TABLE component_installations IS '元件自動安裝記錄';
COMMENT ON COLUMN component_installations.image IS 'Docker image URI';
COMMENT ON COLUMN component_installations.registry_url IS 'Docker registry URL (e.g., docker.io, ghcr.io)';

-- =====================================================
-- Part 3: 統計資訊更新
-- =====================================================

ANALYZE credentials;
ANALYZE users;
