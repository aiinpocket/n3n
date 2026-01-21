-- OAuth2 tokens for credential integration
CREATE TABLE IF NOT EXISTS oauth2_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id   UUID NOT NULL,
    provider        VARCHAR(50) NOT NULL,
    access_token    TEXT NOT NULL,
    refresh_token   TEXT,
    token_type      VARCHAR(50) DEFAULT 'Bearer',
    scope           VARCHAR(500),
    expires_at      TIMESTAMP WITH TIME ZONE,
    raw_response    TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for faster lookup by credential
CREATE INDEX IF NOT EXISTS idx_oauth2_tokens_credential ON oauth2_tokens(credential_id);

-- Unique constraint - one token per credential per provider
CREATE UNIQUE INDEX IF NOT EXISTS idx_oauth2_tokens_unique ON oauth2_tokens(credential_id, provider);
