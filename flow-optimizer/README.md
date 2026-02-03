# Flow Optimizer Service

本地 AI 流程優化服務，使用 llamafile + Phi-3-Mini 模型分析工作流程並提供優化建議。

## 功能

- **依賴分析**：分析流程節點之間的依賴關係
- **並行識別**：識別可以並行執行的節點
- **優化建議**：提供流程優化建議（如合併請求、移除冗餘節點等）

## 技術規格

- **推理引擎**：[llamafile](https://github.com/Mozilla-Ocho/llamafile) by Mozilla
- **模型**：Phi-3-Mini-4K-Instruct Q4 (~2.3GB)
- **API**：OpenAI-compatible REST API

## 使用方式

### Docker Compose（推薦）

```bash
# 啟動包含 flow-optimizer 的完整服務
docker compose --profile with-optimizer up -d

# 或單獨啟動 flow-optimizer
docker compose up -d flow-optimizer
```

### 獨立運行

```bash
cd flow-optimizer
docker build -t n3n-flow-optimizer .
docker run -p 8081:8081 n3n-flow-optimizer
```

## API 端點

### Health Check
```bash
curl http://localhost:8081/health
```

### Chat Completion (OpenAI-compatible)
```bash
curl -X POST http://localhost:8081/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "phi-3-mini",
    "messages": [
      {"role": "user", "content": "Analyze this flow..."}
    ],
    "temperature": 0.7,
    "max_tokens": 1024
  }'
```

## 環境變數

| 變數 | 預設值 | 說明 |
|------|--------|------|
| LLAMAFILE_HOST | 0.0.0.0 | 監聽地址 |
| LLAMAFILE_PORT | 8081 | 監聽端口 |
| LLAMAFILE_THREADS | 4 | CPU 執行緒數 |
| LLAMAFILE_CTX_SIZE | 4096 | 上下文視窗大小 |

## 性能預估

在現代 CPU 上（Intel 12th+, AMD Zen3+, Apple M1+）：
- **生成速度**：20-40 tokens/sec
- **首次回應時間**：1-3 秒
- **記憶體使用**：~3-4 GB

## 注意事項

1. 首次啟動需要下載 ~2.3GB 模型，可能需要較長時間
2. 建議至少分配 4GB 記憶體給容器
3. 若在 ARM 架構上運行（如 Apple Silicon），llamafile 會自動優化
