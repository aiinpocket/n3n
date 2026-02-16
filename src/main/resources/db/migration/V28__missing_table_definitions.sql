-- V28: Add missing CREATE TABLE for entities created by ddl-auto=update
-- This migration ensures these tables exist for fresh installs with ddl-auto=validate

-- =====================================================
-- 1. ai_module_configs - User's AI module configurations
-- =====================================================
CREATE TABLE IF NOT EXISTS ai_module_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    feature VARCHAR(255) NOT NULL,
    provider_type VARCHAR(255) NOT NULL DEFAULT 'llamafile',
    display_name VARCHAR(255),
    base_url VARCHAR(255),
    api_key VARCHAR(512),
    model VARCHAR(255),
    timeout_ms BIGINT DEFAULT 60000,
    is_active BOOLEAN DEFAULT TRUE,
    failover_config JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_ai_module_configs_user_feature UNIQUE (user_id, feature)
);

CREATE INDEX IF NOT EXISTS idx_ai_module_configs_user_id ON ai_module_configs (user_id);

-- =====================================================
-- 2. plugin_install_tasks - Plugin installation tracking
-- =====================================================
CREATE TABLE IF NOT EXISTS plugin_install_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    plugin_id UUID,
    node_type VARCHAR(100) NOT NULL,
    source VARCHAR(20) NOT NULL,
    source_reference VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress_percent INTEGER DEFAULT 0,
    current_stage VARCHAR(200),
    error_message TEXT,
    container_id VARCHAR(100),
    container_port INTEGER,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Indexes (re-create with IF NOT EXISTS for idempotency)
CREATE INDEX IF NOT EXISTS idx_plugin_install_tasks_user_id_v28 ON plugin_install_tasks (user_id);
CREATE INDEX IF NOT EXISTS idx_plugin_install_tasks_status_v28 ON plugin_install_tasks (status);
