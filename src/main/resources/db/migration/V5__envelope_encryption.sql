-- =====================================================
-- N3N Flow Platform - V5 Migration
-- Features: Envelope Encryption, Service-Credential Link
-- =====================================================

-- =====================================================
-- Data Encryption Keys (DEK) for Envelope Encryption
-- =====================================================

CREATE TABLE data_encryption_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_version     INTEGER NOT NULL,
    purpose         VARCHAR(50) NOT NULL DEFAULT 'CREDENTIAL',
    encrypted_key   BYTEA NOT NULL,
    encryption_iv   BYTEA NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    workspace_id    UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    rotated_at      TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_dek_version UNIQUE (purpose, key_version),
    CONSTRAINT chk_dek_status CHECK (status IN ('ACTIVE', 'DECRYPT_ONLY', 'RETIRED'))
);

CREATE INDEX idx_dek_purpose_status ON data_encryption_keys(purpose, status);
CREATE INDEX idx_dek_workspace ON data_encryption_keys(workspace_id) WHERE workspace_id IS NOT NULL;

COMMENT ON TABLE data_encryption_keys IS 'Data Encryption Keys for envelope encryption';
COMMENT ON COLUMN data_encryption_keys.encrypted_key IS 'DEK encrypted by Master Key';
COMMENT ON COLUMN data_encryption_keys.status IS 'ACTIVE=encrypt+decrypt, DECRYPT_ONLY=legacy data, RETIRED=unusable';

-- =====================================================
-- External Service - Credential Link
-- =====================================================

-- Add credential reference to external services
ALTER TABLE external_services ADD COLUMN credential_id UUID REFERENCES credentials(id) ON DELETE SET NULL;

CREATE INDEX idx_external_services_credential ON external_services(credential_id) WHERE credential_id IS NOT NULL;

COMMENT ON COLUMN external_services.credential_id IS 'Reference to credential used for authentication';

-- =====================================================
-- Flow Share Entity Support
-- =====================================================

-- Ensure flow_shares has proper indexes (already exists from V2)
-- Add invited_email for inviting non-registered users
ALTER TABLE flow_shares ADD COLUMN IF NOT EXISTS invited_email VARCHAR(255);
ALTER TABLE flow_shares ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_flow_shares_email ON flow_shares(invited_email) WHERE invited_email IS NOT NULL;

-- =====================================================
-- Credential Usage Tracking
-- =====================================================

-- Track which credentials are used by which services/nodes
CREATE TABLE credential_usage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id   UUID NOT NULL REFERENCES credentials(id) ON DELETE CASCADE,
    resource_type   VARCHAR(50) NOT NULL,  -- 'external_service', 'flow_node', etc.
    resource_id     UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_credential_usage UNIQUE (credential_id, resource_type, resource_id)
);

CREATE INDEX idx_credential_usage_credential ON credential_usage(credential_id);
CREATE INDEX idx_credential_usage_resource ON credential_usage(resource_type, resource_id);

COMMENT ON TABLE credential_usage IS 'Tracks where credentials are being used';

-- =====================================================
-- Secure Execution Log (masked sensitive data)
-- =====================================================

-- Add columns for tracking sensitive data masking in execution
ALTER TABLE node_executions ADD COLUMN IF NOT EXISTS has_sensitive_data BOOLEAN DEFAULT FALSE;
ALTER TABLE node_executions ADD COLUMN IF NOT EXISTS masked_input JSONB;
ALTER TABLE node_executions ADD COLUMN IF NOT EXISTS masked_output JSONB;

COMMENT ON COLUMN node_executions.has_sensitive_data IS 'Indicates if this execution involved sensitive credentials';
COMMENT ON COLUMN node_executions.masked_input IS 'Input with sensitive data masked for logging';
COMMENT ON COLUMN node_executions.masked_output IS 'Output with sensitive data masked for logging';

-- =====================================================
-- Update credentials table for DEK version tracking
-- =====================================================

ALTER TABLE credentials ADD COLUMN IF NOT EXISTS dek_version INTEGER DEFAULT 1;

COMMENT ON COLUMN credentials.dek_version IS 'DEK version used to encrypt this credential';
