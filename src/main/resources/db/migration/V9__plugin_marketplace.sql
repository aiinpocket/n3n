-- Plugin Marketplace Schema

-- Plugin definitions (from marketplace, cached locally)
CREATE TABLE plugins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    author VARCHAR(200) NOT NULL,
    author_url VARCHAR(500),
    repository_url VARCHAR(500),
    documentation_url VARCHAR(500),
    icon_url VARCHAR(500),
    pricing VARCHAR(20) NOT NULL DEFAULT 'free', -- free, paid, freemium
    price DECIMAL(10, 2),
    tags TEXT[], -- PostgreSQL array
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Plugin versions
CREATE TABLE plugin_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id UUID NOT NULL REFERENCES plugins(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    release_notes TEXT,
    min_platform_version VARCHAR(50),
    config_schema JSONB, -- JSON Schema for plugin configuration
    node_definitions JSONB NOT NULL, -- Node type definitions provided by this plugin
    capabilities TEXT[], -- Required capabilities (shell, screen, fs, etc.)
    dependencies JSONB, -- Other plugin dependencies
    download_url VARCHAR(500),
    checksum VARCHAR(128),
    download_count INTEGER DEFAULT 0,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plugin_id, version)
);

-- Plugin installations (per user/workspace)
CREATE TABLE plugin_installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id UUID NOT NULL REFERENCES plugins(id),
    plugin_version_id UUID NOT NULL REFERENCES plugin_versions(id),
    user_id UUID NOT NULL REFERENCES users(id),
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB, -- User-specific plugin configuration
    UNIQUE(plugin_id, user_id)
);

-- Plugin ratings
CREATE TABLE plugin_ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id UUID NOT NULL REFERENCES plugins(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plugin_id, user_id)
);

-- Indexes
CREATE INDEX idx_plugins_category ON plugins(category);
CREATE INDEX idx_plugins_pricing ON plugins(pricing);
CREATE INDEX idx_plugins_tags ON plugins USING GIN(tags);
CREATE INDEX idx_plugin_versions_plugin_id ON plugin_versions(plugin_id);
CREATE INDEX idx_plugin_installations_user_id ON plugin_installations(user_id);
CREATE INDEX idx_plugin_installations_plugin_id ON plugin_installations(plugin_id);
CREATE INDEX idx_plugin_ratings_plugin_id ON plugin_ratings(plugin_id);

-- View for plugin summary with stats
CREATE OR REPLACE VIEW plugin_summary AS
SELECT
    p.id,
    p.name,
    p.display_name,
    p.description,
    p.category,
    p.author,
    p.icon_url,
    p.pricing,
    p.price,
    p.tags,
    pv.version AS latest_version,
    pv.id AS latest_version_id,
    COALESCE(SUM(pv2.download_count), 0) AS total_downloads,
    COALESCE(AVG(pr.rating), 0) AS avg_rating,
    COUNT(DISTINCT pr.id) AS rating_count,
    p.updated_at
FROM plugins p
LEFT JOIN plugin_versions pv ON pv.plugin_id = p.id
    AND pv.published_at = (SELECT MAX(published_at) FROM plugin_versions WHERE plugin_id = p.id)
LEFT JOIN plugin_versions pv2 ON pv2.plugin_id = p.id
LEFT JOIN plugin_ratings pr ON pr.plugin_id = p.id
GROUP BY p.id, p.name, p.display_name, p.description, p.category, p.author,
         p.icon_url, p.pricing, p.price, p.tags, pv.version, pv.id, p.updated_at;

-- Insert some sample plugins for development
INSERT INTO plugins (id, name, display_name, description, category, author, pricing, tags) VALUES
('11111111-1111-1111-1111-111111111111', 'openai-integration', 'OpenAI Integration', 'Connect to OpenAI APIs for GPT, DALL-E, and Whisper models', 'ai', 'N3N Team', 'free', ARRAY['ai', 'gpt', 'openai', 'llm']),
('22222222-2222-2222-2222-222222222222', 'slack-connector', 'Slack Connector', 'Send messages, manage channels, and automate Slack workflows', 'messaging', 'Community', 'free', ARRAY['slack', 'messaging', 'notifications']),
('33333333-3333-3333-3333-333333333333', 'google-sheets', 'Google Sheets', 'Read, write, and manage Google Sheets spreadsheets', 'data', 'N3N Team', 'free', ARRAY['google', 'sheets', 'spreadsheet', 'data']),
('44444444-4444-4444-4444-444444444444', 'aws-s3', 'AWS S3 Storage', 'Upload, download, and manage files in Amazon S3 buckets', 'storage', 'Community', 'free', ARRAY['aws', 's3', 'storage', 'cloud']),
('55555555-5555-5555-5555-555555555555', 'json-transformer', 'JSON Transformer', 'Transform, filter, and manipulate JSON data with JSONPath expressions', 'utility', 'N3N Team', 'free', ARRAY['json', 'transform', 'utility']),
('66666666-6666-6666-6666-666666666666', 'discord-bot', 'Discord Bot', 'Build Discord bots with message handling and slash commands', 'messaging', 'Community', 'freemium', ARRAY['discord', 'bot', 'messaging']);

-- Insert sample versions
INSERT INTO plugin_versions (plugin_id, version, release_notes, node_definitions, capabilities) VALUES
('11111111-1111-1111-1111-111111111111', '1.2.0', 'Added GPT-4 Turbo support',
 '{"nodes": [{"type": "openai", "displayName": "OpenAI", "category": "ai", "description": "OpenAI API integration", "resources": {"chat": {"displayName": "Chat", "operations": [{"name": "createCompletion", "displayName": "Create Completion"}]}, "image": {"displayName": "Image", "operations": [{"name": "createImage", "displayName": "Create Image"}]}}}]}',
 ARRAY['network']),
('22222222-2222-2222-2222-222222222222', '2.0.1', 'Bug fixes and performance improvements',
 '{"nodes": [{"type": "slack", "displayName": "Slack", "category": "messaging", "description": "Slack integration", "resources": {"message": {"displayName": "Message", "operations": [{"name": "send", "displayName": "Send Message"}]}}}]}',
 ARRAY['network']),
('33333333-3333-3333-3333-333333333333', '1.5.0', 'Added batch operations',
 '{"nodes": [{"type": "googleSheets", "displayName": "Google Sheets", "category": "data", "description": "Google Sheets integration", "resources": {"sheet": {"displayName": "Sheet", "operations": [{"name": "read", "displayName": "Read"}, {"name": "append", "displayName": "Append"}]}}}]}',
 ARRAY['network']),
('44444444-4444-4444-4444-444444444444', '1.0.0', 'Initial release',
 '{"nodes": [{"type": "awsS3", "displayName": "AWS S3", "category": "storage", "description": "AWS S3 storage", "resources": {"object": {"displayName": "Object", "operations": [{"name": "upload", "displayName": "Upload"}, {"name": "download", "displayName": "Download"}]}}}]}',
 ARRAY['network']),
('55555555-5555-5555-5555-555555555555', '2.1.0', 'Added JSONPath support',
 '{"nodes": [{"type": "jsonTransformer", "displayName": "JSON Transformer", "category": "utility", "description": "JSON transformation", "resources": {"json": {"displayName": "JSON", "operations": [{"name": "parse", "displayName": "Parse"}, {"name": "stringify", "displayName": "Stringify"}, {"name": "getValue", "displayName": "Get Value"}]}}}]}',
 ARRAY[]),
('66666666-6666-6666-6666-666666666666', '1.3.0', 'Added slash commands',
 '{"nodes": [{"type": "discord", "displayName": "Discord", "category": "messaging", "description": "Discord bot integration", "resources": {"message": {"displayName": "Message", "operations": [{"name": "send", "displayName": "Send"}, {"name": "reply", "displayName": "Reply"}]}}}]}',
 ARRAY['network']);
