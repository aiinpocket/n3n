# 貢獻指南

感謝您對 N3N Flow Platform 的興趣！我們歡迎所有形式的貢獻。

## 如何貢獻

### 回報 Issue

如果您發現 Bug 或有功能建議，請到 [GitHub Issues](https://github.com/aiinpocket/n3n/issues) 回報。

回報 Bug 時，請包含：
- 問題描述
- 重現步驟
- 預期行為
- 實際行為
- 環境資訊（OS、Java 版本、瀏覽器等）

### 提交 Pull Request

1. **Fork 專案**
   ```bash
   git clone https://github.com/YOUR_USERNAME/n3n.git
   cd n3n
   git remote add upstream https://github.com/aiinpocket/n3n.git
   ```

2. **建立功能分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **進行開發**
   - 遵循現有的程式碼風格
   - 確保程式碼通過測試和 lint 檢查
   - 撰寫或更新相關測試

4. **提交變更**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

   Commit 訊息格式：
   - `feat:` 新功能
   - `fix:` Bug 修復
   - `docs:` 文檔更新
   - `style:` 程式碼格式調整
   - `refactor:` 重構
   - `test:` 測試相關
   - `chore:` 維護性工作

5. **推送並建立 PR**
   ```bash
   git push origin feature/your-feature-name
   ```

   然後在 GitHub 上建立 Pull Request。

### 前置需求

- Java 21+
- Node.js 18+
- Docker 24.0+ & Docker Compose v2.20+

### 開發環境設置

請參考 [DEPLOYMENT.md](docs/DEPLOYMENT.md) 設置本地開發環境。

```bash
# 快速開始（一鍵啟動所有依賴服務）
docker compose up -d

# 後端編譯與啟動
./mvnw compile -Dfrontend.skip=true
./mvnw spring-boot:run -Dfrontend.skip=true

# 前端開發（另一個終端）
cd src/main/frontend
npm install
npm run dev
```

### 程式碼檢查

提交前請確保：

```bash
# 後端測試（跳過前端建置，加速執行）
./mvnw test -Dfrontend.skip=true

# 執行單一測試類別
./mvnw test -Dfrontend.skip=true -Dtest=FlowServiceTest

# 執行單一測試方法
./mvnw test -Dfrontend.skip=true -Dtest="FlowServiceTest#shouldCreateFlow"

# 前端 lint
cd src/main/frontend
npm run lint

# 整合建置（前端 + 後端）
./mvnw clean install
```

## 編碼規範

### 後端（Java / Spring Boot）

- 使用 Lombok 註解（`@Data`, `@Builder`, `@RequiredArgsConstructor`）
- Controller 回傳 DTO（`*Response`），不直接暴露 Entity
- 請求 DTO 命名：`Create*Request`、`Update*Request`
- 所有 ID 使用 `UUID`
- 軟刪除使用 `isDeleted` 欄位
- Service 讀取方法加上 `@Transactional(readOnly = true)`
- 測試基底類別：`BaseServiceTest`（Mockito）、`BaseRepositoryTest`（H2）、`BaseControllerTest`（MockMvc）

### 前端（React / TypeScript）

- 所有使用者可見文字必須使用 i18n（`t('key')`），不可寫死字串
- 三個語系檔案的 key 必須同步：`en.json`、`zh-TW.json`、`ja.json`
- 使用 CSS 自訂屬性（如 `var(--color-primary)`），不使用寫死的色碼
- 錯誤處理使用 `extractApiError()` 工具函式
- 使用 `logger` 工具取代 `console.*`
- 使用 `react-router-dom` 的 `navigate()`，不使用 `window.location`
- Table 元件需設定 `scroll={{ x }}` 確保水平捲動

## Branch 保護規則

- `main` 分支受保護，需要透過 Pull Request 合併
- PR 需要至少 1 位審核者批准
- 過期的審核將自動失效

## 授權

貢獻的程式碼將採用 [Apache License 2.0](LICENSE) 授權。

## 聯絡方式

如有任何問題，歡迎在 [Issues](https://github.com/aiinpocket/n3n/issues) 中提出。
