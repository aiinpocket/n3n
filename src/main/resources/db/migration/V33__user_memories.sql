-- 使用者長期記憶：AI 助手跨對話記住的偏好、事實與習慣
-- category: preference | fact | project | style | general
-- source: assistant（AI 主動記下）| user（使用者手動新增）

CREATE TABLE IF NOT EXISTS user_memories (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL,
    content     TEXT          NOT NULL,
    category    VARCHAR(32)   NOT NULL DEFAULT 'general',
    source      VARCHAR(16)   NOT NULL DEFAULT 'assistant',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_memories_user_id ON user_memories (user_id);
