-- V27: Fix V24 indexes that referenced incorrect table names (missing plural 's')
-- These indexes were created on non-existent singular table names.
-- Re-create them with the correct plural table names.

-- plugin_installations (V24 used singular: plugin_installation)
CREATE INDEX IF NOT EXISTS idx_plugin_installations_user_id ON plugin_installations (user_id);

-- plugin_ratings (V24 used singular: plugin_rating)
CREATE INDEX IF NOT EXISTS idx_plugin_ratings_plugin_id ON plugin_ratings (plugin_id);

-- ai_usage_logs (V24 used singular: ai_usage_log)
CREATE INDEX IF NOT EXISTS idx_ai_usage_logs_user_id ON ai_usage_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_logs_created_at ON ai_usage_logs (created_at DESC);

-- execution_approvals (V24 used singular: execution_approval)
CREATE INDEX IF NOT EXISTS idx_execution_approvals_status ON execution_approvals (status);
CREATE INDEX IF NOT EXISTS idx_execution_approvals_expires ON execution_approvals (expires_at);

-- approval_actions (V24 used singular: approval_action)
CREATE INDEX IF NOT EXISTS idx_approval_actions_approval_id ON approval_actions (approval_id);

-- external_services (V24 used singular: external_service)
CREATE INDEX IF NOT EXISTS idx_external_services_created_by ON external_services (created_by, is_deleted);
CREATE INDEX IF NOT EXISTS idx_external_services_status ON external_services (status, is_deleted);

-- plugin_install_tasks (V24 used singular: plugin_install_task)
CREATE INDEX IF NOT EXISTS idx_plugin_install_tasks_user_id_v27 ON plugin_install_tasks (user_id);
CREATE INDEX IF NOT EXISTS idx_plugin_install_tasks_status_v27 ON plugin_install_tasks (status);
