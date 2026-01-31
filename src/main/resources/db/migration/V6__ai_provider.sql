-- =====================================================
-- N3N Flow Platform - V6 Migration
-- Features: AI Provider Integration
-- =====================================================

-- =====================================================
-- AI Provider Configurations
-- =====================================================

CREATE TABLE ai_provider_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id    UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    provider        VARCHAR(50) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    credential_id   UUID REFERENCES credentials(id) ON DELETE SET NULL,
    base_url        VARCHAR(500),
    default_model   VARCHAR(255),
    settings        JSONB DEFAULT '{}',
    is_active       BOOLEAN DEFAULT TRUE,
    is_default      BOOLEAN DEFAULT FALSE,
    rate_limit_rpm  INTEGER,
    rate_limit_tpm  INTEGER,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_ai_config_name UNIQUE (owner_id, name),
    CONSTRAINT chk_ai_provider CHECK (provider IN ('claude', 'openai', 'gemini', 'ollama'))
);

CREATE INDEX idx_ai_configs_owner ON ai_provider_configs(owner_id);
CREATE INDEX idx_ai_configs_workspace ON ai_provider_configs(workspace_id) WHERE workspace_id IS NOT NULL;
CREATE INDEX idx_ai_configs_provider ON ai_provider_configs(provider);
CREATE INDEX idx_ai_configs_default ON ai_provider_configs(owner_id, is_default) WHERE is_default = TRUE;

COMMENT ON TABLE ai_provider_configs IS 'AI 供應商設定，每個使用者可配置多個供應商';
COMMENT ON COLUMN ai_provider_configs.provider IS '供應商類型: claude, openai, gemini, ollama';
COMMENT ON COLUMN ai_provider_configs.credential_id IS '參考 credentials 表的加密 API Key';
COMMENT ON COLUMN ai_provider_configs.base_url IS '自訂 API URL (用於 Ollama 或代理)';
COMMENT ON COLUMN ai_provider_configs.default_model IS '預設使用的模型';
COMMENT ON COLUMN ai_provider_configs.settings IS '供應商特定設定 (timeout, headers 等)';
COMMENT ON COLUMN ai_provider_configs.is_default IS '是否為使用者的預設供應商';

-- =====================================================
-- AI Usage Logging (用量追蹤)
-- =====================================================

CREATE TABLE ai_usage_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    config_id       UUID REFERENCES ai_provider_configs(id) ON DELETE SET NULL,
    provider        VARCHAR(50) NOT NULL,
    model           VARCHAR(255) NOT NULL,
    input_tokens    INTEGER NOT NULL DEFAULT 0,
    output_tokens   INTEGER NOT NULL DEFAULT 0,
    total_tokens    INTEGER NOT NULL DEFAULT 0,
    latency_ms      INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'success',
    error_code      VARCHAR(100),
    error_message   TEXT,
    request_type    VARCHAR(50) DEFAULT 'chat',
    execution_id    UUID,
    node_id         VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_ai_log_status CHECK (status IN ('success', 'error', 'rate_limited', 'timeout'))
);

CREATE INDEX idx_ai_usage_user ON ai_usage_logs(user_id);
CREATE INDEX idx_ai_usage_created ON ai_usage_logs(created_at DESC);
CREATE INDEX idx_ai_usage_provider ON ai_usage_logs(provider, model);
CREATE INDEX idx_ai_usage_execution ON ai_usage_logs(execution_id) WHERE execution_id IS NOT NULL;

COMMENT ON TABLE ai_usage_logs IS 'AI API 使用記錄，用於用量統計與計費';

-- =====================================================
-- AI Model Cache (模型清單快取)
-- =====================================================

CREATE TABLE ai_model_cache (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider        VARCHAR(50) NOT NULL,
    model_id        VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    context_window  INTEGER,
    max_output      INTEGER,
    supports_vision BOOLEAN DEFAULT FALSE,
    supports_stream BOOLEAN DEFAULT TRUE,
    capabilities    JSONB DEFAULT '{}',
    cached_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_model_cache UNIQUE (provider, model_id)
);

CREATE INDEX idx_model_cache_provider ON ai_model_cache(provider);

COMMENT ON TABLE ai_model_cache IS 'AI 模型清單快取，減少 API 呼叫';

-- =====================================================
-- AI Credential Type (新增憑證類型)
-- =====================================================

INSERT INTO credential_types (name, display_name, description, icon, fields_schema) VALUES
('ai_claude', 'Claude API Key', 'Anthropic Claude API 金鑰', 'robot',
 '{"type":"object","properties":{"apiKey":{"type":"string","format":"password","title":"API Key","description":"從 console.anthropic.com 取得"}},"required":["apiKey"]}'),
('ai_openai', 'OpenAI API Key', 'OpenAI ChatGPT API 金鑰', 'robot',
 '{"type":"object","properties":{"apiKey":{"type":"string","format":"password","title":"API Key","description":"從 platform.openai.com 取得"}},"required":["apiKey"]}'),
('ai_gemini', 'Gemini API Key', 'Google Gemini API 金鑰', 'robot',
 '{"type":"object","properties":{"apiKey":{"type":"string","format":"password","title":"API Key","description":"從 Google AI Studio 取得"}},"required":["apiKey"]}'),
('ai_ollama', 'Ollama', 'Ollama 本地 AI 服務', 'robot',
 '{"type":"object","properties":{"baseUrl":{"type":"string","title":"Base URL","default":"http://localhost:11434","description":"Ollama 服務位址"}},"required":[]}')
ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- Agent Conversations (AI 對話)
-- =====================================================

CREATE TABLE agent_conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id    UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    title           VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    context_snapshot JSONB,
    draft_flow_id   UUID REFERENCES flows(id) ON DELETE SET NULL,
    ai_config_id    UUID REFERENCES ai_provider_configs(id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_message_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_conversation_status CHECK (status IN ('active', 'archived', 'deleted'))
);

CREATE INDEX idx_agent_conversations_user ON agent_conversations(user_id);
CREATE INDEX idx_agent_conversations_status ON agent_conversations(status);
CREATE INDEX idx_agent_conversations_last_message ON agent_conversations(last_message_at DESC);

COMMENT ON TABLE agent_conversations IS 'AI 助手對話記錄';
COMMENT ON COLUMN agent_conversations.context_snapshot IS '對話上下文快照 (元件清單等)';
COMMENT ON COLUMN agent_conversations.draft_flow_id IS 'AI 生成的草稿流程';

-- =====================================================
-- Agent Messages (AI 訊息)
-- =====================================================

CREATE TABLE agent_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES agent_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    structured_data JSONB,
    token_count     INTEGER,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_message_role CHECK (role IN ('user', 'assistant', 'system'))
);

CREATE INDEX idx_agent_messages_conversation ON agent_messages(conversation_id);
CREATE INDEX idx_agent_messages_created ON agent_messages(conversation_id, created_at);

COMMENT ON TABLE agent_messages IS 'AI 對話訊息';
COMMENT ON COLUMN agent_messages.structured_data IS '結構化資料 (元件推薦、流程預覽等)';
