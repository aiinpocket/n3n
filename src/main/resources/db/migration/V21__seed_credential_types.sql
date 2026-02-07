-- =====================================================
-- Ensure credential_types seed data exists
-- V2 migration may have been skipped when Hibernate ddl-auto=update
-- creates tables before Flyway runs. This migration ensures the
-- essential credential types are always present.
-- =====================================================

INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('http_basic', 'HTTP Basic Auth', 'Username and password authentication',
 '{"type":"object","properties":{"username":{"type":"string","title":"Username"},"password":{"type":"string","format":"password","title":"Password"}},"required":["username","password"]}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('http_bearer', 'HTTP Bearer Token', 'Bearer token authentication',
 '{"type":"object","properties":{"token":{"type":"string","format":"password","title":"Token"}},"required":["token"]}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('api_key', 'API Key', 'API key authentication',
 '{"type":"object","properties":{"key":{"type":"string","format":"password","title":"API Key"},"header":{"type":"string","title":"Header Name","default":"X-API-Key"}},"required":["key"]}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('oauth2', 'OAuth2', 'OAuth2 authentication',
 '{"type":"object","properties":{"clientId":{"type":"string","title":"Client ID"},"clientSecret":{"type":"string","format":"password","title":"Client Secret"},"tokenUrl":{"type":"string","format":"uri","title":"Token URL"},"scope":{"type":"string","title":"Scope"}},"required":["clientId","clientSecret","tokenUrl"]}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('database', 'Database Connection', 'Database connection credentials',
 '{"type":"object","properties":{"host":{"type":"string","title":"Host"},"port":{"type":"integer","title":"Port"},"database":{"type":"string","title":"Database"},"username":{"type":"string","title":"Username"},"password":{"type":"string","format":"password","title":"Password"}},"required":["host","database","username","password"]}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO credential_types (name, display_name, description, fields_schema) VALUES
('ssh', 'SSH Key', 'SSH key authentication',
 '{"type":"object","properties":{"host":{"type":"string","title":"Host"},"port":{"type":"integer","title":"Port","default":22},"username":{"type":"string","title":"Username"},"privateKey":{"type":"string","format":"textarea","title":"Private Key"},"passphrase":{"type":"string","format":"password","title":"Passphrase"}},"required":["host","username","privateKey"]}')
ON CONFLICT (name) DO NOTHING;
