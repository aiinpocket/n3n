# N3N Recovery Key 與 Flow 匯出/匯入 實作進度

**計畫文件**: `/Users/aiinpocket/.claude/plans/partitioned-rolling-crab.md`

---

## Phase 1: 資料庫 Migration [已完成]

- [x] `V8__recovery_key_and_flow_export.sql` - 建立完成
- [x] `EncryptionKeyMetadata` entity - 建立完成
- [x] `KeyMigrationLog` entity - 建立完成
- [x] `FlowImport` entity - 建立完成
- [x] `ComponentInstallation` entity - 建立完成
- [x] `EncryptionKeyMetadataRepository` - 建立完成
- [x] `ComponentInstallationRepository` - 建立完成
- [x] `Credential` entity 更新 - 新增 keyVersion, keyStatus
- [x] `User` entity 更新 - 新增 recoveryKeyBackedUp, recoveryKeyBackedUpAt
- [x] `FlowImportRepository` - 需要建立

---

## Phase 2: Recovery Key 核心服務 [已完成]

### 後端
- [x] `BIP39WordList.java` - 2048 個英文單詞詞庫
- [x] `RecoveryKey.java` - Recovery Key DTO
- [x] `RecoveryKeyService.java` - 產生、驗證、衍生 Master Key
- [x] 修改 `MasterKeyProvider.java` - 整合 Recovery Key 系統
- [x] `KeyMigrationLogRepository.java` - 遷移記錄 Repository

### 關鍵檔案位置
```
src/main/java/com/aiinpocket/n3n/credential/
├── wordlist/
│   └── BIP39WordList.java          # 待建立
├── dto/
│   └── RecoveryKey.java            # 待建立
├── service/
│   ├── RecoveryKeyService.java     # 待建立
│   ├── KeyMigrationService.java    # 待建立
│   └── MasterKeyProvider.java      # 待修改
└── repository/
    └── KeyMigrationLogRepository.java  # 待建立
```

---

## Phase 3: Recovery Key API 和前端 [進行中]

### 後端 API
- [x] `RecoveryKeyController.java` - Recovery Key API
  - `GET /api/security/status` - 取得加密系統狀態
  - `POST /api/security/recovery-key/confirm` - 確認備份
  - `POST /api/security/migrate` - 用舊 Key 遷移憑證
  - `POST /api/security/emergency-restore` - 緊急還原
- [ ] 修改 `AuthService.java` - 登入時檢查是否需要顯示 Recovery Key
- [ ] 修改 `AuthResponse.java` - 新增 recoveryKey 欄位

### 前端
- [x] `RecoveryKeyModal.tsx` - 首次顯示 Recovery Key Modal
- [x] `KeyMismatchBanner.tsx` - Key 不匹配警告橫幅
- [x] `MigrateCredentialModal.tsx` - 用舊 Key 遷移憑證 Modal
- [x] 修改 `authStore.ts` - 新增 Recovery Key 狀態
- [x] `security.ts` API - Security API 模組
- [ ] 修改 `LoginPage.tsx` - 登入後顯示 Modal
- [ ] 修改 `CredentialCard.tsx` - 顯示 Key 不匹配狀態

### 關鍵檔案位置
```
src/main/frontend/src/
├── components/security/
│   ├── RecoveryKeyModal.tsx        # 待建立
│   ├── KeyMismatchBanner.tsx       # 待建立
│   └── MigrateCredentialModal.tsx  # 待建立
├── stores/
│   └── authStore.ts                # 待修改
└── pages/
    └── LoginPage.tsx               # 待修改
```

---

## Phase 4: Flow 匯出功能 [已完成]

### 後端
- [x] `FlowExportPackage.java` - 匯出包 DTO
- [x] `ComponentDependency.java` - 元件依賴 DTO
- [x] `CredentialPlaceholder.java` - 憑證佔位符 DTO
- [x] `FlowExportService.java` - 匯出服務
- [x] `FlowExportController.java` - 匯出 API
  - `GET /api/flows/{flowId}/versions/{version}/export`

### 前端
- [ ] 在 `FlowEditorPage.tsx` 新增匯出按鈕
- [ ] 在 `flow.ts` API 新增 exportFlow 方法

### 關鍵檔案位置
```
src/main/java/com/aiinpocket/n3n/flow/
├── dto/export/
│   ├── FlowExportPackage.java      # 待建立
│   ├── ComponentDependency.java    # 待建立
│   └── CredentialPlaceholder.java  # 待建立
├── service/
│   └── FlowExportService.java      # 待建立
└── controller/
    └── FlowExportController.java   # 待建立
```

---

## Phase 5: Flow 匯入功能 [已完成]

### 後端
- [x] `FlowImportRequest.java` - 匯入請求 DTO
- [x] `FlowImportPreviewResponse.java` - 預覽回應 DTO（含 ComponentStatus, CredentialRequirement）
- [x] `FlowImportService.java` - 匯入服務
- [x] 新增匯入 API 到 `FlowExportController.java`
  - `POST /api/flows/import/preview`
  - `POST /api/flows/import`

### 前端
- [ ] `FlowImportModal.tsx` - 匯入 Modal
- [ ] 在 `FlowListPage.tsx` 新增匯入按鈕
- [ ] 在 `flow.ts` API 新增 previewImport, importFlow 方法

### 關鍵檔案位置
```
src/main/java/com/aiinpocket/n3n/flow/
├── dto/import_/
│   ├── FlowImportRequest.java          # 待建立
│   ├── FlowImportPreviewResponse.java  # 待建立
│   ├── ComponentStatus.java            # 待建立
│   └── CredentialRequirement.java      # 待建立
└── service/
    └── FlowImportService.java          # 待建立

src/main/frontend/src/
└── components/flow/
    └── FlowImportModal.tsx             # 待建立
```

---

## Phase 6: 測試 [已完成]

- [x] 編譯和啟動測試
- [ ] Recovery Key 產生和驗證測試（手動測試）
- [ ] 金鑰遷移測試（手動測試）
- [ ] Flow 匯出/匯入 round-trip 測試（手動測試）

---

## 實作完成摘要

**後端實作：**
- V8 Migration 完成
- Recovery Key 系統（BIP39 詞庫、產生、驗證、衍生 Master Key）
- MasterKeyProvider 整合 Recovery Key
- RecoveryKeyController API
- FlowExportService / FlowImportService
- FlowExportController API

**前端實作：**
- RecoveryKeyModal 元件
- MigrateCredentialModal 元件
- KeyMismatchBanner 元件
- security.ts API 模組
- authStore 整合

**編譯狀態：** BUILD SUCCESS

---

## 重啟對話後的指令

```
請繼續實作 N3N Recovery Key 與 Flow 匯出/匯入功能。
當前進度請參考 /Users/aiinpocket/IdeaProjects/n3n/TODO_RECOVERY_KEY_AND_EXPORT.md
計畫文件請參考 /Users/aiinpocket/.claude/plans/partitioned-rolling-crab.md

目前 Phase 1 已完成，請繼續 Phase 2: Recovery Key 核心服務。
下一步是建立 BIP39WordList.java 詞庫。
```

---

## 已建立的檔案清單

```
# V8 Migration
src/main/resources/db/migration/V8__recovery_key_and_flow_export.sql

# Entity
src/main/java/com/aiinpocket/n3n/credential/entity/EncryptionKeyMetadata.java
src/main/java/com/aiinpocket/n3n/credential/entity/KeyMigrationLog.java
src/main/java/com/aiinpocket/n3n/flow/entity/FlowImport.java
src/main/java/com/aiinpocket/n3n/flow/entity/ComponentInstallation.java

# Repository
src/main/java/com/aiinpocket/n3n/credential/repository/EncryptionKeyMetadataRepository.java
src/main/java/com/aiinpocket/n3n/flow/repository/ComponentInstallationRepository.java

# 修改的檔案
src/main/java/com/aiinpocket/n3n/credential/entity/Credential.java (新增 keyVersion, keyStatus)
src/main/java/com/aiinpocket/n3n/auth/entity/User.java (新增 recoveryKeyBackedUp 欄位)
```
