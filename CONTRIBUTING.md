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

### 開發環境設置

請參考 [DEPLOYMENT.md](docs/DEPLOYMENT.md) 設置本地開發環境。

```bash
# 快速開始
docker compose up -d
./mvnw spring-boot:run

# 前端開發（另一個終端）
cd src/main/frontend
npm install
npm run dev
```

### 程式碼檢查

提交前請確保：

```bash
# 後端測試
./mvnw test

# 前端 lint
cd src/main/frontend
npm run lint

# 整合建置
./mvnw clean install
```

## Branch 保護規則

- `main` 分支受保護，需要透過 Pull Request 合併
- PR 需要至少 1 位審核者批准
- 過期的審核將自動失效

## 授權

貢獻的程式碼將採用 [Apache License 2.0](LICENSE) 授權。

## 聯絡方式

如有任何問題，歡迎在 [Issues](https://github.com/aiinpocket/n3n/issues) 中提出。
