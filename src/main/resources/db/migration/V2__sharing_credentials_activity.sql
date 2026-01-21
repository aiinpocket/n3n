-- =====================================================
-- N3N Flow Platform - V2 Migration
-- Features: Sharing, Credentials, Activity Logging
-- =====================================================

-- =====================================================
-- Flow Sharing & Permissions
-- =====================================================

-- Add visibility to flows
ALTER TABLE flows ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'private';
ALTER TABLE flows ADD CONSTRAINT chk_flow_visibility
    CHECK (visibility IN ('private', 'shared', 'public'));

-- Flow sharing - who can access shared flows
CREATE TABLE flow_shares (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission      VARCHAR(20) NOT NULL DEFAULT 'view',
    shared_by       UUID NOT NULL REFERENCES users(id),
    shared_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_flow_shares UNIQUE (flow_id, user_id),
    CONSTRAINT chk_share_permission CHECK (permission IN ('view', 'edit', 'admin'))
);

CREATE INDEX idx_flow_shares_flow ON flow_shares(flow_id);
CREATE INDEX idx_flow_shares_user ON flow_shares(user_id);

-- Teams/Workspaces for organizational sharing
CREATE TABLE workspaces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    owner_id        UUID NOT NULL REFERENCES users(id),
    settings        JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE workspace_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_workspace_members UNIQUE (workspace_id, user_id),
    CONSTRAINT chk_workspace_role CHECK (role IN ('owner', 'admin', 'member', 'viewer'))
);

CREATE INDEX idx_workspace_members_workspace ON workspace_members(workspace_id);
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);

-- Add workspace to flows
ALTER TABLE flows ADD COLUMN workspace_id UUID REFERENCES workspaces(id);
CREATE INDEX idx_flows_workspace ON flows(workspace_id) WHERE workspace_id IS NOT NULL;

-- =====================================================
-- Credentials/Secrets Management
-- =====================================================

CREATE TABLE credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(100) NOT NULL,
    description     TEXT,
    owner_id        UUID NOT NULL REFERENCES users(id),
    workspace_id    UUID REFERENCES workspaces(id),
    visibility      VARCHAR(20) NOT NULL DEFAULT 'private',
    encrypted_data  BYTEA NOT NULL,
    encryption_iv   BYTEA NOT NULL,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_credential_visibility CHECK (visibility IN ('private', 'workspace', 'shared'))
);

CREATE INDEX idx_credentials_owner ON credentials(owner_id);
CREATE INDEX idx_credentials_workspace ON credentials(workspace_id) WHERE workspace_id IS NOT NULL;
CREATE INDEX idx_credentials_type ON credentials(type);

-- Credential sharing
CREATE TABLE credential_shares (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id   UUID NOT NULL REFERENCES credentials(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    can_use         BOOLEAN DEFAULT TRUE,
    can_edit        BOOLEAN DEFAULT FALSE,
    shared_by       UUID NOT NULL REFERENCES users(id),
    shared_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_credential_shares UNIQUE (credential_id, user_id)
);

CREATE INDEX idx_credential_shares_credential ON credential_shares(credential_id);
CREATE INDEX idx_credential_shares_user ON credential_shares(user_id);

-- Credential types definition
CREATE TABLE credential_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(255),
    fields_schema   JSONB NOT NULL,
    test_endpoint   VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Insert common credential types
INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('http_basic', 'HTTP Basic Auth', 'Username and password authentication',
 '{"type":"object","properties":{"username":{"type":"string","title":"Username"},"password":{"type":"string","format":"password","title":"Password"}},"required":["username","password"]}'),
('http_bearer', 'HTTP Bearer Token', 'Bearer token authentication',
 '{"type":"object","properties":{"token":{"type":"string","format":"password","title":"Token"}},"required":["token"]}'),
('api_key', 'API Key', 'API key authentication',
 '{"type":"object","properties":{"key":{"type":"string","format":"password","title":"API Key"},"header":{"type":"string","title":"Header Name","default":"X-API-Key"}},"required":["key"]}'),
('oauth2', 'OAuth2', 'OAuth2 authentication',
 '{"type":"object","properties":{"clientId":{"type":"string","title":"Client ID"},"clientSecret":{"type":"string","format":"password","title":"Client Secret"},"tokenUrl":{"type":"string","format":"uri","title":"Token URL"},"scope":{"type":"string","title":"Scope"}},"required":["clientId","clientSecret","tokenUrl"]}'),
('database', 'Database Connection', 'Database connection credentials',
 '{"type":"object","properties":{"host":{"type":"string","title":"Host"},"port":{"type":"integer","title":"Port"},"database":{"type":"string","title":"Database"},"username":{"type":"string","title":"Username"},"password":{"type":"string","format":"password","title":"Password"}},"required":["host","database","username","password"]}'),
('ssh', 'SSH Key', 'SSH key authentication',
 '{"type":"object","properties":{"host":{"type":"string","title":"Host"},"port":{"type":"integer","title":"Port","default":22},"username":{"type":"string","title":"Username"},"privateKey":{"type":"string","format":"textarea","title":"Private Key"},"passphrase":{"type":"string","format":"password","title":"Passphrase"}},"required":["host","username","privateKey"]}');

-- =====================================================
-- User Activity Logging
-- =====================================================

CREATE TABLE user_activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    session_id      VARCHAR(100),
    activity_type   VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     UUID,
    resource_name   VARCHAR(255),
    details         JSONB,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_user_activities_user ON user_activities(user_id);
CREATE INDEX idx_user_activities_type ON user_activities(activity_type);
CREATE INDEX idx_user_activities_created ON user_activities(created_at DESC);
CREATE INDEX idx_user_activities_resource ON user_activities(resource_type, resource_id);

-- Partitioning for user activities (last 90 days)
-- Note: In production, implement proper partitioning strategy

-- =====================================================
-- Execution History Archival
-- =====================================================

-- Archive table for completed executions (30+ days old)
CREATE TABLE execution_archives (
    id                  UUID PRIMARY KEY,
    flow_version_id     UUID NOT NULL,
    flow_name           VARCHAR(255),
    flow_version        VARCHAR(50),
    status              VARCHAR(20) NOT NULL,
    trigger_input       JSONB,
    trigger_context     JSONB,
    output              JSONB,
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    duration_ms         INTEGER,
    triggered_by        UUID,
    trigger_type        VARCHAR(50),
    node_executions     JSONB,
    archived_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_execution_archives_flow ON execution_archives(flow_version_id);
CREATE INDEX idx_execution_archives_started ON execution_archives(started_at DESC);
CREATE INDEX idx_execution_archives_archived ON execution_archives(archived_at DESC);

-- Add output column to executions for storing final result
ALTER TABLE executions ADD COLUMN output JSONB;

-- =====================================================
-- Webhooks
-- =====================================================

CREATE TABLE webhooks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    path            VARCHAR(500) NOT NULL UNIQUE,
    method          VARCHAR(10) NOT NULL DEFAULT 'POST',
    is_active       BOOLEAN DEFAULT TRUE,
    auth_type       VARCHAR(50),
    auth_config     JSONB,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_webhook_method CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
);

CREATE INDEX idx_webhooks_flow ON webhooks(flow_id);
CREATE INDEX idx_webhooks_path ON webhooks(path) WHERE is_active;

-- =====================================================
-- Flow Templates
-- =====================================================

CREATE TABLE flow_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    tags            TEXT[],
    definition      JSONB NOT NULL,
    thumbnail_url   VARCHAR(500),
    is_official     BOOLEAN DEFAULT FALSE,
    usage_count     INTEGER DEFAULT 0,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_flow_templates_category ON flow_templates(category);
CREATE INDEX idx_flow_templates_tags ON flow_templates USING GIN(tags);

-- =====================================================
-- Scheduled Executions
-- =====================================================

CREATE TABLE schedules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    timezone        VARCHAR(50) NOT NULL DEFAULT 'UTC',
    is_active       BOOLEAN DEFAULT TRUE,
    input           JSONB,
    last_run_at     TIMESTAMP WITH TIME ZONE,
    next_run_at     TIMESTAMP WITH TIME ZONE,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_schedules_flow ON schedules(flow_id);
CREATE INDEX idx_schedules_next_run ON schedules(next_run_at) WHERE is_active;

-- =====================================================
-- Execution Retry Configuration
-- =====================================================

ALTER TABLE flow_versions ADD COLUMN retry_config JSONB DEFAULT '{"maxRetries": 3, "retryDelay": 1000, "backoffMultiplier": 2}';

COMMENT ON COLUMN flow_versions.retry_config IS 'Retry configuration for failed node executions';
