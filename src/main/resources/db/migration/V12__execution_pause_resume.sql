-- =====================================================
-- N3N Flow Platform - Execution Pause & Resume Support
-- Version: 12
-- Description: Add waiting status and approval tables for
--              human-in-the-loop workflow support
-- =====================================================

-- =====================================================
-- 1. Extend execution status to include 'waiting'
-- =====================================================

ALTER TABLE executions DROP CONSTRAINT chk_execution_status;
ALTER TABLE executions ADD CONSTRAINT chk_execution_status CHECK (
    status IN ('pending', 'running', 'waiting', 'completed', 'failed', 'cancelled', 'cancelling')
);

-- Add pause/wait related columns to executions
ALTER TABLE executions ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE executions ADD COLUMN IF NOT EXISTS waiting_node_id VARCHAR(100);
ALTER TABLE executions ADD COLUMN IF NOT EXISTS pause_reason VARCHAR(500);
ALTER TABLE executions ADD COLUMN IF NOT EXISTS resume_condition JSONB;

-- Retry support columns
ALTER TABLE executions ADD COLUMN IF NOT EXISTS retry_of UUID REFERENCES executions(id);
ALTER TABLE executions ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0;
ALTER TABLE executions ADD COLUMN IF NOT EXISTS max_retries INTEGER DEFAULT 3;

COMMENT ON COLUMN executions.paused_at IS 'Timestamp when execution entered waiting state';
COMMENT ON COLUMN executions.waiting_node_id IS 'ID of the node that triggered the wait';
COMMENT ON COLUMN executions.pause_reason IS 'Human-readable reason for waiting (e.g., "Waiting for manual approval")';
COMMENT ON COLUMN executions.resume_condition IS 'JSON containing resume condition type and configuration';
COMMENT ON COLUMN executions.retry_of IS 'Reference to original execution if this is a retry';
COMMENT ON COLUMN executions.retry_count IS 'Number of retry attempts';
COMMENT ON COLUMN executions.max_retries IS 'Maximum allowed retry attempts';

-- Index for finding waiting executions
CREATE INDEX IF NOT EXISTS idx_executions_waiting ON executions(status, paused_at)
    WHERE status = 'waiting';

-- =====================================================
-- 2. Extend node_executions status to include 'waiting'
-- =====================================================

ALTER TABLE node_executions DROP CONSTRAINT chk_node_status;
ALTER TABLE node_executions ADD CONSTRAINT chk_node_status CHECK (
    status IN ('pending', 'running', 'waiting', 'completed', 'failed', 'cancelled', 'skipped')
);

-- Add waiting related columns to node_executions
ALTER TABLE node_executions ADD COLUMN IF NOT EXISTS waiting_for VARCHAR(50);
ALTER TABLE node_executions ADD COLUMN IF NOT EXISTS waiting_since TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN node_executions.waiting_for IS 'Type of wait: approval, form, webhook, timer';
COMMENT ON COLUMN node_executions.waiting_since IS 'Timestamp when node started waiting';

-- =====================================================
-- 3. Execution Approvals Table
-- =====================================================

CREATE TABLE IF NOT EXISTS execution_approvals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    node_id             VARCHAR(100) NOT NULL,
    approval_type       VARCHAR(50) NOT NULL DEFAULT 'manual',

    -- Approval configuration
    message             TEXT,
    required_approvers  INTEGER NOT NULL DEFAULT 1,
    approval_mode       VARCHAR(20) NOT NULL DEFAULT 'any',
    metadata            JSONB,

    -- Approval state
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    approved_count      INTEGER NOT NULL DEFAULT 0,
    rejected_count      INTEGER NOT NULL DEFAULT 0,

    -- Time constraints
    expires_at          TIMESTAMP WITH TIME ZONE,

    -- Timestamps
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_execution_approval UNIQUE (execution_id, node_id),
    CONSTRAINT chk_approval_status CHECK (
        status IN ('pending', 'approved', 'rejected', 'expired', 'cancelled')
    ),
    CONSTRAINT chk_approval_mode CHECK (
        approval_mode IN ('any', 'all', 'majority')
    ),
    CONSTRAINT chk_approval_type CHECK (
        approval_type IN ('manual', 'webhook', 'timer', 'conditional')
    )
);

CREATE INDEX IF NOT EXISTS idx_approvals_execution ON execution_approvals(execution_id);
CREATE INDEX IF NOT EXISTS idx_approvals_status ON execution_approvals(status, created_at)
    WHERE status = 'pending';

COMMENT ON TABLE execution_approvals IS 'Stores approval requests for waiting executions';
COMMENT ON COLUMN execution_approvals.approval_type IS 'Type of approval: manual (human), webhook (external), timer (auto), conditional (rule-based)';
COMMENT ON COLUMN execution_approvals.approval_mode IS 'How approvals are counted: any (first), all (unanimous), majority (>50%)';
COMMENT ON COLUMN execution_approvals.metadata IS 'Additional data for approval (e.g., form fields, display data)';

-- =====================================================
-- 4. Approval Actions Table
-- =====================================================

CREATE TABLE IF NOT EXISTS approval_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_id     UUID NOT NULL REFERENCES execution_approvals(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    action          VARCHAR(20) NOT NULL,
    comment         TEXT,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_approval_action UNIQUE (approval_id, user_id),
    CONSTRAINT chk_approval_action CHECK (action IN ('approve', 'reject'))
);

CREATE INDEX IF NOT EXISTS idx_approval_actions_approval ON approval_actions(approval_id);
CREATE INDEX IF NOT EXISTS idx_approval_actions_user ON approval_actions(user_id);

COMMENT ON TABLE approval_actions IS 'Records individual approval/rejection actions by users';
COMMENT ON COLUMN approval_actions.metadata IS 'Additional data submitted with the action (e.g., form responses)';

-- =====================================================
-- 5. Form Submissions Table
-- =====================================================

CREATE TABLE IF NOT EXISTS form_submissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    node_id         VARCHAR(100) NOT NULL,
    data            JSONB NOT NULL,
    submitted_by    UUID REFERENCES users(id),
    submitted_ip    VARCHAR(45),
    submitted_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_form_submission UNIQUE (execution_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_form_submissions_execution ON form_submissions(execution_id);

COMMENT ON TABLE form_submissions IS 'Stores form data submitted during flow execution';
COMMENT ON COLUMN form_submissions.data IS 'JSON object containing form field values';

-- =====================================================
-- 6. Form Triggers Table (for FormTrigger nodes)
-- =====================================================

CREATE TABLE IF NOT EXISTS form_triggers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    node_id         VARCHAR(100) NOT NULL,
    form_token      VARCHAR(64) NOT NULL UNIQUE,
    config          JSONB NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    max_submissions INTEGER DEFAULT 0,
    submission_count INTEGER DEFAULT 0,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uk_form_trigger_flow_node UNIQUE (flow_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_form_triggers_token ON form_triggers(form_token);
CREATE INDEX IF NOT EXISTS idx_form_triggers_flow ON form_triggers(flow_id);

COMMENT ON TABLE form_triggers IS 'Stores configuration for FormTrigger nodes';
COMMENT ON COLUMN form_triggers.form_token IS 'Unique token for public form URL';
COMMENT ON COLUMN form_triggers.config IS 'Form fields configuration (JSON Schema)';
COMMENT ON COLUMN form_triggers.max_submissions IS '0 = unlimited submissions';

-- =====================================================
-- 7. Add SSH credential types
-- =====================================================

-- This is handled in the application code, but we can add a comment
-- to document the new credential types:
-- - ssh_password: { username, password }
-- - ssh_private_key: { username, privateKey, passphrase? }

-- =====================================================
-- End of Migration V12
-- =====================================================
