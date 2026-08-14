-- 執行紀錄保留策略：
-- 預設保留 7 天（housekeeping.retention-days），但「每個流程最新一次執行」與
-- 「設為永久（pinned）」的執行不清理；artifact 隨執行清理，pinned 的 artifact 保留。

ALTER TABLE executions ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE artifacts ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_exec_pinned ON executions (pinned) WHERE pinned;
CREATE INDEX IF NOT EXISTS idx_artifacts_execution ON artifacts (execution_id) WHERE execution_id IS NOT NULL;
