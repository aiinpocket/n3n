# N3N 快速入門指南

[English](QUICK_START.en.md) | [日本語](QUICK_START.ja.md) | 繁體中文

> 3 分鐘啟動你的第一個 AI 自動化流程

---

## 前提條件

- 已安裝 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows/Mac）或 [Docker Engine](https://docs.docker.com/engine/install/)（Linux）
- 至少 4 GB 記憶體、10 GB 磁碟空間

確認 Docker 已安裝並啟動：
```bash
docker --version
# 應顯示 Docker version 24.x 或更新版本
```

---

## 第一步：下載 N3N

**方式 A：使用 Git**
```bash
git clone https://github.com/aiinpocket/n3n.git
cd n3n
```

**方式 B：下載 ZIP**

[下載 ZIP](https://github.com/aiinpocket/n3n/archive/refs/heads/main.zip) → 解壓 → 進入 `n3n` 資料夾

---

## 第二步：啟動服務

```bash
docker compose up -d
```

首次啟動需下載映像檔（約 2.3 GB），依網路速度需要 10-30 分鐘。
後續重啟僅需 60-90 秒。

**追蹤啟動進度：**
```bash
docker compose logs -f app
```

當看到 `Started N3nApplication` 時，表示服務已就緒。

---

## 第三步：開始使用

打開瀏覽器前往 **http://localhost:8080**

### 初始設定（僅首次）

1. **建立管理員帳號** — 填入名稱、Email、密碼
2. **備份 Recovery Key** — 系統顯示 12 個英文單詞，請務必複製保存
3. **驗證 Recovery Key** — 輸入 12 個單詞確認已備份
4. **設定 AI 助手**（選擇性）— 輸入你的 AI 服務 API Key
5. 開始建立流程！

> **重要**：Recovery Key 只顯示一次，遺失後無法恢復加密憑證。建議存放在密碼管理器中。

---

## 建立你的第一個流程

1. 點擊側邊欄的「流程」→「新增流程」
2. 使用以下任一方式：
   - **AI 生成**：點擊 AI 助手按鈕，用自然語言描述你想要的流程
   - **手動建立**：從左側面板拖拉節點到畫布上
3. 連接節點 → 設定參數 → 點擊「發佈」
4. 點擊「執行」測試你的流程

---

## 設定 AI 助手（選擇性）

進入「設定」→「AI 設定」，選擇你的 AI 服務：

| AI 服務 | 費用 | 申請連結 |
|--------|------|---------|
| **Claude** | 付費 | [console.anthropic.com](https://console.anthropic.com/) |
| **ChatGPT** | 付費 | [platform.openai.com](https://platform.openai.com/api-keys) |
| **Gemini** | 付費 | [aistudio.google.com](https://aistudio.google.com/apikey) |
| **Ollama** | 免費（本地） | [ollama.com](https://ollama.com/download) |

> 不想付費？選擇 Ollama 在本地運行 AI，完全免費！

---

## 常用操作

| 操作 | 指令 |
|------|------|
| 啟動服務 | `docker compose up -d` |
| 停止服務 | `docker compose down` |
| 查看狀態 | `docker compose ps` |
| 查看日誌 | `docker compose logs -f app` |
| 更新版本 | `git pull && docker compose down && docker compose up -d --build` |

---

## 故障排除

### Docker Desktop 未啟動
- **Windows/Mac**：開啟 Docker Desktop，等待系統匣圖示顯示「Running」
- **Linux**：`sudo systemctl start docker`

### 連接埠 8080 被佔用
```bash
N3N_PORT=9090 docker compose up -d
# 改用 http://localhost:9090
```

### 記憶體不足
在 `.env` 設定 `FLOW_OPTIMIZER_ENABLED=false` 可節省 2-4 GB 記憶體。

---

## 下一步

- [完整功能介紹](README.md) — 了解所有功能特色
- [部署指南](docs/DEPLOYMENT.md) — 生產環境部署
- [技術文檔](TECHNICAL.md) — API 參考與架構說明
- [貢獻指南](CONTRIBUTING.md) — 參與開發
