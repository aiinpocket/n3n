-- V24: Add performance indexes for frequently queried columns
-- These indexes cover the most impactful missing indexes identified in the database audit.

-- node_executions: queried on every execution detail view
CREATE INDEX IF NOT EXISTS idx_node_executions_execution_id ON node_executions (execution_id);
CREATE INDEX IF NOT EXISTS idx_node_executions_execution_node ON node_executions (execution_id, node_id);

-- user_activities: audit log pagination
CREATE INDEX IF NOT EXISTS idx_user_activities_user_id ON user_activities (user_id);
CREATE INDEX IF NOT EXISTS idx_user_activities_created_at ON user_activities (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_activities_activity_type ON user_activities (activity_type);

-- form_submissions: form data queries
CREATE INDEX IF NOT EXISTS idx_form_submissions_execution_id ON form_submissions (execution_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_execution_node ON form_submissions (execution_id, node_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_submitted_by ON form_submissions (submitted_by);

-- form_triggers: public form access
CREATE INDEX IF NOT EXISTS idx_form_triggers_flow_id ON form_triggers (flow_id);
CREATE INDEX IF NOT EXISTS idx_form_triggers_created_by ON form_triggers (created_by);

-- ai_token_usage: AI usage statistics
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_user_id ON ai_token_usage (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_created_at ON ai_token_usage (created_at DESC);

-- component_versions: component version queries
CREATE INDEX IF NOT EXISTS idx_component_versions_component_id ON component_versions (component_id);

-- skill_executions: skill tracking
CREATE INDEX IF NOT EXISTS idx_skill_executions_execution_id ON skill_executions (execution_id);
CREATE INDEX IF NOT EXISTS idx_skill_executions_skill_id ON skill_executions (skill_id);

-- plugin_versions: plugin version listing (N+1 prevention)
CREATE INDEX IF NOT EXISTS idx_plugin_versions_plugin_id ON plugin_versions (plugin_id);

-- plugin_installations: user plugin listing
CREATE INDEX IF NOT EXISTS idx_plugin_installations_user_id ON plugin_installations (user_id);

-- plugin_ratings: rating queries
CREATE INDEX IF NOT EXISTS idx_plugin_ratings_plugin_id ON plugin_ratings (plugin_id);

-- ai_usage_logs: usage analytics
CREATE INDEX IF NOT EXISTS idx_ai_usage_logs_user_id ON ai_usage_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_logs_created_at ON ai_usage_logs (created_at DESC);

-- execution_approvals: pending approval queries
CREATE INDEX IF NOT EXISTS idx_execution_approvals_status ON execution_approvals (status);
CREATE INDEX IF NOT EXISTS idx_execution_approvals_expires ON execution_approvals (expires_at);

-- approval_actions: approval action queries
CREATE INDEX IF NOT EXISTS idx_approval_actions_approval_id ON approval_actions (approval_id);

-- external_services: service listing
CREATE INDEX IF NOT EXISTS idx_external_services_created_by ON external_services (created_by, is_deleted);
CREATE INDEX IF NOT EXISTS idx_external_services_status ON external_services (status, is_deleted);

-- service_endpoints: endpoint listing
CREATE INDEX IF NOT EXISTS idx_service_endpoints_service_id ON service_endpoints (service_id);

-- executions_history: archive cleanup
CREATE INDEX IF NOT EXISTS idx_executions_history_archived_at ON executions_history (archived_at);

-- node_executions_history: archive cleanup
CREATE INDEX IF NOT EXISTS idx_node_executions_history_execution_id ON node_executions_history (execution_id);
CREATE INDEX IF NOT EXISTS idx_node_executions_history_archived_at ON node_executions_history (archived_at);

-- plugin_install_tasks: task listing
CREATE INDEX IF NOT EXISTS idx_plugin_install_tasks_user_id ON plugin_install_tasks (user_id);
CREATE INDEX IF NOT EXISTS idx_plugin_install_tasks_status ON plugin_install_tasks (status);
