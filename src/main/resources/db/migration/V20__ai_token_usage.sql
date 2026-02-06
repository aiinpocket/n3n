CREATE TABLE IF NOT EXISTS ai_token_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    execution_id UUID,
    node_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_usage_user ON ai_token_usage(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_token_usage_provider ON ai_token_usage(provider, created_at);
