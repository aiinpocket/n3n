-- Cloud Backup feature tables

-- Backup settings (singleton - only one row)
CREATE TABLE IF NOT EXISTS backup_settings (
    id            BIGINT       PRIMARY KEY DEFAULT 1,
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    provider      VARCHAR(20),
    endpoint      VARCHAR(500),
    bucket        VARCHAR(200),
    base_path     VARCHAR(500),
    access_key    VARCHAR(1000),
    secret_key    VARCHAR(1000),
    region        VARCHAR(50),
    service_account_json TEXT,
    sftp_host     VARCHAR(200),
    sftp_port     INTEGER      DEFAULT 22,
    sftp_username VARCHAR(200),
    sftp_password VARCHAR(1000),
    sftp_private_key TEXT,
    sftp_path     VARCHAR(500),
    schedule      VARCHAR(50),
    last_backup_at TIMESTAMP WITH TIME ZONE,
    version       BIGINT       DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT backup_settings_singleton CHECK (id = 1)
);

-- Backup history
CREATE TABLE IF NOT EXISTS backup_history (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    filename      VARCHAR(200) NOT NULL,
    file_size     BIGINT,
    provider      VARCHAR(20)  NOT NULL,
    checksum      VARCHAR(100),
    status        VARCHAR(20)  NOT NULL DEFAULT 'completed',
    error_message VARCHAR(2000),
    triggered_by  UUID,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_backup_history_created_at
    ON backup_history (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backup_history_status
    ON backup_history (status);
