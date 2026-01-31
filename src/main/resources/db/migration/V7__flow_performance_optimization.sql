-- V7: Flow 表效能優化
-- 1. 新增複合索引優化查詢效能
-- 2. 擴展欄位長度避免截斷錯誤
-- 3. 新增全文搜索支援

-- =====================================================
-- 1. 複合索引優化
-- =====================================================

-- flow_versions: 優化按 flow_id 和 status 的查詢
CREATE INDEX IF NOT EXISTS idx_flow_versions_flow_status
    ON flow_versions(flow_id, status);

-- flow_versions: 優化按 flow_id 和建立時間的查詢（用於取得最新版本）
CREATE INDEX IF NOT EXISTS idx_flow_versions_flow_created
    ON flow_versions(flow_id, created_at DESC);

-- flow_shares: 優化按使用者和權限的查詢
CREATE INDEX IF NOT EXISTS idx_flow_shares_user_permission
    ON flow_shares(user_id, permission);

-- executions: 優化按狀態和時間的查詢
CREATE INDEX IF NOT EXISTS idx_executions_status_started
    ON executions(status, started_at DESC);

-- executions: 優化按流程版本和時間的查詢
CREATE INDEX IF NOT EXISTS idx_executions_flow_version_started
    ON executions(flow_version_id, started_at DESC);

-- node_executions: 優化按執行 ID 和狀態的查詢
CREATE INDEX IF NOT EXISTS idx_node_executions_execution_status
    ON node_executions(execution_id, status);

-- =====================================================
-- 2. 欄位長度擴展
-- =====================================================

-- flows.name: 從 VARCHAR(255) 擴展為 VARCHAR(500)
-- 允許較長的流程名稱
ALTER TABLE flows
    ALTER COLUMN name TYPE VARCHAR(500);

-- flows.description: 確保是 TEXT 類型（無長度限制）
-- 已經是 TEXT，不需要修改

-- flow_versions.version: 從 VARCHAR(50) 擴展為 VARCHAR(100)
-- 支援更靈活的版本命名（如 1.0.0-beta.1+build.123）
ALTER TABLE flow_versions
    ALTER COLUMN version TYPE VARCHAR(100);

-- =====================================================
-- 3. 全文搜索支援（PostgreSQL 特有）
-- =====================================================

-- 為 flows 表新增全文搜索向量欄位
ALTER TABLE flows
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- 建立觸發器自動更新搜索向量
CREATE OR REPLACE FUNCTION flows_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 移除舊觸發器（如果存在）
DROP TRIGGER IF EXISTS flows_search_vector_trigger ON flows;

-- 建立新觸發器
CREATE TRIGGER flows_search_vector_trigger
    BEFORE INSERT OR UPDATE OF name, description ON flows
    FOR EACH ROW
    EXECUTE FUNCTION flows_search_vector_update();

-- 建立全文搜索 GIN 索引
CREATE INDEX IF NOT EXISTS idx_flows_search
    ON flows USING GIN(search_vector);

-- 更新現有資料的搜索向量
UPDATE flows SET search_vector =
    setweight(to_tsvector('simple', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(description, '')), 'B')
WHERE search_vector IS NULL;

-- =====================================================
-- 4. 統計資訊更新
-- =====================================================

-- 分析表以更新統計資訊，優化查詢計劃
ANALYZE flows;
ANALYZE flow_versions;
ANALYZE flow_shares;
ANALYZE executions;
ANALYZE node_executions;
