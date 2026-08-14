-- Webhook 使用者命名空間：
-- 觸發網址改為 /webhook/{ns}/{path}，ns 為使用者的隨機短碼（非帳號衍生），
-- 唯一性從全域 (path) 改為 (ns, path, method)——不同使用者可各自使用相同 path。
-- 舊 webhook（ns 為 NULL）沿用 /webhook/{path} 舊網址，不受影響。

ALTER TABLE users ADD COLUMN IF NOT EXISTS webhook_ns VARCHAR(16);
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_webhook_ns ON users (webhook_ns) WHERE webhook_ns IS NOT NULL;

ALTER TABLE webhooks ADD COLUMN IF NOT EXISTS ns VARCHAR(16);

-- 移除舊的全域 path 唯一約束（ddl-auto 與 V1 兩種環境的約束名不同，動態尋找）
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'webhooks'
          AND con.contype = 'u'
          AND (
              SELECT array_agg(att.attname ORDER BY att.attname)
              FROM unnest(con.conkey) AS k(attnum)
              JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k.attnum
          ) = ARRAY['path']::name[]
    LOOP
        EXECUTE format('ALTER TABLE webhooks DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_webhooks_ns_path_method ON webhooks (ns, path, method) WHERE ns IS NOT NULL;
-- 舊資料維持全域唯一，避免新建立的無 ns 資料互撞（正常流程不會再產生 ns NULL 的新資料）
CREATE UNIQUE INDEX IF NOT EXISTS uk_webhooks_legacy_path_method ON webhooks (path, method) WHERE ns IS NULL;
