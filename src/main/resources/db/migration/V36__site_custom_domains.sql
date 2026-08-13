-- 站台自訂網域：使用者可將自己的網域指向站台
-- custom_domain 全站唯一（NULL 可重複）；驗證採 DNS TXT token

ALTER TABLE sites ADD COLUMN IF NOT EXISTS custom_domain VARCHAR(255);
ALTER TABLE sites ADD COLUMN IF NOT EXISTS custom_domain_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sites ADD COLUMN IF NOT EXISTS custom_domain_token VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sites_custom_domain ON sites (custom_domain);
