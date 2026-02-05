-- Memory System Enhancement
-- Adds memory configuration and individual message tracking for advanced memory types

-- Memory configuration per conversation
CREATE TABLE memory_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    memory_type VARCHAR(20) NOT NULL DEFAULT 'BUFFER',
    max_tokens INT NOT NULL DEFAULT 4000,
    window_size INT NOT NULL DEFAULT 10,
    summary_model VARCHAR(50),
    summary_threshold INT NOT NULL DEFAULT 3000,
    vector_dimension INT NOT NULL DEFAULT 1536,
    vector_top_k INT NOT NULL DEFAULT 5,
    vector_similarity_threshold FLOAT NOT NULL DEFAULT 0.7,
    include_system_messages BOOLEAN NOT NULL DEFAULT false,
    ttl_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_memory_config_conversation UNIQUE (conversation_id)
);

-- Individual messages for more granular control
CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Conversation summaries (separate from messages)
CREATE TABLE conversation_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    messages_summarized INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversation_summary UNIQUE (conversation_id)
);

-- Indexes
CREATE INDEX idx_conversation_messages_conversation_id ON conversation_messages(conversation_id);
CREATE INDEX idx_conversation_messages_created_at ON conversation_messages(conversation_id, created_at);

-- Index for vector similarity search (requires pgvector extension)
-- CREATE INDEX idx_conversation_messages_embedding ON conversation_messages USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Comments
COMMENT ON TABLE memory_config IS 'Memory configuration per AI conversation (Buffer, Window, Summary, Vector types)';
COMMENT ON TABLE conversation_messages IS 'Individual messages for conversation memory with optional embeddings';
COMMENT ON TABLE conversation_summaries IS 'Conversation summaries for Summary memory type';
COMMENT ON COLUMN conversation_messages.embedding IS 'Vector embedding for semantic search (1536 dimensions for OpenAI)';
