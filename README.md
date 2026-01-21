# N3N Flow Platform

> 可視化流程編排平台 - 零侵入式外部服務整合與安全認證管理

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 目錄

- [功能特色](#功能特色)
- [系統架構](#系統架構)
- [快速開始](#快速開始)
- [安全架構](#安全架構)
- [外部服務整合](#外部服務整合)
- [認證管理](#認證管理)
- [流程分享](#流程分享)
- [API 文檔](#api-文檔)
- [開發指南](#開發指南)

---

## 功能特色

### 核心功能

| 功能 | 說明 |
|------|------|
| **可視化流程編輯** | 拖拉式介面設計工作流程，支援條件、迴圈、並行執行 |
| **零侵入服務整合** | 只需提供服務地址，自動探測 OpenAPI/Swagger 文檔 |
| **安全認證管理** | AES-256 加密儲存 Token、密碼、API Key 等機敏資訊 |
| **流程分享協作** | 與團隊成員分享流程，支援 view/edit/admin 權限 |
| **即時執行監控** | WebSocket 即時推送執行狀態，視覺化追蹤流程進度 |
| **多協議支援** | REST、GraphQL、gRPC（規劃中） |

### 安全特性

- **Envelope Encryption** - 雙層加密架構，支援 Key 輪換
- **敏感資料遮罩** - Log 自動遮罩 Token、密碼等欄位
- **用戶隔離** - 認證資訊依用戶/工作區隔離
- **權限控制** - 細粒度的流程分享權限管理

---

## 系統架構

```
┌─────────────────────────────────────────────────────────────────────┐
│                         N3N Flow Platform                            │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                      Frontend (React)                          │  │
│  │  流程編輯器 │ 認證管理 │ 服務管理 │ 執行監控                    │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │ HTTP/WebSocket                        │
│  ┌───────────────────────────┴───────────────────────────────────┐  │
│  │                    Backend (Spring Boot)                       │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐  │  │
│  │  │ Flow Engine │ │  Security   │ │   External Service      │  │  │
│  │  │ DAG Parser  │ │ Encryption  │ │   Auto Discovery        │  │  │
│  │  │ Scheduler   │ │ Masking     │ │   Protocol Adapter      │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│         ┌────────────────────┼────────────────────┐                 │
│         ▼                    ▼                    ▼                  │
│  ┌────────────┐      ┌────────────┐      ┌────────────────┐        │
│  │ PostgreSQL │      │   Redis    │      │ External APIs  │        │
│  │ 流程定義    │      │ 執行狀態   │      │ (自動探測)     │        │
│  │ 認證資訊    │      │ 資料快取   │      │                │        │
│  └────────────┘      └────────────┘      └────────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 快速開始

### 前置需求

- Java 21+
- Node.js 18+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7.0+

### 安裝步驟

```bash
# 1. Clone 專案
git clone https://github.com/your-org/n3n.git
cd n3n

# 2. 啟動資料庫服務
docker compose up -d

# 3. 設定環境變數（生產環境必須）
export N3N_MASTER_KEY="your-32-byte-base64-encoded-key"

# 4. 編譯並啟動
./mvnw spring-boot:run

# 5. 開啟瀏覽器
open http://localhost:8080
```

### 開發模式（Hot Reload）

```bash
# Terminal 1: 啟動後端
./mvnw spring-boot:run

# Terminal 2: 啟動前端開發伺服器
cd src/main/frontend
npm install
npm run dev

# 前端開發伺服器: http://localhost:3000
# 後端 API: http://localhost:8080
```

---

## 安全架構

### Envelope Encryption（信封加密）

```
┌─────────────────────────────────────────────────────────┐
│                    Encryption Flow                       │
│                                                          │
│   Master Key (環境變數)                                  │
│        │                                                 │
│        └──► 加密 ──► Data Encryption Key (DEK)          │
│                            │                             │
│                            └──► 加密 ──► 敏感資料        │
│                                         (Token, 密碼)    │
└─────────────────────────────────────────────────────────┘
```

**優點：**
- Master Key 永不直接接觸資料
- 支援 Key 輪換而不需重新加密所有資料
- DEK 可依工作區隔離

### 設定 Master Key

```bash
# 方法 1: 環境變數（推薦）
export N3N_MASTER_KEY="nJsaWEwTysEqcg/pAbCD32u8emt/KkJSeBZWdh7NGos="

# 方法 2: 設定檔
# application.properties
app.master-key=${N3N_MASTER_KEY}

# 方法 3: Key 檔案
app.master-key-file=/etc/n3n/master.key
```

**生成新的 Master Key：**

```java
String key = MasterKeyProvider.generateMasterKeyString();
// 輸出: nJsaWEwTysEqcg/pAbCD32u8emt/KkJSeBZWdh7NGos=
```

### 敏感資料遮罩

系統自動在 Log 中遮罩以下欄位：

| 欄位類型 | 範例 |
|---------|------|
| 密碼 | password, passwd, pwd |
| Token | token, accessToken, bearerToken |
| API Key | apiKey, api_key, secret |
| 認證 | authorization, credential |
| 資料庫 | connectionString, dbPassword |

**Log 輸出範例：**
```
原始: {"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}
遮罩: {"token": "ey****9..."}
```

---

## 外部服務整合

### 零侵入式服務發現

只需提供服務地址，平台自動探測 API 結構：

```bash
# 新增外部服務
POST /api/services
{
  "name": "user-service",
  "displayName": "用戶服務",
  "baseUrl": "http://user-service:8080",
  "schemaUrl": "/v3/api-docs"    # OpenAPI 文檔路徑
}
```

**支援的探測方式：**
- OpenAPI 3.0 / Swagger 2.0
- 手動定義端點（無 API 文檔時）

### 服務認證

外部服務可關聯已儲存的認證：

```bash
# 更新服務認證
PUT /api/services/{id}
{
  "credentialId": "uuid-of-credential"
}
```

執行流程時，平台自動：
1. 解密認證資訊
2. 注入到 HTTP Request Header
3. 呼叫外部服務

---

## 認證管理

### 支援的認證類型

| 類型 | 說明 | 使用場景 |
|------|------|---------|
| `http_basic` | 帳號密碼 | Basic Auth API |
| `http_bearer` | Bearer Token | JWT API |
| `api_key` | API Key | 第三方服務 |
| `oauth2` | OAuth2 | Google, GitHub 等 |
| `database` | 資料庫連線 | MySQL, PostgreSQL |
| `ssh` | SSH Key | 遠端伺服器 |

### API 範例

```bash
# 建立認證
POST /api/credentials
{
  "name": "Production API Key",
  "type": "api_key",
  "visibility": "private",
  "data": {
    "key": "sk-xxxxx",
    "header": "X-API-Key"
  }
}

# 列出認證
GET /api/credentials

# 刪除認證
DELETE /api/credentials/{id}
```

---

## 流程分享

### 權限等級

| 權限 | 檢視 | 編輯 | 管理分享 |
|------|:----:|:----:|:-------:|
| view | ✅ | ❌ | ❌ |
| edit | ✅ | ✅ | ❌ |
| admin | ✅ | ✅ | ✅ |

### API 範例

```bash
# 分享流程
POST /api/flows/{flowId}/shares
{
  "email": "colleague@company.com",
  "permission": "edit"
}

# 查看分享清單
GET /api/flows/{flowId}/shares

# 更新權限
PUT /api/flows/{flowId}/shares/{shareId}?permission=admin

# 移除分享
DELETE /api/flows/{flowId}/shares/{shareId}

# 查看被分享給我的流程
GET /api/flows/shared-with-me
```

---

## API 文檔

### 主要端點

| Method | Path | 說明 |
|--------|------|------|
| **流程管理** |
| GET | /api/flows | 列出流程 |
| POST | /api/flows | 建立流程 |
| GET | /api/flows/{id} | 取得流程 |
| PUT | /api/flows/{id} | 更新流程 |
| DELETE | /api/flows/{id} | 刪除流程 |
| **流程版本** |
| GET | /api/flows/{id}/versions | 列出版本 |
| POST | /api/flows/{id}/versions | 儲存版本 |
| POST | /api/flows/{id}/versions/{v}/publish | 發布版本 |
| **流程分享** |
| GET | /api/flows/{id}/shares | 取得分享清單 |
| POST | /api/flows/{id}/shares | 分享流程 |
| DELETE | /api/flows/{id}/shares/{shareId} | 移除分享 |
| **外部服務** |
| GET | /api/services | 列出服務 |
| POST | /api/services | 新增服務 |
| GET | /api/services/{id} | 取得服務詳情 |
| POST | /api/services/{id}/parse | 解析 OpenAPI |
| **認證管理** |
| GET | /api/credentials | 列出認證 |
| POST | /api/credentials | 建立認證 |
| DELETE | /api/credentials/{id} | 刪除認證 |
| GET | /api/credentials/types | 列出認證類型 |
| **執行** |
| POST | /api/flows/{id}/trigger | 觸發執行 |
| GET | /api/executions | 列出執行記錄 |
| GET | /api/executions/{id} | 取得執行詳情 |
| WS | /ws/executions/{id} | 即時執行狀態 |

---

## 開發指南

### 專案結構

```
n3n/
├── src/main/
│   ├── java/com/aiinpocket/n3n/
│   │   ├── auth/              # 身份認證
│   │   ├── credential/        # 認證管理（加密）
│   │   │   ├── entity/
│   │   │   ├── service/
│   │   │   │   ├── MasterKeyProvider.java
│   │   │   │   ├── EnvelopeEncryptionService.java
│   │   │   │   └── SensitiveDataMasker.java
│   │   │   └── controller/
│   │   ├── flow/              # 流程管理
│   │   │   ├── entity/
│   │   │   │   ├── Flow.java
│   │   │   │   └── FlowShare.java
│   │   │   └── service/
│   │   ├── service/           # 外部服務管理
│   │   │   ├── entity/
│   │   │   │   ├── ExternalService.java
│   │   │   │   └── ServiceEndpoint.java
│   │   │   └── service/
│   │   ├── execution/         # 執行引擎
│   │   └── workspace/         # 工作區管理
│   ├── resources/
│   │   ├── application.properties
│   │   └── db/migration/      # Flyway 遷移
│   └── frontend/              # React 前端
│       └── src/
│           ├── pages/
│           ├── components/
│           ├── stores/
│           └── api/
└── pom.xml
```

### 執行測試

```bash
# 後端測試
./mvnw test

# 前端測試
cd src/main/frontend
npm run lint
npm run build

# 整合測試
./mvnw clean install
```

### 建立 Native Image

```bash
# 需要 GraalVM 21+
./mvnw native:compile -Pnative
```

---

## 環境變數

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `N3N_MASTER_KEY` | Master Key（生產必須） | auto-generated |
| `SPRING_DATASOURCE_URL` | PostgreSQL URL | jdbc:postgresql://localhost:5432/n3n |
| `SPRING_DATASOURCE_USERNAME` | 資料庫帳號 | n3n |
| `SPRING_DATASOURCE_PASSWORD` | 資料庫密碼 | n3n |
| `SPRING_DATA_REDIS_HOST` | Redis Host | localhost |
| `SPRING_DATA_REDIS_PORT` | Redis Port | 6379 |

---

## License

MIT License - 詳見 [LICENSE](LICENSE)

---

## 貢獻

歡迎提交 Issue 和 Pull Request！

1. Fork 此專案
2. 建立功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交變更 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 開啟 Pull Request
