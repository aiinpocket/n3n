-- External Services Table
CREATE TABLE external_services (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    base_url        VARCHAR(500) NOT NULL,
    protocol        VARCHAR(20) NOT NULL DEFAULT 'REST',
    schema_url      VARCHAR(500),
    auth_type       VARCHAR(50),
    auth_config     JSONB,
    health_check    JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_deleted      BOOLEAN DEFAULT FALSE,

    CONSTRAINT uk_external_services_name UNIQUE (name),
    CONSTRAINT chk_external_service_protocol CHECK (protocol IN ('REST', 'GraphQL', 'gRPC')),
    CONSTRAINT chk_external_service_status CHECK (status IN ('active', 'inactive', 'error'))
);

CREATE INDEX idx_external_services_status ON external_services(status) WHERE NOT is_deleted;
CREATE INDEX idx_external_services_created_by ON external_services(created_by) WHERE NOT is_deleted;

COMMENT ON TABLE external_services IS '外部服務註冊表';
COMMENT ON COLUMN external_services.base_url IS '服務基礎 URL';
COMMENT ON COLUMN external_services.protocol IS '協議類型：REST, GraphQL, gRPC';
COMMENT ON COLUMN external_services.schema_url IS 'OpenAPI/Swagger 文檔路徑';
COMMENT ON COLUMN external_services.auth_type IS '認證類型：none, api_key, bearer, basic, oauth2';
COMMENT ON COLUMN external_services.auth_config IS '認證配置';
COMMENT ON COLUMN external_services.health_check IS '健康檢查配置';

-- Service Endpoints Table
CREATE TABLE service_endpoints (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id      UUID NOT NULL REFERENCES external_services(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    method          VARCHAR(10) NOT NULL,
    path            VARCHAR(500) NOT NULL,
    path_params     JSONB,
    query_params    JSONB,
    request_body    JSONB,
    response_schema JSONB,
    tags            TEXT[],
    is_enabled      BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_service_endpoints UNIQUE (service_id, method, path),
    CONSTRAINT chk_endpoint_method CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'))
);

CREATE INDEX idx_service_endpoints_service ON service_endpoints(service_id);
CREATE INDEX idx_service_endpoints_tags ON service_endpoints USING GIN(tags);

COMMENT ON TABLE service_endpoints IS '服務端點表';
COMMENT ON COLUMN service_endpoints.path_params IS '路徑參數定義，JSON Schema 格式';
COMMENT ON COLUMN service_endpoints.query_params IS '查詢參數定義，JSON Schema 格式';
COMMENT ON COLUMN service_endpoints.request_body IS '請求體定義，JSON Schema 格式';
COMMENT ON COLUMN service_endpoints.response_schema IS '響應格式定義，JSON Schema 格式';
COMMENT ON COLUMN service_endpoints.tags IS '標籤，用於分類和搜索';

-- Data Mappings Table
CREATE TABLE data_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_version_id UUID NOT NULL REFERENCES flow_versions(id) ON DELETE CASCADE,
    source_node_id  VARCHAR(100) NOT NULL,
    target_node_id  VARCHAR(100) NOT NULL,
    mappings        JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_data_mappings UNIQUE (flow_version_id, source_node_id, target_node_id)
);

CREATE INDEX idx_data_mappings_flow ON data_mappings(flow_version_id);

COMMENT ON TABLE data_mappings IS '數據映射配置表';
COMMENT ON COLUMN data_mappings.mappings IS '映射規則，格式: [{"source": "$.response.data.id", "target": "$.userId", "transform": "toString"}]';
