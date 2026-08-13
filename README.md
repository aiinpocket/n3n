# N3N Flow Platform

[English](README.en.md) | [日本語](README.ja.md) | 繁體中文

> 用說的就能建立自動化流程 - 讓 AI 幫你把想法變成可執行的工作流程

> **第一次使用？** 請先閱讀 [快速入門指南](QUICK_START.md)，3 分鐘即可啟動。

---

## 硬體需求

N3N 平台內建多項服務，請根據使用場景選擇適當的硬體配置：

### 最低需求（基本運行）

| 項目 | 規格 |
|------|------|
| **CPU** | 2 核心 |
| **記憶體** | 4 GB |
| **硬碟** | 10 GB SSD |
| **作業系統** | Windows 10/11、macOS 12+、Ubuntu 20.04+ |

> 適合：個人使用、簡單流程、無 AI 功能

### 建議需求（含雲端 AI）

| 項目 | 規格 |
|------|------|
| **CPU** | 4 核心 |
| **記憶體** | 8 GB |
| **硬碟** | 20 GB SSD |
| **網路** | 穩定的網際網路連線 |

> 適合：日常使用、中等複雜度流程、使用 OpenAI/Claude/Gemini 等雲端 AI

### 內建服務資源佔用

| 服務 | 記憶體佔用 | 說明 |
|------|-----------|------|
| **N3N App** | ~512 MB | Spring Boot 主應用程式 |
| **PostgreSQL** | ~256 MB | 關聯式資料庫 |
| **Redis** | ~128 MB | 快取與執行狀態 |
| **MongoDB** | ~256 MB | NoSQL 資料庫（工作流程節點使用） |

---

## 這是什麼？

N3N 是一個**視覺化流程自動化平台**，讓你可以：

- **用自然語言描述**你想要的工作流程，AI 助手幫你生成
- **拖拉調整**流程圖，不需要寫任何程式碼
- **連接外部服務**（API、資料庫等），自動化你的日常工作

適合**不會寫程式但想要自動化工作流程的人**，也適合**喜歡規劃流程的人**參與設計。

---

## 快速開始

### 1. 安裝 Docker

如果你還沒有 Docker，請先安裝：

| 作業系統 | 下載連結 |
|---------|---------|
| Windows | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) |
| Mac | [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/) |
| Linux | [Docker Engine](https://docs.docker.com/engine/install/) |

### 2. 啟動 N3N

打開終端機：
- **Windows**: 按 `Win + R`，輸入 `cmd` 後按 Enter
- **Mac**: 按 `Cmd + Space`，輸入 `Terminal` 後按 Enter
- **Linux**: 按 `Ctrl + Alt + T`

在終端機中執行以下指令：

```bash
# 下載專案（需要安裝 Git，或者直接從 GitHub 下載 ZIP 解壓）
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# 啟動服務（首次需要下載映像檔，約需數分鐘）
docker compose up -d
```

> **沒有 Git？** 你也可以直接[下載 ZIP 檔案](https://github.com/aiinpocket/n3n/archive/refs/heads/main.zip)，解壓後在該資料夾中執行 `docker compose up -d`。

> **零配置啟動**：N3N 採用開箱即用設計，你不需要手動設定任何東西：
> - 資料庫（PostgreSQL / Redis / MongoDB）自動啟動並連接
> - JWT 簽名密鑰在首次啟動時自動隨機產生
> - 資料加密主金鑰自動產生並安全保存
> - 無需設定任何環境變數即可運行

### 3. 開始使用

> **首次啟動需要等待**：首次啟動需下載 Docker 映像檔，取決於網路速度可能需要幾分鐘。後續重啟僅需 60-90 秒。可用以下指令追蹤進度：
> ```bash
> # 追蹤啟動進度
> docker compose logs -f app
> # 確認所有容器已就緒
> docker compose ps
> ```
> 當看到 `Started N3nApplication` 字樣時，即可開啟瀏覽器。
>

打開瀏覽器，前往：**http://localhost:8080**

首次使用會引導你：
1. **建立管理員帳號** — 填入名稱、Email 和密碼（12-128 字元，需包含大寫、小寫、數字、特殊符號中的至少 3 種）
2. **備份 Recovery Key** — 系統會顯示 12 個英文單詞，這是你恢復加密資料的唯一方式。頁面上有「複製」按鈕，請務必複製或抄寫保存在安全的地方
3. **驗證 Recovery Key** — 輸入剛才的 12 個單詞以確認你已備份
4. 設定 AI 助手（選擇你有的 AI 服務）
5. 開始建立你的第一個流程！

> **重要**：Recovery Key 只會在首次設定時顯示一次。如果遺失，將無法恢復加密的憑證資料。請務必備份！
>
> **備份建議**：將 Recovery Key 存放在密碼管理器（如 1Password、Bitwarden）中，或抄寫在紙上存放於安全處。請不要以截圖或未加密的文字檔保存。
>
> **Recovery Key 遺失怎麼辦？** 如果遺失 Recovery Key，你仍然可以正常登入使用系統。但如果需要將加密憑證還原到新環境，或進行雲端備份還原時，將無法解密舊的憑證資料。建議遺失後儘快重新建立所需的憑證。

---

## 設定 AI 助手

N3N 支援多種 AI 服務，你可以選擇任何一種：

| AI 服務 | 說明 | 申請連結 |
|--------|------|---------|
| **Claude** | Anthropic 的 AI，擅長分析與推理 | [申請 API Key](https://console.anthropic.com/) |
| **ChatGPT** | OpenAI 的 AI，廣泛的知識與程式能力 | [申請 API Key](https://platform.openai.com/api-keys) |
| **Gemini** | Google 的 AI，支援多模態 | [申請 API Key](https://aistudio.google.com/apikey) |
| **Ollama** | 本地運行，免費且隱私 | [下載 Ollama](https://ollama.com/download) |

> **提示**：如果你不想付費，可以選擇 Ollama 在自己電腦上運行 AI，完全免費！

---

## 常見問題

### 如何確認 Docker 已安裝？

```bash
docker --version
# 應該看到類似 Docker version 24.0.6
```

如果沒有回應，表示 Docker 尚未安裝或未啟動。Windows/Mac 使用者請先開啟 Docker Desktop 應用程式。

### 如何確認服務已啟動？

```bash
docker compose ps
# 應該看到 app、postgres、redis 等容器狀態為 running
```

### 啟動失敗怎麼辦？

先查看日誌找出原因：
```bash
# 查看即時日誌
docker compose logs -f app

# 查看所有服務狀態
docker compose ps

# 查看資料庫遷移日誌（如果是資料庫問題）
docker compose logs postgres
```

如果仍無法解決，重新啟動：
```bash
docker compose down
docker compose up -d
```

### 連接埠被佔用怎麼辦？

如果看到 `port 8080 is already in use` 的錯誤，表示其他程式正在使用 8080 連接埠。

```bash
# 查看是哪個程式佔用了 8080
# Mac/Linux:
lsof -i :8080
# Windows (PowerShell):
netstat -ano | findstr :8080

# 或改用其他連接埠啟動（例如改用 9090）
N3N_PORT=9090 docker compose up -d
# 然後用 http://localhost:9090 開啟
```

### 如何停止服務？

```bash
docker compose down
```

### 如何更新到最新版本？

```bash
# 建議先備份資料庫（以防萬一）
docker compose exec postgres pg_dump -U n3n n3n > backup_$(date +%Y%m%d).sql

# 更新並重啟
git pull
docker compose down
docker compose up -d --build
```

> **資料安全**：更新通常不會影響資料（Flyway 自動處理資料庫遷移），但備份總是好的。

### 如何存取資料庫（開發用）？

內部服務（PostgreSQL、Redis、MongoDB）預設不對外暴露端口。如需直接存取：

```bash
# 方法一：使用 Docker exec
docker compose exec postgres psql -U n3n

# 方法二：使用開發模式覆蓋（暴露所有端口）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

### 如何還原資料庫備份？

```bash
# 從 SQL 備份還原
docker compose exec -T postgres psql -U n3n n3n < backup_20260216.sql
```

### Docker Desktop 沒有啟動？

如果執行 `docker compose up` 出現 `Cannot connect to the Docker daemon` 錯誤：

- **Windows / Mac**：先開啟 Docker Desktop 應用程式，等待系統匣圖示顯示「Running」
- **Linux**：執行 `sudo systemctl start docker`

### 磁碟空間不足？

首次啟動需要下載約 3-4 GB 映像。確認可用空間：

```bash
# Mac/Linux
df -h .
# Windows (PowerShell)
Get-PSDrive C
```

如果空間不足，可以清理 Docker 舊映像：`docker system prune -a`


---

## 生產部署安全檢查清單

將 N3N 部署到可公開存取的環境前，請先完成以下安全設定：

```bash
# 1. 複製環境變數範本
cp .env.example .env

# 2. 編輯 .env 設定以下項目
```

| 項目 | 說明 | 範例 |
|------|------|------|
| `POSTGRES_PASSWORD` | 資料庫密碼（取代預設的 `n3n`） | 使用隨機密碼 |
| `REDIS_PASSWORD` | Redis 密碼（預設無密碼） | 使用隨機密碼 |
| `ALLOWED_ORIGINS` | 你的域名（取代 localhost） | `https://n3n.example.com` |
| `N3N_PORT` | 對外連接埠 | `8080`（預設） |

> **密碼產生器**：可用 `openssl rand -base64 24` 產生隨機密碼。

### HTTPS 反向代理設定

N3N 預設透過 HTTP 提供服務。生產環境建議搭配反向代理啟用 HTTPS：

**使用 Caddy（最簡單，自動 HTTPS）：**

```bash
# 安裝 Caddy 後，建立 Caddyfile
echo 'n3n.example.com {
  reverse_proxy localhost:8080
}' > Caddyfile

caddy start
```

**使用 Nginx：**

```nginx
server {
    listen 443 ssl;
    server_name n3n.example.com;

    ssl_certificate /etc/letsencrypt/live/n3n.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/n3n.example.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # WebSocket 支援
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

> 設定反向代理後，記得在 `.env` 中更新 `ALLOWED_ORIGINS=https://n3n.example.com`。

---

## 功能特色

- **AI 流程生成** - 用自然語言描述，AI 幫你建立流程
- **視覺化編輯** - 拖拉式介面，直覺調整流程
- **錯誤處理路由** - 視覺化區分正常流程與錯誤處理路徑（綠色/紅色/藍色連線）
- **即時監控** - 看到流程執行的每一步，節點狀態即時更新
- **Webhook 觸發** - 讓外部系統（如 GitHub、Slack）自動觸發流程
- **流程模板庫** - 官方模板，支援瀏覽、搜尋、從現有流程建立模板並跨平台分享
- **執行審批系統** - 流程執行到審批節點時暫停，審批者可附留言核准或駁回
- **技能系統** - 內建常用自動化技能，無需額外設定
- **安全儲存** - 你的 API Key 和密碼都有 AES-256 加密保護
- **自訂 Docker 工具** - 平台內建 90+ 工具節點，也可從 Docker Hub 拉取額外工具容器，自動註冊為流程節點
- **審批待辦儀表板** - 獨立頁面集中管理所有待審批項目，一鍵核准或駁回
- **系統清理管理** - 管理員可查看統計、手動觸發清理過期執行記錄
- **流程分享與協作** - 與團隊成員分享流程，支援檢視/編輯權限控制
- **OAuth2 整合** - 支援第三方 OAuth2 服務連接，簡化認證流程
- **即時日誌查看** - 管理員可即時串流查看系統日誌（SSE）
- **系統監控儀表板** - 即時查看 JVM 記憶體、CPU、流程執行統計
- **流程驗證** - 編輯器工具列內建驗證按鈕，發佈前即時檢查 DAG 結構
- **排程管理** - Cron 排程觸發流程，支援暫停/恢復/立即執行（Quartz 整合）
- **Webhook 測試** - 在 Webhook 管理頁面直接測試觸發，即時確認流程連動
- **插件評分** - 自訂工具市集支援社群評分與評論
- **儲存為模板** - 將現有流程版本一鍵轉換為可重複使用的模板
- **表單觸發器** - 建立公開表單，外部使用者提交後自動觸發流程執行，無需登入
- **雲端加密備份** - 自動加密備份流程、憑證、設定到 S3/GCS/R2/SFTP，透過 Recovery Key 還原
- **多語言支援** - 完整的英文、繁體中文、日文介面（2,400+ 翻譯鍵值）
- **OpenAPI 文件** - 內建 Swagger UI，260+ 個 API 端點完整文件化
- **豐富的鍵盤快捷鍵** - 16 組編輯器快捷鍵（儲存、發佈、撤銷、AI 助手等），附完整說明
- **智慧搜尋篩選** - 憑證、元件、審批、執行等列表頁面均支援即時搜尋與分類篩選
- **表格欄位排序** - 流程、執行、排程等列表支援依名稱、時間、狀態排序
- **儀表板深層連結** - 統計卡片可點擊跳轉至對應的篩選列表頁面
- **外部服務管理** - 匯入 OpenAPI 規格，自動產生 API 呼叫節點並管理連線
- **元件註冊管理** - 自訂元件版本控制，支援啟用/棄用/回滾管理
- **活動歷史追蹤** - 記錄使用者操作、Webhook 觸發、登入等事件，供安全審計使用

### 錯誤處理路由

N3N 支援三種連線類型，讓你清楚區分正常流程與錯誤處理：

| 連線類型 | 顏色 | 說明 |
|---------|------|------|
| **成功路徑** | 綠色 | 節點執行成功後走這條路線 |
| **錯誤路徑** | 紅色虛線 | 節點執行失敗時走這條路線 |
| **總是執行** | 藍色 | 無論成功或失敗都會執行 |

在流程編輯器中，點擊連線即可設定其類型。

---

## 自訂 Docker 工具

N3N 讓你從 Docker Hub 拉取工具容器，自動註冊為可用的流程節點：

### 使用方式

1. 進入「自訂 Docker 工具」頁面
2. 輸入 Docker Hub 映像名稱（例如 `n3n/tool-slack`）
3. 點擊「拉取」，系統自動下載並註冊
4. 設定對應的憑證（API Key 等）
5. 在流程編輯器中即可使用新的節點

---

## 環境變數設定（選填）

N3N 採用**零配置設計**，所有設定都有合理的預設值。以下環境變數僅在特殊需求時才需要設定：

### 資料庫連線（外部資料庫）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/n3n` | PostgreSQL 連線字串 |
| `DATABASE_USERNAME` | `n3n` | 資料庫使用者名稱 |
| `DATABASE_PASSWORD` | `n3n` | 資料庫密碼 |
| `REDIS_HOST` | `localhost` | Redis 主機 |
| `REDIS_PORT` | `6379` | Redis 連接埠 |
| `REDIS_PASSWORD` | （空） | Redis 密碼（設定後自動啟用認證） |
| `MONGO_USER` | `n3n_admin` | MongoDB 使用者名稱 |
| `MONGO_PASSWORD` | `n3n_dev_only` | MongoDB 密碼 |
| `MONGO_DB` | `n3n_test` | MongoDB 資料庫名稱 |

### 安全設定

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `JWT_SECRET` | 自動產生 | JWT 簽名密鑰（叢集部署時需統一設定） |
| `N3N_MASTER_KEY` | 自動產生 | 資料加密主金鑰（**生產環境必須設定**） |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | CORS 允許來源 |

> **生產環境注意**：`N3N_MASTER_KEY` 在開發環境會自動產生並持久化到 `/data/keys/master.key`，但在生產環境（`SPRING_PROFILES_ACTIVE=prod`）下**必須手動設定**，否則應用會拒絕啟動。產生方式：`openssl rand -base64 32`

### 容器編排（插件系統）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `ORCHESTRATOR_TYPE` | `docker` | 容器編排引擎（`docker` / `kubernetes` / `auto`） |
| `K8S_NAMESPACE` | `n3n` | Kubernetes 主命名空間 |
| `K8S_PLUGIN_NAMESPACE` | `n3n-plugins` | Kubernetes 插件命名空間 |
| `K8S_SERVICE_ACCOUNT` | `n3n-plugin-manager` | Kubernetes 服務帳號 |

> **auto 模式**：系統啟動時自動偵測環境（K8s Service Account → Docker Socket → Docker CLI），選擇對應的容器編排引擎。

### AI 供應商（雲端）

N3N 的 AI 功能（流程生成、多模態節點、流程優化）使用雲端 AI 供應商。
在「憑證管理」或「AI 設定」頁面填入 API Key 即可使用：

| 供應商 | 用途 | 餘額查詢 |
|--------|------|---------|
| OpenRouter | 統一入口，一把 Key 可用上百個模型（Claude / GPT / Gemini） | 支援（官方 API） |
| OpenAI | 對話、視覺理解、TTS、圖片生成 | 本地估算 |
| Anthropic (Claude) | 對話、流程生成 | 本地估算 |
| Google (Gemini) | 對話、多模態 | 本地估算 |
| fal.ai | AI 圖片生成（FLUX）、AI 影片生成（Veo / Kling / Hailuo） | 支援（官方 API） |
| ElevenLabs | AI 語音合成 | 支援（字元配額） |

前往「AI 餘額管理」頁面可以一次查看所有供應商的剩餘餘額與用量統計。

### Google 登入（選填）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `GOOGLE_OAUTH_CLIENT_ID` | （空，功能停用） | Google OAuth Client ID，設定後登入頁自動出現「使用 Google 登入」按鈕，首次登入自動建立帳號 |
| `ADMIN_EMAILS` | `mopacke2422@gmail.com` | 管理者 Email 清單（逗號分隔），名單內的帳號登入時自動取得管理者權限 |

### 平台共用 AI 金鑰與成員管理

AI 供應商的 API Key 由管理者在「AI 設定」統一設定，全站成員共用，成員不需要自備金鑰；「AI 餘額管理」提供平台總用量與依成員分列的用量統計，皆僅限管理者存取。管理者可在「用戶管理」頁面授予其他成員管理者角色。

### 分享連結與共編

在「我的流程」的分享視窗可以產生分享連結（檢視或共編權限、可設定有效期限、可隨時撤銷），把連結交給夥伴，對方登入後即可一起編輯同一條流程。

### 個人化 AI 記憶

AI 助手會記住每位使用者的偏好與習慣（各自獨立、互不可見），讓流程生成越用越貼近你的需求；在「帳戶設定 → AI 記憶」可以隨時檢視、修改或刪除。

> 在 [Google Cloud Console](https://console.cloud.google.com/apis/credentials) 建立 OAuth Client ID（Web application），Authorized JavaScript origins 填入你的網站來源即可，不需要 redirect URI。

### 作品庫（產出檔案儲存）

流程產生的 AI 影片、語音、圖片與文件會自動存入每位使用者專屬的作品庫，可在「作品庫」頁面預覽、下載與刪除。

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `ARTIFACT_STORAGE_PATH` | `./data/artifacts` | 作品檔案儲存路徑（Docker 部署時掛載 volume） |
| `ARTIFACT_MAX_FILE_SIZE_MB` | `512` | 單一檔案大小上限（MB） |

### 排程自動掛載

流程中若包含「排程觸發」節點，**發布流程時會自動註冊定時執行**，不需要再到排程頁手動建立；關閉瀏覽器後排程仍在伺服器端持續運行。

---

## 把作品掛上網域

想讓 AI 蓋的小站台有自己的網址？三步驟：

1. DNS 建立 wildcard 記錄：`*.sites.example.com → 主機 IP`
2. `.env` 設定 `SITE_MAIN_DOMAIN=n3n.example.com`、`SITE_BASE_DOMAIN=sites.example.com`
3. 啟動：`docker compose --profile domain up -d`（Caddy 會自動簽發 HTTPS 憑證）

之後每座站台都住在 `https://{slug}.sites.example.com/`。想用自己的網域？在站台抽屜輸入網域，照指示建立 TXT 與 CNAME 記錄後按「驗證」即可。

**上傳自己的應用**：不只靜態站台——把含 `docker-compose.yml` 或 `Dockerfile` 的專案打包成 zip 上傳到「小應用」，平台會在沙盒容器裡跑起來、掛上同一個 wildcard 子網域。功能預設關閉，啟用方式：`docker network create n3n-apps`，再以 `docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d` 啟動（詳見 `docs/ARCHITECTURE_ASSESSMENT.md`）。

---

## 進階資訊

如果你是開發者，想了解技術細節，請參考 [TECHNICAL.md](TECHNICAL.md)；模組地圖與「新程式碼放哪裡」的指南請見 [ARCHITECTURE.md](ARCHITECTURE.md)。

---

## 授權

Apache License 2.0 - 詳見 [LICENSE](LICENSE)
