-- V13: Add pinned_data column to flow_versions table
-- Allows users to pin test data to nodes for reuse across executions

ALTER TABLE flow_versions
ADD COLUMN IF NOT EXISTS pinned_data JSONB DEFAULT '{}'::jsonb;

-- Add index for querying versions with pinned data
CREATE INDEX IF NOT EXISTS idx_flow_versions_has_pinned_data
ON flow_versions ((pinned_data IS NOT NULL AND pinned_data != '{}'::jsonb));

COMMENT ON COLUMN flow_versions.pinned_data IS 'Pinned node output data. Key: nodeId, Value: pinned output data';
