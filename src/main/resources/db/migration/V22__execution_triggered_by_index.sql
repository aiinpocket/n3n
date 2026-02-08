-- Add index on executions.triggered_by for faster user-based execution queries
CREATE INDEX IF NOT EXISTS idx_executions_triggered_by
    ON executions(triggered_by);

-- Add composite index for common query pattern: user's recent executions
CREATE INDEX IF NOT EXISTS idx_executions_triggered_by_started
    ON executions(triggered_by, started_at DESC);

-- Add index on flow_id + opt_lock_version for optimistic locking lookups
CREATE INDEX IF NOT EXISTS idx_flows_opt_lock
    ON flows(id, opt_lock_version);

-- Add opt_lock_version column if not present (created by JPA ddl-auto but migration ensures it)
ALTER TABLE flows ADD COLUMN IF NOT EXISTS opt_lock_version BIGINT DEFAULT 0;
ALTER TABLE flow_versions ADD COLUMN IF NOT EXISTS opt_lock_version BIGINT DEFAULT 0;
