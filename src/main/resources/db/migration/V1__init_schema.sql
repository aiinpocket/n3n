-- =====================================================
-- N3N Flow Platform - Database Schema
-- Version: 1.0
-- =====================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- Users & Authentication
-- =====================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    avatar_url      VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    email_verified  BOOLEAN DEFAULT FALSE,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    login_attempts  INTEGER DEFAULT 0,
    locked_until    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_user_status CHECK (status IN ('pending', 'active', 'suspended', 'deleted'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE user_roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_user_roles UNIQUE (user_id, role),
    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'USER', 'VIEWER'))
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    device_info     VARCHAR(500),
    ip_address      VARCHAR(45),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    revoked_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_refresh_token UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;

CREATE TABLE email_verifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_email_verification_token UNIQUE (token_hash),
    CONSTRAINT chk_verification_type CHECK (type IN ('email_verify', 'password_reset'))
);

CREATE INDEX idx_email_verifications_user_id ON email_verifications(user_id);

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     UUID,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    details         JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- =====================================================
-- Flows
-- =====================================================

CREATE TABLE flows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_deleted      BOOLEAN DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_flows_name ON flows(name) WHERE NOT is_deleted;
CREATE INDEX idx_flows_created_by ON flows(created_by) WHERE NOT is_deleted;

CREATE TABLE flow_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id),
    version         VARCHAR(50) NOT NULL,
    definition      JSONB NOT NULL,
    settings        JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      UUID NOT NULL REFERENCES users(id),

    CONSTRAINT uk_flow_versions UNIQUE (flow_id, version),
    CONSTRAINT chk_flow_version_status CHECK (status IN ('draft', 'published', 'deprecated'))
);

CREATE INDEX idx_flow_versions_flow_id ON flow_versions(flow_id);

COMMENT ON COLUMN flow_versions.definition IS 'Flow JSON definition containing nodes and edges';
COMMENT ON COLUMN flow_versions.settings IS 'Concurrency control, timeout settings, etc.';

-- =====================================================
-- Executions
-- =====================================================

CREATE TABLE executions (
    id                  UUID PRIMARY KEY,
    flow_version_id     UUID NOT NULL REFERENCES flow_versions(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    trigger_input       JSONB,
    trigger_context     JSONB,
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    duration_ms         INTEGER,
    triggered_by        UUID REFERENCES users(id),
    trigger_type        VARCHAR(50),
    cancel_reason       TEXT,
    cancelled_by        UUID REFERENCES users(id),
    cancelled_at        TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_execution_status CHECK (
        status IN ('pending', 'running', 'completed', 'failed', 'cancelled', 'cancelling')
    )
);

CREATE INDEX idx_executions_flow_version ON executions(flow_version_id);
CREATE INDEX idx_executions_status ON executions(status);
CREATE INDEX idx_executions_started_at ON executions(started_at DESC);

CREATE TABLE node_executions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID NOT NULL REFERENCES executions(id),
    node_id             VARCHAR(100) NOT NULL,
    component_name      VARCHAR(255) NOT NULL,
    component_version   VARCHAR(50) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    duration_ms         INTEGER,
    error_message       TEXT,
    error_stack         TEXT,
    worker_id           VARCHAR(255),
    retry_count         INTEGER DEFAULT 0,

    CONSTRAINT uk_node_execution UNIQUE (execution_id, node_id),
    CONSTRAINT chk_node_status CHECK (
        status IN ('pending', 'running', 'completed', 'failed', 'cancelled', 'skipped')
    )
);

CREATE INDEX idx_node_executions_execution ON node_executions(execution_id);
CREATE INDEX idx_node_executions_component ON node_executions(component_name);

-- =====================================================
-- Components
-- =====================================================

CREATE TABLE components (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    icon            VARCHAR(255),
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_deleted      BOOLEAN DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_components_name ON components(name) WHERE NOT is_deleted;
CREATE INDEX idx_components_category ON components(category) WHERE NOT is_deleted;

CREATE TABLE component_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    component_id    UUID NOT NULL REFERENCES components(id),
    version         VARCHAR(50) NOT NULL,
    image           VARCHAR(500) NOT NULL,
    interface_def   JSONB NOT NULL,
    config_schema   JSONB,
    resources       JSONB NOT NULL DEFAULT '{"memory": "256Mi", "cpu": "200m"}',
    health_check    JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      UUID NOT NULL REFERENCES users(id),

    CONSTRAINT uk_component_versions UNIQUE (component_id, version),
    CONSTRAINT chk_component_version_status CHECK (status IN ('active', 'deprecated', 'disabled'))
);

CREATE INDEX idx_component_versions_component ON component_versions(component_id);

COMMENT ON COLUMN component_versions.interface_def IS 'Input/output interface definition';
COMMENT ON COLUMN component_versions.config_schema IS 'JSON Schema for component configuration';
COMMENT ON COLUMN component_versions.resources IS 'K8s resource requirements';

-- =====================================================
-- Default Admin User (password: admin123)
-- BCrypt hash generated with strength 12
-- =====================================================

INSERT INTO users (id, email, password_hash, name, status, email_verified)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin@n3n.local',
    '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'Admin',
    'active',
    true
);

INSERT INTO user_roles (user_id, role)
VALUES ('a0000000-0000-0000-0000-000000000001', 'ADMIN');

INSERT INTO user_roles (user_id, role)
VALUES ('a0000000-0000-0000-0000-000000000001', 'USER');
