package com.aiinpocket.n3n.common.constant;

/**
 * 統一的狀態常數定義
 * 避免硬編碼魔法字串散布在程式碼各處
 */
public final class Status {

    private Status() {
        // 防止實例化
    }

    /**
     * 流程版本狀態
     */
    public static final class FlowVersion {
        public static final String DRAFT = "draft";
        public static final String PUBLISHED = "published";
        public static final String DEPRECATED = "deprecated";

        private FlowVersion() {}
    }

    /**
     * 使用者狀態
     */
    public static final class User {
        public static final String ACTIVE = "active";
        public static final String PENDING = "pending";
        public static final String SUSPENDED = "suspended";
        public static final String DELETED = "deleted";

        private User() {}
    }

    /**
     * 執行狀態
     */
    public static final class Execution {
        public static final String PENDING = "pending";
        public static final String RUNNING = "running";
        public static final String COMPLETED = "completed";
        public static final String FAILED = "failed";
        public static final String CANCELLED = "cancelled";

        private Execution() {}
    }

    /**
     * 節點執行狀態
     */
    public static final class NodeExecution {
        public static final String PENDING = "pending";
        public static final String RUNNING = "running";
        public static final String COMPLETED = "completed";
        public static final String FAILED = "failed";
        public static final String SKIPPED = "skipped";

        private NodeExecution() {}
    }

    /**
     * 元件版本狀態
     */
    public static final class ComponentVersion {
        public static final String ACTIVE = "active";
        public static final String DEPRECATED = "deprecated";
        public static final String DISABLED = "disabled";

        private ComponentVersion() {}
    }

    /**
     * 認證狀態
     */
    public static final class Credential {
        public static final String ACTIVE = "active";
        public static final String MIGRATING = "migrating";
        public static final String DISABLED = "disabled";

        private Credential() {}
    }

    /**
     * 對話狀態
     */
    public static final class Conversation {
        public static final String ACTIVE = "active";
        public static final String COMPLETED = "completed";
        public static final String CANCELLED = "cancelled";
        public static final String ARCHIVED = "archived";

        private Conversation() {}
    }

    /**
     * 外部服務狀態
     */
    public static final class ExternalService {
        public static final String ACTIVE = "active";
        public static final String INACTIVE = "inactive";
        public static final String ERROR = "error";

        private ExternalService() {}
    }
}
