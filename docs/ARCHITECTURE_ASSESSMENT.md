# 部署架構評估：單一 VM vs GCP 分散式服務

> 評估原則（依產品定位排序）：**第一優先是使用者體驗與 AI 能力精準度，第二才是成本。**
> 結論先講：**現階段建議「單一 VM + Docker Compose」起步，並在架構上保留無痛遷移到 GCP 託管服務的路徑。**

---

## 為什麼不是一開始就上分散式

N3N 的核心體驗是「描述需求 → AI 編排 → 定時產出作品」。影響體驗的關鍵是：

1. **AI 回應延遲**——瓶頸在外部 AI API（OpenAI/Anthropic/Gemini/fal.ai），不在我們的運算資源。分散式架構對這件事毫無幫助。
2. **流程引擎穩定度**——引擎跑在 Virtual Threads 上，單機可輕鬆支撐數千併發節點執行；狀態在 Redis、資料在 PostgreSQL，單 VM 已綽綽有餘。
3. **精準度**——取決於 prompt 工程（節點目錄注入、個人記憶、上下文管理），與部署拓撲無關。

在使用者規模明確成長之前，分散式帶來的是運維複雜度與更多故障點，而不是更好的體驗。

## 建議架構（Phase 1：單一 VM）

```
GCE VM（e2-standard-4：4 vCPU / 16GB RAM，約 $100/月）
├── n3n-app（Spring Boot，2GB heap）
├── PostgreSQL 15（pgvector）
├── Redis 7（執行狀態 + 快取）
└── MongoDB 7（文件型資料）
＋ Cloudflare（免費版：TLS、CDN、DDoS 基本防護）
＋ GCS bucket（每日 pg_dump + artifacts 異地備份，用現有 backup 模組）
```

- 磁碟：100GB pd-balanced（作品庫會長大，優先觀察這裡）
- 既有 `docker-compose.yml` 直接可用；`ADMIN_EMAILS`、`GOOGLE_OAUTH_CLIENT_ID`、AI 金鑰由管理介面設定
- 升級路徑：磁碟不夠 → 掛大；CPU 不夠 → 換 e2-standard-8（垂直擴充到 32 vCPU 前都不需要動架構）

### 何時該進 Phase 2（混合託管）

出現以下任一訊號時，把「狀態」搬出 VM，App 保持不變：

| 訊號 | 動作 | GCP 服務 |
|------|------|----------|
| DB 連線數/IOPS 吃緊、想要自動備份與 HA | PostgreSQL 搬出 | Cloud SQL（含 pgvector） |
| Redis 記憶體吃緊或需要持久化保證 | Redis 搬出 | Memorystore |
| 作品庫超過數百 GB | artifacts 改物件儲存 | GCS（backup 模組已支援 S3 相容介面） |
| 需要多實例（滾動更新零中斷） | App 進容器編排 | GKE Autopilot 或 Cloud Run（注意：排程/引擎需 sticky 單實例或分散鎖，Quartz 已支援 JDBC clustering） |

### 為什麼不建議 Cloud Run / Functions 起步

- 流程引擎與 Quartz 排程需要**常駐**，Serverless 的冷啟動與實例回收會直接傷害「關掉瀏覽器也持續運行」的核心承諾。
- WebSocket（STOMP）與 SSE 長連線在 Cloud Run 有時限與併發限制。
- 每月固定成本 vs 按量計費：常駐型工作負載在 VM 上永遠更便宜。

### 成本速算（月）

| 方案 | 成本 | 適用 |
|------|------|------|
| Phase 1 單一 VM | ~$100–130（VM + 磁碟 + GCS 備份） | 0–數百活躍使用者 |
| Phase 2 混合（VM + Cloud SQL + Memorystore） | ~$250–400 | 數百–數千活躍使用者、需要 SLA |
| Phase 3 GKE 全託管 | $500+ | 多租戶 SaaS、需要水平擴充與零中斷部署 |

> AI API 費用另計，且永遠是最大宗——這也是為什麼平台把金鑰與額度統一放在管理者頁面控管。

## 安全基線（不分階段都要）

- VM 防火牆只開 443（Cloudflare 回源）與 IAP SSH；不裸露 5432/6379/27017
- 平台金鑰 AES-256-GCM 落地加密（現有 credential 模組）；`JWT_SECRET` 首啟自動生成並存 DB
- 使用者站台（/sites/**）以 CSP sandbox 唯一來源隔離，無法讀取平台 token
- 每日備份到 GCS + 每季還原演練

## 網域與站台路由

平台可將每個使用者站台掛在獨立子網域，並支援使用者自帶網域：

1. **DNS**：建立 wildcard 記錄 `*.sites.example.com → VM IP`（A 記錄），主網域 `n3n.example.com → VM IP`。
2. **環境變數**（`.env`）：`SITE_MAIN_DOMAIN=n3n.example.com`、`SITE_BASE_DOMAIN=sites.example.com`。app 讀 `SITE_BASE_DOMAIN` 後，`HostSiteFilter` 會在 Spring Security 之前以 Host 路由服務 `{slug}.sites.example.com`；未設定時功能休眠，僅路徑式 `/sites/{slug}/`。
3. **啟動**：`docker compose --profile domain up -d` —— 追加 Caddy（80/443），主網域反向代理到 app，其餘 HTTPS 走 on-demand TLS，簽發前先問 `GET /api/public/sites/tls-check?domain=`（只有已發佈站台子網域或已驗證自訂網域回 200，防止憑證簽發濫用）。
4. **自訂網域**：使用者在站台抽屜輸入網域 → 平台產生 `n3n-verify-xxxx` token → 使用者建立 `TXT _n3n-verify.{domain}` 與 `CNAME {domain} → {slug}.sites.example.com` → 按「驗證」由後端做真實 DNS 查詢，通過後 Host 路由與 TLS 簽發即生效。

站台在子網域上已是獨立 origin，回應仍保留 CSP sandbox header 作縱深防禦。

### 小應用（動態容器）

使用者可上傳含 `docker-compose.yml` 或 `Dockerfile` 的 zip，平台在沙盒容器中跑起來並掛在 `{slug}.{SITE_BASE_DOMAIN}` 子網域（與靜態站台共用 wildcard 網域與 TLS 機制，slug 命名空間雙向互斥）。

**啟用流程**（功能預設關閉——執行使用者容器等同任意程式碼執行，須由營運者明確開啟）：

1. `docker network create n3n-apps` —— 平台與使用者容器共用的 bridge network，compose 啟動前必須先存在（部署時 `DockerContainerRuntime.ensureNetwork` 也會自動建立，但那發生在第一次部署，晚於 compose up 掛載 external network 的時點）。
2. `docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d`（網域模式再加 `--profile domain`）—— 覆蓋檔為 app 服務掛上 docker.sock、設 `APPS_ENABLED=true`、並把 app 容器接上 `n3n-apps` 網路。
3. 完成。TLS 不需額外設定：Caddy 的 on-demand TLS 簽發前問的 `/api/public/sites/tls-check` 對「執行中的小應用子網域」同樣回 200。

**請求路徑**：`AppProxyFilter`（order -111，早於 HostSiteFilter 的 -110 與 Security chain 的 -100）以 Host 解析 slug，命中執行中的小應用即串流反向代理到 `n3napp-{slug}-{webService}:{internalPort}`（共用 n3n-apps 網路的容器 DNS 名稱；`APPS_PROXY_TARGET=host-port` 時改走 `127.0.0.1:{hostPort}`，供平台直接跑在主機上的部署）。hop-by-hop headers 剝除、補 X-Forwarded-*，其餘 headers 原樣通過——應用在自己的子網域 origin 上，安全 header 由它自己決定。WebSocket v1 未支援（回 501）。

**安全模型回顧**：功能旗標預設關閉（關閉時所有 API 回 404、不建 Docker client）；每人應用數量上限；容器一律硬化（cap-drop ALL、no-new-privileges、記憶體/CPU/pids 上限、專用 bridge network、無 bind mount）；清理一律以 `n3n.app.id` label 篩選；秘密參數 AES-256-GCM 加密存放、API 永不回傳原文。掛載 docker.sock 等同給 app 容器主機 root 權限——只在信任平台使用者的環境啟用。

**WordPress 範例**：把下列 `docker-compose.yml` 打包成 zip 上傳——

```yaml
services:
  wordpress:
    image: wordpress:6
    ports:
      - "8080:80"
    environment:
      WORDPRESS_DB_HOST: db
      WORDPRESS_DB_USER: wp
      WORDPRESS_DB_PASSWORD: ${DB_PASSWORD}
      WORDPRESS_DB_NAME: wordpress
    depends_on:
      - db
  db:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: wordpress
      MARIADB_USER: wp
      MARIADB_PASSWORD: ${DB_PASSWORD}
      MARIADB_RANDOM_ROOT_PASSWORD: "1"
```

平台解析後自動偵測出 `DB_PASSWORD` 參數（名稱含 PASS → 判定為秘密、以密碼框呈現並加密存放）、web 服務為 `wordpress`（第一個有 ports 的服務）、容器內埠 80；填好參數按「建立並部署」，兩個容器會在專用網路裡以服務名互連，WordPress 掛上 `{slug}.{SITE_BASE_DOMAIN}`。
