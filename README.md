# N3N Flow Platform

[English](README.en.md) | [日本語](README.ja.md) | 繁體中文

> 用說的就能建立自動化流程 - 讓 AI 幫你把想法變成可執行的工作流程

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

### 進階需求（本地 AI 優化器）

| 項目 | 規格 |
|------|------|
| **CPU** | 8 核心以上 |
| **記憶體** | 16 GB 以上（建議 32 GB）|
| **硬碟** | 50 GB SSD |
| **GPU** | 選配：NVIDIA GPU 8GB+ VRAM 可加速推理 |

> 適合：使用本地 AI 流程優化器、高負載/多流程並行、企業部署

### 內建服務資源佔用

| 服務 | 記憶體佔用 | 說明 |
|------|-----------|------|
| **N3N App** | ~512 MB | Spring Boot 主應用程式 |
| **PostgreSQL** | ~256 MB | 關聯式資料庫 |
| **Redis** | ~128 MB | 快取與執行狀態 |
| **MongoDB** | ~256 MB | NoSQL 資料庫（工作流程節點使用） |
| **Flow Optimizer** | ~2-4 GB | 本地 LLM（選配） |

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

打開終端機（Terminal），執行以下指令：

```bash
# 下載專案
git clone https://github.com/aiinpocket/n3n.git
cd n3n

# 啟動服務（首次需要等待幾分鐘）
docker compose up -d
```

> **零配置啟動**：N3N 採用開箱即用設計，你不需要手動設定任何東西：
> - 資料庫（PostgreSQL / Redis / MongoDB）自動啟動並連接
> - JWT 簽名密鑰在首次啟動時自動隨機產生
> - 資料加密主金鑰自動產生並安全保存
> - 無需設定任何環境變數即可運行

### 3. 開始使用

打開瀏覽器，前往：**http://localhost:8080**

首次使用會引導你：
1. **建立管理員帳號** — 填入名稱、Email 和密碼
2. **備份 Recovery Key** — 系統會顯示 12 個英文單詞，這是你恢復加密資料的唯一方式。請務必抄寫或複製保存在安全的地方
3. **驗證 Recovery Key** — 輸入剛才的 12 個單詞以確認你已備份
4. 設定 AI 助手（選擇你有的 AI 服務）
5. 開始建立你的第一個流程！

> **重要**：Recovery Key 只會在首次設定時顯示一次。如果遺失，將無法恢復加密的憑證資料。
>
> **備份建議**：將 Recovery Key 存放在密碼管理器（如 1Password、Bitwarden）中，或抄寫在紙上存放於安全處。請不要以截圖或未加密的文字檔保存。

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

### 啟動失敗怎麼辦？

確認 Docker 正在運行，然後重試：
```bash
docker compose down
docker compose up -d
```

### 連接埠被佔用怎麼辦？

如果看到 `port 8080 is already in use` 的錯誤，表示其他程式正在使用 8080 連接埠。你可以：
```bash
# 使用其他連接埠啟動（例如改用 9090）
N3N_PORT=9090 docker compose up -d
# 然後用 http://localhost:9090 開啟
```

### 如何停止服務？

```bash
docker compose down
```

### 如何更新到最新版本？

```bash
git pull
docker compose down
docker compose up -d --build
```

### 如何存取資料庫（開發用）？

內部服務（PostgreSQL、Redis、MongoDB）預設不對外暴露端口。如需直接存取：

```bash
# 方法一：使用 Docker exec
docker compose exec postgres psql -U n3n

# 方法二：使用開發模式覆蓋（暴露所有端口）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

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
- **自訂 Docker 工具** - 從 Docker Hub 拉取工具容器，自動註冊為流程節點，支援精選推薦與社群評分
- **審批待辦儀表板** - 獨立頁面集中管理所有待審批項目，一鍵核准或駁回
- **系統清理管理** - 管理員可查看統計、手動觸發清理過期執行記錄
- **裝置管理** - 連接本地代理程式，讓流程控制你的電腦
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
- **閘道配對管理** - 產生配對碼連接本地代理，WebSocket 即時通訊管理

### 錯誤處理路由

N3N 支援三種連線類型，讓你清楚區分正常流程與錯誤處理：

| 連線類型 | 顏色 | 說明 |
|---------|------|------|
| **成功路徑** | 🟢 綠色 | 節點執行成功後走這條路線 |
| **錯誤路徑** | 🔴 紅色虛線 | 節點執行失敗時走這條路線 |
| **總是執行** | 🔵 藍色 | 無論成功或失敗都會執行 |

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

## 本地代理程式 (Local Agent)

想讓流程控制你的電腦？安裝本地代理程式：

### 下載代理程式

| 作業系統 | 下載連結 | 說明 |
|---------|---------|------|
| Windows | [GitHub Release](https://github.com/aiinpocket/n3n/releases) | .NET 8 自包含執行檔 |
| macOS | [GitHub Release](https://github.com/aiinpocket/n3n/releases) | Swift 應用程式（Apple Silicon） |

### 代理程式功能

- **檔案操作** - 讀取、寫入、複製、移動檔案
- **剪貼簿** - 讀取和設定剪貼簿內容
- **桌面通知** - 顯示系統通知
- **應用程式啟動** - 開啟本地應用程式
- **螢幕截圖** - 擷取螢幕畫面

### 配對流程

1. 在 N3N 網頁介面進入「裝置管理」
2. 點擊「新增裝置」，取得 6 位數配對碼
3. 在代理程式輸入配對碼
4. 配對成功後，即可在流程中使用本地節點

### 安全機制

- **X25519 ECDH** - 端對端加密金鑰交換
- **AES-256-GCM** - 所有指令都加密傳輸
- **配對碼驗證** - 確保只有你能配對裝置
- **憑證安全儲存** - Windows 使用 Credential Manager，macOS 使用 Keychain

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

### 安全設定

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `JWT_SECRET` | 自動產生 | JWT 簽名密鑰（叢集部署時需統一設定） |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | CORS 允許來源 |

### 容器編排（插件系統）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `ORCHESTRATOR_TYPE` | `docker` | 容器編排引擎（`docker` / `kubernetes` / `auto`） |
| `K8S_NAMESPACE` | `n3n` | Kubernetes 主命名空間 |
| `K8S_PLUGIN_NAMESPACE` | `n3n-plugins` | Kubernetes 插件命名空間 |
| `K8S_SERVICE_ACCOUNT` | `n3n-plugin-manager` | Kubernetes 服務帳號 |

> **auto 模式**：系統啟動時自動偵測環境（K8s Service Account → Docker Socket → Docker CLI），選擇對應的容器編排引擎。

### AI 流程優化器（預設啟用）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `FLOW_OPTIMIZER_ENABLED` | `true` | 本地 AI 優化器（預設啟用） |
| `FLOW_OPTIMIZER_URL` | `http://flow-optimizer:8081` | 優化器服務位址 |

本地 AI 優化器隨 `docker compose up -d` 自動啟動，無需額外設定或 API 金鑰。

> **注意**：本地 AI 優化器在本機執行，首次啟動時需要下載模型（約 2.3GB），且需要至少 4GB 記憶體。

---

## 進階資訊

如果你是開發者，想了解技術細節，請參考 [TECHNICAL.md](TECHNICAL.md)。

---

## 授權

Apache License 2.0 - 詳見 [LICENSE](LICENSE)
