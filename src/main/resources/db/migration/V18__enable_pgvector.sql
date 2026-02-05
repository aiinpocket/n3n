-- Enable pgvector extension for vector similarity search
-- This migration enables the pgvector extension and creates indexes for efficient vector search

-- Enable the pgvector extension
-- Note: This requires the pgvector extension to be installed on the PostgreSQL server
-- For Docker: use the pgvector/pgvector:pg16 image
-- For installation: https://github.com/pgvector/pgvector#installation
CREATE EXTENSION IF NOT EXISTS vector;

-- Create IVFFlat index for conversation_messages embedding column
-- IVFFlat is faster for approximate nearest neighbor search with some accuracy tradeoff
-- lists = 100 is good for up to 1 million rows; adjust based on data size
CREATE INDEX IF NOT EXISTS idx_conversation_messages_embedding
ON conversation_messages
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- Create table for storing general vector documents (RAG, knowledge base, etc.)
CREATE TABLE IF NOT EXISTS vector_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    namespace VARCHAR(100) NOT NULL DEFAULT 'default',
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for vector_documents
CREATE INDEX IF NOT EXISTS idx_vector_documents_namespace ON vector_documents(namespace);
CREATE INDEX IF NOT EXISTS idx_vector_documents_created_by ON vector_documents(created_by);
CREATE INDEX IF NOT EXISTS idx_vector_documents_embedding
ON vector_documents
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- Create table for template embeddings (for semantic template search)
CREATE TABLE IF NOT EXISTS template_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES flow_templates(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_template_embedding UNIQUE (template_id)
);

-- Index for template embeddings
CREATE INDEX IF NOT EXISTS idx_template_embeddings_embedding
ON template_embeddings
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 50);

-- Create table for flow embeddings (for similar flow search)
CREATE TABLE IF NOT EXISTS flow_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    version INT NOT NULL DEFAULT 1,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_flow_embedding UNIQUE (flow_id, version)
);

-- Index for flow embeddings
CREATE INDEX IF NOT EXISTS idx_flow_embeddings_embedding
ON flow_embeddings
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- Comments
COMMENT ON TABLE vector_documents IS 'General vector document store for RAG and knowledge base';
COMMENT ON TABLE template_embeddings IS 'Template embeddings for semantic template search';
COMMENT ON TABLE flow_embeddings IS 'Flow embeddings for similar flow search';
COMMENT ON COLUMN vector_documents.namespace IS 'Namespace for isolating different document collections';
