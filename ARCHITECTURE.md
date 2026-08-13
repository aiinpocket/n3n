# N3N 架構指南

本文件是模組地圖與「新東西放哪裡」的實用指南，目標是讓任何人都能快速找到程式碼位置、知道新功能該落在哪裡。API 細節、資料庫 schema 與設定項請見 [TECHNICAL.md](TECHNICAL.md)。

## 1. 總覽

N3N 是一個「模組化單體」（modular monolith）：

- 單一 Spring Boot 4（Java 21）應用程式，前端為 React 18 + TypeScript（Vite 建置後放進 boot jar 的 static resources）。
- 後端依「業務能力」切分頂層 package（近似 DDD bounded context），每個模組內部再依 controller / dto / entity / repository / service 分層。
- 節點以 Virtual Threads 非同步執行；執行狀態存 Redis；資料主存 PostgreSQL（Flyway 遷移），檔案類存 MongoDB GridFS。
- 跨模組溝通優先採 Spring 事件（ApplicationEvent）解耦，其次才是直接注入對方 service。

```
瀏覽器 (React) ── REST /api + STOMP WebSocket + SSE ──> Spring Boot
                                                        ├── PostgreSQL (Flyway, pgvector)
                                                        ├── Redis (執行狀態、快取、記憶)
                                                        └── MongoDB (檔案 / artifact)
```

## 2. 後端模組地圖

所有模組位於 `src/main/java/com/aiinpocket/n3n/`。

| 模組 | 職責 | 關鍵入口類 | 主要依賴模組 |
|------|------|-----------|--------------|
| `auth/` | 註冊、登入、JWT、Google 登入；發布 `UserAuthenticatedEvent` | `AuthController`, `AuthService`, `JwtService` | common |
| `oauth2/` | 第三方服務 OAuth2 授權與 token 管理 | `OAuth2Controller`, `OAuth2TokenService` | credential |
| `admin/` | 管理員使用者管理（ADMIN 端點） | `AdminController`, `AdminUserService` | auth |
| `flow/` | 流程定義 CRUD（nodes + edges JSON）、版本、匯出入、分享連結 | `FlowController`, `FlowService`, `FlowShareLinkController` | execution, auth |
| `execution/` | 流程引擎核心：DAG 解析、fan-in 相依檢查、併發模式、暫停/續跑、審批閘 | `ExecutionService`, `StateManager`, `ExecutionController` | flow, credential |
| `execution/handler/` | 節點型別系統：`NodeHandler` 介面、註冊表、表達式求值、憑證注入 | `NodeHandlerRegistry`, `NodeTypesController`, `ExpressionEvaluator`, `CredentialResolver` | credential, ai |
| `execution/handler/handlers/` | 具體節點實作，依類別分包：ai / action / browser / data / database / document / file / flowcontrol / gcp / google / integrations / messaging / network / nosql / scripting / transform / trigger | 各 `*NodeHandler` | 依節點而定 |
| `ai/` | AI 助手與 AI 基礎設施（子模組見下表） | `AiAssistantController`（controller/）, `AiProviderFactory`（provider/） | flow, execution |
| `credential/` | 加密憑證庫（AES-256-GCM，master key 首次啟動自動產生）、Recovery Key、連線測試 | `CredentialController`, `CredentialService`, `EncryptionService`, `EnvelopeEncryptionService` | common |
| `scheduler/` | Quartz 排程觸發、排程同步與復原 | `ScheduleController`, `SchedulerService` | flow, execution |
| `webhook/` | Webhook 觸發端點與管理 | `WebhookController`, `WebhookTriggerController` | flow, execution |
| `component/` | 可重用元件（子流程封裝） | `ComponentController`, `ComponentService` | flow |
| `plugin/` | 外掛安裝（Docker 容器化執行）、自訂工具 | `PluginService`, `PluginInstallController`, `CustomToolsController` | execution |
| `backup/` | 備份與雲端同步（S3 / R2 / SFTP），備份加密 | `BackupController`, `CloudSyncController` | flow, credential |
| `artifact/` | 執行產物儲存（MongoDB GridFS） | `ArtifactController`, `ArtifactStorageService` | execution |
| `site/` | AI 建站：站台 CRUD、公開存取（CSP sandbox）、自訂網域 / TLS | `SiteController`, `PublicSiteController`, `SiteDomainService` | ai |
| `hostedapp/` | 小型 App 託管：上傳 zip（docker-compose / Dockerfile）沙箱部署 | `HostedAppController`, `AppDeployService` | — |
| `template/` | 流程範本庫 | `FlowTemplateController` | flow |
| `skill/` | Skills 與 MCP 整合 | `SkillController`, `SkillService` | ai |
| `service/` | 外部服務定義（OpenAPI 匯入解析） | `ExternalServiceController`, `OpenApiParserService` | credential |
| `activity/` | 使用者活動紀錄（ADMIN） | `ActivityController` | auth |
| `dashboard/` | 儀表板彙總 API | `DashboardController` | flow, execution |
| `monitoring/` | 監控指標彙總（ADMIN） | `MonitoringController`, `MetricsAggregationService` | execution |
| `logging/` | 日誌檢視（ADMIN） | `LogViewerController` | — |
| `housekeeping/` | 資料清理排程（ADMIN） | `HousekeepingController`, `HousekeepingService` | execution |
| `api/` | 健康檢查等平台級端點 | `HealthController` | — |
| `common/` | 共用工具、Email、全域例外處理 | `EmailService` | — |

### ai/ 子模組

| 子模組 | 職責 |
|--------|------|
| `ai/agent/` | 流程建構 multi-agent（supervisor + subagent：Discovery / Builder / Optimizer，`IntentAnalyzer`）；`ai/agent/tools/` 是「建流程」工具（AddNodeTool 等） |
| `ai/billing/` | AI 用量計費與統計（`AiTokenUsage`，ADMIN 端點 `AiBillingController`） |
| `ai/codex/` | 程式碼生成輔助 |
| `ai/conversation/` | 對話管理（`ConversationManager`）、自動壓縮長上下文 |
| `ai/context/`, `ai/layout/` | AI 助手對話用的元件/技能上下文建構（`ComponentContextBuilder`, `SkillContextBuilder`）與流程佈局（`FlowLayoutEngine`）；連同 `AgentController`/`AgentService`/`ConversationService`（`/api/agent/conversations`）自原 `agent/` 模組移入（2026-08，裝置 agent 子系統移除） |
| `ai/embedding/` | 向量嵌入（OpenAI / Ollama 等），pgvector |
| `ai/module/` | AI 助手功能模組（`NaturalLanguageModule` 流程生成、`FlowOptimizationModule`；provider 呼叫一律走 `ai/provider/AssistantAiClient`） |
| `ai/prompt/` | 提示詞管理 |
| `ai/provider/` | 多供應商抽象（OpenAI / Claude / Gemini / 本地）：`AiProviderFactory` 為節點側統一入口，`AssistantAiClient` 為助手側統一入口（共用同一套平台設定，內建一次 failover） |
| `ai/rag/` | RAG：`RagService`、`InMemoryVectorStore`、retriever |
| `ai/security/` | AI 相關安全（提示注入防護等） |
| `ai/usermemory/` | 個人 AI 記憶（自動抽取：`MemoryExtractionService`） |

### 前端結構（`src/main/frontend/src/`）

| 目錄 | 內容 |
|------|------|
| `pages/` | 路由層級頁面（`App.tsx` 註冊路由，`components/MainLayout.tsx` 註冊選單） |
| `components/` | 共用元件；`components/edges/` 自訂邊、`components/nodes/` 自訂節點 |
| `stores/` | Zustand 狀態（`flowEditorStore` 為編輯器核心） |
| `api/` | axios API client（依後端模組對應分檔） |
| `i18n/locales/` | zh-TW / en / ja 三語，必須同步維護 |
| `hooks/`, `config/` | 自訂 hooks、節點型別顯示設定（`config/nodeTypes.ts`） |

## 3. 模組內部慣例

- **分層**：每個模組內固定 `controller/`（REST 入口）、`dto/`（request/response）、`entity/`（JPA）、`repository/`（Spring Data）、`service/`（業務邏輯）。Lombok 全面使用。
- **節點自動註冊**：實作 `NodeHandler`（通常繼承 `AbstractNodeHandler` 或 AI 側的 `AbstractAiNodeHandler`）並標 `@Component`，`NodeHandlerRegistry` 啟動時自動收集，`NodeTypesController` 對前端曝光 —— 不需要改任何註冊表程式碼。
- **Agent 工具自動註冊**：AI Agent「節點」的工具實作 `AgentNodeTool` 並標 `@Component`，由 `AgentNodeToolRegistry` 自動收集（位於 `execution/handler/handlers/ai/agent/`）。流程建構助手的工具（`ai/agent/tools/`）是另一套，服務 supervisor/subagent。
- **Spring 事件解耦**：跨模組通知用 ApplicationEvent。例：`auth` 發布 `UserAuthenticatedEvent`，`flow` 的 `PendingInvitationListener` 監聽處理待領取的共享邀請 —— `auth` 不需要知道 `flow` 的存在。
- **Flyway 遷移**：`src/main/resources/db/migration/`，命名 `V{n}__snake_case.sql`，目前到 V37。只增不改：既有版本檔案一旦上線不得修改。注意 `spring.jpa.hibernate.ddl-auto=update` 預設也開著，正式 schema 變更仍應以 Flyway 為準。
- **零設定原則**：`application.properties` 每個設定都是 `${ENV_VAR:localhost可用預設值}` 形式；新增設定不得要求本機啟動前必須先設環境變數。
- **表達式**：節點參數中的 `{{...}}` 由 `ExpressionEvaluator` 求值；需要憑證的節點透過 `CredentialResolver` 取得解密後憑證，不自行讀取 credential 資料表。

## 4. 「新東西放哪裡」決策表

| 想做的事 | 放哪裡 / 怎麼做 |
|----------|----------------|
| 新節點型別 | `execution/handler/handlers/{category}/` 新增 handler，`@Component` 即自動註冊；前端在 `config/nodeTypes.ts` 補顯示設定、i18n 三語補 `nodeTypes.{type}.label/description` |
| 新 AI Agent 工具（Agent 節點可呼叫） | `execution/handler/handlers/ai/agent/tools/` 實作 `AgentNodeTool` + `@Component` |
| 新流程建構助手工具 | `ai/agent/tools/` 實作 `AgentTool` |
| 新業務功能（獨立領域） | 新頂層模組 `com.aiinpocket.n3n.{feature}/`，內建 entity / repository / dto / service / controller；schema 用新 Flyway 版本 |
| 新前端頁面 | `pages/` 新增頁面 + `api/` 對應 client + `App.tsx` 註冊路由 + `MainLayout.tsx` 加選單項 + i18n 三語（zh-TW / en / ja）同步 |
| 新設定項 | `application.properties` 用 `${ENV_VAR:default}` 零設定模式；文件補進 TECHNICAL.md |
| 跨模組通知 | 定義 ApplicationEvent 於發布方模組的 `event/`，訂閱方寫 Listener；避免雙向 service 注入 |
| 使用者文件變更 | README 三語同步：`README.md`（zh-TW）、`README.en.md`、`README.ja.md` |

## 5. 已知架構債

誠實記錄目前的重複與遺留，供日後清理；在債務解決前，新程式碼請依「建議方向」選邊。

| 項目 | 現況 | 建議方向 |
|------|------|----------|
| 兩套 AI provider 設定系統 | 已解決：所有助手側消費者改走 `ai/provider/AssistantAiClient`（facade，底層為 `AiProviderConfig` + `AiProviderFactory` 平台共用設定）；`AiModuleConfig`/`SimpleAIProviderRegistry`/`ai/failover/` 已移除，`ai_module_configs` 資料表保留不再讀寫（2026-08） | 已解決 |
| 兩個 chain 節點 | 已解決：刪除舊 `ChainNodeHandler` 及其 `ai/chain/`、`ai/memory/` 舊棧；`aiChain` 型別由 `LegacyChainAliasHandler` 以平台共用 provider 相容承接（節點目錄標示 deprecated，AI 不再推薦），新流程一律用 `aiPipeline`（2026-08） | 已解決 |
| 同名介面棧 ×2 | 已解決：助手側 `ai/memory/` 刪除後 `MemoryStore`/`RedisMemoryStore` 已唯一；`ai/rag/` 的 `VectorStore` 改名 `RagVectorStore`，與節點側 `handlers/ai/vector/VectorStore` 不再同名（2026-08） | 已解決 |
| V17 / V18 pgvector 表 | 已解決：舊記憶棧移除後 `conversation_messages`/`memory_config`/`conversation_summaries`/`vector_documents`/`template_embeddings`/`flow_embeddings` 確認無程式讀寫；保留以維持 DDL 歷史，operator 可手動 DROP（見 TECHNICAL.md Retired Tables，2026-08） | 已解決 |
| 裝置 agent 子系統 | 已解決：平台轉為純 Web，整個裝置 agent 子系統（`gateway/`、`agent/` 裝置部分、原生 agents、`agent` 節點）已移除；AI 助手對話（`AgentController`/`AgentService`/`ConversationService`）移入 `ai/`（2026-08） | 已解決 |

## 6. 安全邊界摘要

| 邊界 | 機制 |
|------|------|
| 公開站台（site） | 每個回應強制 `Content-Security-Policy: sandbox`（`SiteSecurityHeaders.SITE_CSP`），使用者上傳的 HTML 一律在瀏覽器沙箱內執行，無同源權限 |
| 託管 App（hostedapp） | 容器硬化：memory/cpu 上限、`cap-drop ALL`（僅加回 CHOWN/DAC_OVERRIDE/FOWNER/SETGID/SETUID/KILL/NET_BIND_SERVICE 常規集）、`no-new-privileges`、pids-limit 256、禁止 bind mount，僅暴露宣告的 web 埠 |
| ADMIN 端點 | `@PreAuthorize("hasRole('ADMIN')")`：admin、activity、monitoring、logging、housekeeping、backup/cloudSync、ai/billing、component 等控制器 |
| 憑證 | AES-256-GCM 加密落庫；master key 首次啟動自動產生；支援 envelope encryption 與 Recovery Key；節點僅能經 `CredentialResolver` 取用 |
| 認證 | JWT（secret 自動產生並存 DB）、Google OAuth 登入；外部服務授權走 `oauth2/` token 管理 |
| 租戶隔離 / 併發 | 連線池金鑰含密碼雜湊（SHA-256，不落明文）、瀏覽器 session 與限流器以 userId 範圍化、node 用戶端具閒置回收（`n3n.node.client-idle-ttl-seconds`、`n3n.browser.session-ttl-seconds`）|
| RAG / 向量 / 記憶命名空間 | 所有向量/記憶存儲鍵一律由伺服器端以 userId 前綴推導：`RagService` 存儲鍵 = `userId:storeName`（storeName 空白→`default`），節點側 Redis 的 `vector:{userId:namespace}:…`、`memory:session:{userId:sessionId}`、`legacy-chain` 會話鍵皆加 userId 前綴；呼叫者提供的 storeName/namespace/sessionId 永遠不會單獨作為鍵，缺 userId 時 fail-closed |
| 流程執行緒模型 | 流程/節點 @Async 執行器（bean `taskExecutor`）為 virtual-thread-per-task（`SimpleAsyncTaskExecutor` + `setVirtualThreads(true)`），阻塞式節點 I/O 不佔用共用平台執行緒；每使用者（10）與全域（100）並行上限由 `ExecutionService` 以 DB 計數在建立執行前把關，與執行緒池大小無關 |
