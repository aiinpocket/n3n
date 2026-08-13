-- fal.ai 平台供應商支援 + 移除 Ollama：
-- 1. V6 的 chk_ai_provider CHECK 只允許 claude/openai/gemini/ollama，
--    擋掉了 openrouter 與新加入的 fal；供應商驗證已由應用層負責，移除 DB 層限制。
-- 2. Ollama 供應商已自程式中移除，停用殘留設定避免被解析為執行設定。
ALTER TABLE ai_provider_configs DROP CONSTRAINT IF EXISTS chk_ai_provider;
COMMENT ON COLUMN ai_provider_configs.provider IS '供應商類型: claude, openai, gemini, openrouter, fal';

UPDATE ai_provider_configs SET is_active = FALSE WHERE provider = 'ollama';
