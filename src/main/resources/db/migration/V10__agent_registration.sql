-- Agent Registration System
-- Supports one-time token registration and agent blocking

-- Agent registration tracking
CREATE TABLE agent_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    registration_token_hash VARCHAR(64) NOT NULL,  -- SHA256 hash of token
    device_id VARCHAR(64),  -- Assigned after registration
    device_name VARCHAR(255),
    platform VARCHAR(50),
    fingerprint VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at TIMESTAMP,
    blocked_at TIMESTAMP,
    blocked_reason TEXT,
    last_seen_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_agent_reg_token ON agent_registrations(registration_token_hash);
CREATE INDEX idx_agent_reg_user ON agent_registrations(user_id);
CREATE INDEX idx_agent_reg_status ON agent_registrations(status);
CREATE INDEX idx_agent_reg_device ON agent_registrations(device_id);

-- Gateway settings (singleton)
CREATE TABLE gateway_settings (
    id BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    gateway_domain VARCHAR(255) NOT NULL DEFAULT 'localhost',
    gateway_port INTEGER NOT NULL DEFAULT 9443,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default gateway settings
INSERT INTO gateway_settings (id, gateway_domain, gateway_port, enabled)
VALUES (1, 'localhost', 9443, true);

-- Comments
COMMENT ON TABLE agent_registrations IS 'Tracks agent registration with one-time tokens';
COMMENT ON COLUMN agent_registrations.registration_token_hash IS 'SHA256 hash of the one-time registration token';
COMMENT ON COLUMN agent_registrations.status IS 'PENDING, REGISTERED, BLOCKED';
COMMENT ON TABLE gateway_settings IS 'Gateway server configuration (singleton)';
