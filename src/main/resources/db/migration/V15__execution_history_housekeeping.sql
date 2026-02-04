-- =====================================================
-- N3N Flow Platform - Execution History & Housekeeping
-- Version: 15
-- Description: Create history tables for execution archival
--              and housekeeping configuration
-- =====================================================

-- =====================================================
-- 1. Executions History Table
-- =====================================================

CREATE TABLE IF NOT EXISTS executions_history (
    id                  UUID PRIMARY KEY,
    flow_version_id     UUID NOT NULL,
    flow_id             UUID,
    flow_name           VARCHAR(255),
    flow_version        VARCHAR(50),
    status              VARCHAR(20) NOT NULL,
    trigger_input       JSONB,
    trigger_context     JSONB,
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    duration_ms         INTEGER,
    triggered_by        UUID,
    triggered_by_email  VARCHAR(255),
    trigger_type        VARCHAR(50),
    cancel_reason       TEXT,
    cancelled_by        UUID,
    cancelled_at        TIMESTAMP WITH TIME ZONE,

    -- Pause/Wait related
    paused_at           TIMESTAMP WITH TIME ZONE,
    waiting_node_id     VARCHAR(100),
    pause_reason        VARCHAR(500),
    resume_condition    JSONB,

    -- Retry support
    retry_of            UUID,
    retry_count         INTEGER DEFAULT 0,
    max_retries         INTEGER DEFAULT 3,

    -- History metadata
    archived_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    original_created_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_executions_history_flow ON executions_history(flow_id);
CREATE INDEX IF NOT EXISTS idx_executions_history_flow_version ON executions_history(flow_version_id);
CREATE INDEX IF NOT EXISTS idx_executions_history_status ON executions_history(status);
CREATE INDEX IF NOT EXISTS idx_executions_history_archived ON executions_history(archived_at DESC);
CREATE INDEX IF NOT EXISTS idx_executions_history_started ON executions_history(started_at DESC);

COMMENT ON TABLE executions_history IS 'Archived execution records older than retention period';
COMMENT ON COLUMN executions_history.archived_at IS 'When the record was moved to history';
COMMENT ON COLUMN executions_history.original_created_at IS 'Original started_at from source table';

-- =====================================================
-- 2. Node Executions History Table
-- =====================================================

CREATE TABLE IF NOT EXISTS node_executions_history (
    id                  UUID PRIMARY KEY,
    execution_id        UUID NOT NULL,
    node_id             VARCHAR(100) NOT NULL,
    component_name      VARCHAR(255) NOT NULL,
    component_version   VARCHAR(50) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    duration_ms         INTEGER,
    error_message       TEXT,
    error_stack         TEXT,
    worker_id           VARCHAR(255),
    retry_count         INTEGER DEFAULT 0,

    -- Wait related
    waiting_for         VARCHAR(50),
    waiting_since       TIMESTAMP WITH TIME ZONE,

    -- History metadata
    archived_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_node_executions_history_execution ON node_executions_history(execution_id);
CREATE INDEX IF NOT EXISTS idx_node_executions_history_component ON node_executions_history(component_name);
CREATE INDEX IF NOT EXISTS idx_node_executions_history_archived ON node_executions_history(archived_at DESC);

COMMENT ON TABLE node_executions_history IS 'Archived node execution records';

-- =====================================================
-- 3. Housekeeping Configuration Table
-- =====================================================

CREATE TABLE IF NOT EXISTS housekeeping_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key          VARCHAR(100) NOT NULL UNIQUE,
    config_value        VARCHAR(500) NOT NULL,
    description         TEXT,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_by          UUID REFERENCES users(id)
);

-- Default housekeeping configuration
INSERT INTO housekeeping_config (config_key, config_value, description) VALUES
    ('retention_days', '30', 'Number of days to retain execution records in main table'),
    ('archive_to_history', 'false', 'Whether to archive to history tables or delete directly'),
    ('batch_size', '1000', 'Number of records to process per batch'),
    ('history_retention_days', '365', 'Number of days to retain records in history tables (0 = forever)')
ON CONFLICT (config_key) DO NOTHING;

COMMENT ON TABLE housekeeping_config IS 'Configuration settings for data housekeeping';

-- =====================================================
-- 4. Housekeeping Jobs Table (for tracking)
-- =====================================================

CREATE TABLE IF NOT EXISTS housekeeping_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type            VARCHAR(50) NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(20) NOT NULL DEFAULT 'running',
    records_processed   INTEGER DEFAULT 0,
    records_archived    INTEGER DEFAULT 0,
    records_deleted     INTEGER DEFAULT 0,
    error_message       TEXT,
    details             JSONB,

    CONSTRAINT chk_housekeeping_status CHECK (
        status IN ('running', 'completed', 'failed', 'cancelled')
    )
);

CREATE INDEX IF NOT EXISTS idx_housekeeping_jobs_started ON housekeeping_jobs(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_housekeeping_jobs_status ON housekeeping_jobs(status);

COMMENT ON TABLE housekeeping_jobs IS 'Tracks housekeeping job executions for auditing';

-- =====================================================
-- 5. Helper Functions for Housekeeping
-- =====================================================

-- Function to archive executions to history
CREATE OR REPLACE FUNCTION archive_execution(exec_id UUID) RETURNS VOID AS $$
DECLARE
    exec_record RECORD;
    flow_record RECORD;
BEGIN
    -- Get execution with flow info
    SELECT e.*, fv.flow_id, fv.version as flow_ver, f.name as flow_name
    INTO exec_record
    FROM executions e
    JOIN flow_versions fv ON e.flow_version_id = fv.id
    JOIN flows f ON fv.flow_id = f.id
    WHERE e.id = exec_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- Insert into history
    INSERT INTO executions_history (
        id, flow_version_id, flow_id, flow_name, flow_version,
        status, trigger_input, trigger_context,
        started_at, completed_at, duration_ms,
        triggered_by, trigger_type,
        cancel_reason, cancelled_by, cancelled_at,
        paused_at, waiting_node_id, pause_reason, resume_condition,
        retry_of, retry_count, max_retries,
        archived_at, original_created_at
    ) VALUES (
        exec_record.id, exec_record.flow_version_id, exec_record.flow_id,
        exec_record.flow_name, exec_record.flow_ver,
        exec_record.status, exec_record.trigger_input, exec_record.trigger_context,
        exec_record.started_at, exec_record.completed_at, exec_record.duration_ms,
        exec_record.triggered_by, exec_record.trigger_type,
        exec_record.cancel_reason, exec_record.cancelled_by, exec_record.cancelled_at,
        exec_record.paused_at, exec_record.waiting_node_id, exec_record.pause_reason, exec_record.resume_condition,
        exec_record.retry_of, exec_record.retry_count, exec_record.max_retries,
        NOW(), exec_record.started_at
    );

    -- Archive node executions
    INSERT INTO node_executions_history (
        id, execution_id, node_id, component_name, component_version,
        status, started_at, completed_at, duration_ms,
        error_message, error_stack, worker_id, retry_count,
        waiting_for, waiting_since, archived_at
    )
    SELECT
        id, execution_id, node_id, component_name, component_version,
        status, started_at, completed_at, duration_ms,
        error_message, error_stack, worker_id, retry_count,
        waiting_for, waiting_since, NOW()
    FROM node_executions
    WHERE execution_id = exec_id;

    -- Delete from main tables
    DELETE FROM node_executions WHERE execution_id = exec_id;
    DELETE FROM executions WHERE id = exec_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive_execution IS 'Archives an execution and its nodes to history tables';

-- Function to delete old executions directly
CREATE OR REPLACE FUNCTION delete_execution(exec_id UUID) RETURNS VOID AS $$
BEGIN
    DELETE FROM node_executions WHERE execution_id = exec_id;
    DELETE FROM executions WHERE id = exec_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION delete_execution IS 'Deletes an execution and its nodes without archiving';

-- =====================================================
-- 6. Add index for efficient cleanup queries
-- =====================================================

-- Index for finding old completed executions
CREATE INDEX IF NOT EXISTS idx_executions_cleanup
    ON executions(started_at)
    WHERE status IN ('completed', 'failed', 'cancelled');

-- =====================================================
-- End of Migration V15
-- =====================================================
