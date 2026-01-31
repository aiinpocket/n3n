# N3N Flow Platform

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
- **即時監控** - 看到流程執行的每一步
- **安全儲存** - 你的 API Key 和密碼都有加密保護

---

## 進階資訊

如果你是開發者，想了解技術細節，請參考 [TECHNICAL.md](TECHNICAL.md)。

---

## 授權

Apache License 2.0 - 詳見 [LICENSE](LICENSE)
