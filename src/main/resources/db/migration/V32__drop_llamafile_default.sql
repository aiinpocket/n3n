-- Remove llamafile as the default AI module provider.
-- The local llamafile provider has been removed; AI features now require a real configured provider.

-- Change column default from 'llamafile' to 'openai'
ALTER TABLE ai_module_configs ALTER COLUMN provider_type SET DEFAULT 'openai';

-- Migrate existing llamafile rows: switch to 'openai' but deactivate them
-- so no user is silently moved onto a provider they never configured.
UPDATE ai_module_configs
SET provider_type = 'openai',
    is_active = FALSE
WHERE provider_type = 'llamafile';
