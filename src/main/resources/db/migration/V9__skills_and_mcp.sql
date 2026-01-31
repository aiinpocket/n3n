-- V9: Skills System and MCP Integration
-- Skills: 預備好的 API，執行時不需 AI
-- MCP: Model Context Protocol 工具伺服器

-- Skills 表
CREATE TABLE skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,  -- 'file', 'web', 'data', 'http', 'notify', 'system'
    icon VARCHAR(50),
    is_builtin BOOLEAN DEFAULT false,
    is_enabled BOOLEAN DEFAULT true,

    -- 實作方式（純程式碼，非 AI）
    implementation_type VARCHAR(20) NOT NULL,  -- 'java', 'http', 'script'
    implementation_config JSONB NOT NULL DEFAULT '{}',

    -- 輸入輸出 Schema (JSON Schema 格式)
    input_schema JSONB NOT NULL,
    output_schema JSONB,

    -- 權限控制
    owner_id UUID REFERENCES users(id),
    visibility VARCHAR(20) DEFAULT 'private',  -- 'private', 'workspace', 'public'

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Skill 執行記錄
CREATE TABLE skill_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id UUID REFERENCES skills(id) ON DELETE SET NULL,
    skill_name VARCHAR(100) NOT NULL,  -- 保留技能名稱以防技能被刪除
    execution_id UUID REFERENCES flow_executions(id) ON DELETE CASCADE,
    node_execution_id UUID REFERENCES node_executions(id) ON DELETE CASCADE,

    input_data JSONB,
    output_data JSONB,
    status VARCHAR(20) NOT NULL,  -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    error_message TEXT,
    duration_ms INTEGER,

    executed_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- MCP Server 設定
CREATE TABLE mcp_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,

    -- 傳輸設定
    transport_type VARCHAR(20) NOT NULL,  -- 'stdio', 'sse', 'http'
    transport_config JSONB NOT NULL,
    -- stdio: {"command": "npx", "args": ["-y", "@anthropic/mcp-server-filesystem", "/tmp"]}
    -- sse: {"url": "http://localhost:3001/sse"}
    -- http: {"url": "http://localhost:3001"}

    -- 連線狀態
    status VARCHAR(20) DEFAULT 'disconnected',  -- 'connected', 'disconnected', 'error'
    last_connected_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,

    -- 快取的工具清單
    cached_tools JSONB,
    cached_prompts JSONB,
    cached_resources JSONB,

    -- 權限控制
    owner_id UUID REFERENCES users(id),
    visibility VARCHAR(20) DEFAULT 'private',

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- MCP 工具快取（方便查詢）
CREATE TABLE mcp_tools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID REFERENCES mcp_servers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    input_schema JSONB NOT NULL,
    UNIQUE(server_id, name)
);

-- 索引
CREATE INDEX idx_skills_category ON skills(category);
CREATE INDEX idx_skills_is_builtin ON skills(is_builtin);
CREATE INDEX idx_skills_owner_id ON skills(owner_id);
CREATE INDEX idx_skill_executions_execution_id ON skill_executions(execution_id);
CREATE INDEX idx_skill_executions_skill_id ON skill_executions(skill_id);
CREATE INDEX idx_mcp_servers_owner_id ON mcp_servers(owner_id);
CREATE INDEX idx_mcp_servers_status ON mcp_servers(status);
CREATE INDEX idx_mcp_tools_server_id ON mcp_tools(server_id);
