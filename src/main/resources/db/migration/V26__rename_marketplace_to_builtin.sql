-- V26: Rename MARKETPLACE install source to BUILTIN
-- All tools are now considered built-in platform capabilities.
-- Docker Hub pulling is the fallback for custom tools.

UPDATE plugin_install_tasks
SET source = 'BUILTIN'
WHERE source = 'MARKETPLACE';
