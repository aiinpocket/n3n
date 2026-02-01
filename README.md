# N3N Flow Platform

[English](README.en.md) | [日本語](README.ja.md) | 繁體中文

> 用說的就能建立自動化流程 - 讓 AI 幫你把想法變成可執行的工作流程

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

### 3. 開始使用

打開瀏覽器，前往：**http://localhost:8080**

首次使用會引導你：
1. 建立管理員帳號
2. 設定 AI 助手（選擇你有的 AI 服務）
3. 開始建立你的第一個流程！

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

---

## 功能特色

- **AI 流程生成** - 用自然語言描述，AI 幫你建立流程
- **視覺化編輯** - 拖拉式介面，直覺調整流程
- **即時監控** - 看到流程執行的每一步，節點狀態即時更新
- **Webhook 觸發** - 讓外部系統（如 GitHub、Slack）自動觸發流程
- **技能系統** - 內建常用自動化技能，無需額外設定
- **安全儲存** - 你的 API Key 和密碼都有 AES-256 加密保護
- **插件市集** - 瀏覽、安裝第三方整合插件，擴展平台功能
- **裝置管理** - 連接本地代理程式，讓流程控制你的電腦

---

## 插件市集

N3N 提供插件市集，讓你輕鬆擴展平台功能：

### 目前支援的插件類型

| 類型 | 說明 | 範例 |
|------|------|------|
| **AI 整合** | 連接各大 AI 服務 | OpenAI, Claude, Gemini |
| **通訊平台** | 發送訊息到聊天應用 | Slack, Discord, Line, Telegram |
| **資料處理** | 轉換、處理資料 | JSON 處理, 文字處理 |
| **雲端服務** | 連接雲端 API | Google Sheets, Notion, AWS S3 |

### 安裝插件

1. 進入「插件市集」頁面
2. 瀏覽或搜尋需要的插件
3. 點擊「安裝」
4. 設定對應的憑證（API Key 等）
5. 在流程編輯器中使用新的節點

---

## 本地代理程式 (Local Agent)

想讓流程控制你的電腦？安裝本地代理程式：

### 下載代理程式

| 作業系統 | 下載連結 | 說明 |
|---------|---------|------|
| Windows | [N3N Agent for Windows](#) | .NET 8 應用程式 |
| macOS | [N3N Agent for macOS](#) | Swift 應用程式 |

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

## 進階資訊

如果你是開發者，想了解技術細節，請參考 [TECHNICAL.md](TECHNICAL.md)。

---

## 授權

Apache License 2.0 - 詳見 [LICENSE](LICENSE)
