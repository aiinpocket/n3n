# Local Agent 安全通訊設計

## 設計目標

1. **端到端加密** - 即使 HTTPS 被中間人攻擊，訊息仍然安全
2. **雙向驗證** - 平台驗證 Agent，Agent 也驗證平台
3. **防重放攻擊** - 時間戳 + 序列號
4. **遠端控制** - 可指定 IP 連接遠端 Agent
5. **操作簡單** - 一次配對，永久使用

---

## 架構概覽

```
┌─────────────────────────────────────────────────────────────────┐
│  N3N Platform                                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Security Layer                                            │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐          │  │
│  │  │  Pairing   │  │  KeyStore  │  │  Crypto    │          │  │
│  │  │  Service   │  │  (per device)│ │  Service   │          │  │
│  │  └────────────┘  └────────────┘  └────────────┘          │  │
│  └──────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────┘
                              │ Encrypted Messages (JWE-like)
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│ Agent (家)    │     │ Agent (公司)  │     │ Agent (遠端)  │
│ 192.168.1.x   │     │ 10.0.0.x      │     │ 203.0.113.x   │
│               │     │               │     │               │
│ ┌───────────┐ │     │ ┌───────────┐ │     │ ┌───────────┐ │
│ │ KeyStore  │ │     │ │ KeyStore  │ │     │ │ KeyStore  │ │
│ │ (Keychain)│ │     │ │ (Keychain)│ │     │ │ (Keychain)│ │
│ └───────────┘ │     │ └───────────┘ │     │ └───────────┘ │
└───────────────┘     └───────────────┘     └───────────────┘
```

---

## 配對流程 (Pairing)

### Step 1: 生成配對碼（平台）

```
使用者                    N3N Platform
  │                           │
  │─── 點擊「新增設備」──────▶│
  │                           │
  │                           │ 生成:
  │                           │ - pairingCode: 6位數字
  │                           │ - pairingSecret: 32 bytes (臨時)
  │                           │ - 有效期: 5 分鐘
  │                           │
  │◀── 顯示配對碼 ────────────│
  │    "123456"               │
```

### Step 2: 輸入配對碼（Agent）

```
使用者                    Local Agent               N3N Platform
  │                           │                          │
  │─── 輸入配對碼 ───────────▶│                          │
  │    "123456"               │                          │
  │                           │                          │
  │                           │ 生成:                    │
  │                           │ - deviceId: UUID         │
  │                           │ - deviceKeyPair: X25519  │
  │                           │                          │
  │                           │─── POST /api/agent/pair ─▶│
  │                           │    {                      │
  │                           │      pairingCode,         │
  │                           │      deviceId,            │
  │                           │      devicePublicKey,     │
  │                           │      deviceFingerprint    │
  │                           │    }                      │
  │                           │                          │
  │                           │                          │ 驗證配對碼
  │                           │                          │ 生成 platformKeyPair
  │                           │                          │ 計算 sharedSecret
  │                           │                          │ 派生 masterKey
  │                           │                          │
  │                           │◀── 200 OK ───────────────│
  │                           │    {                      │
  │                           │      platformPublicKey,   │
  │                           │      deviceToken,         │
  │                           │      platformFingerprint  │
  │                           │    }                      │
  │                           │                          │
  │                           │ 計算 sharedSecret        │
  │                           │ 派生 masterKey           │
  │                           │ 儲存到 Keychain          │
  │                           │                          │
  │◀── 配對成功 ──────────────│                          │
```

### 密鑰派生 (HKDF)

```
sharedSecret = X25519(devicePrivateKey, platformPublicKey)
             = X25519(platformPrivateKey, devicePublicKey)

masterKey = HKDF-SHA256(
    ikm = sharedSecret,
    salt = deviceId || platformId,
    info = "n3n-agent-v1"
)

// 從 masterKey 派生各種密鑰
encryptKeyC2S = HKDF(masterKey, info="encrypt-c2s")  // Client → Server
encryptKeyS2C = HKDF(masterKey, info="encrypt-s2c")  // Server → Client
authKey = HKDF(masterKey, info="auth")               // 認證用
```

---

## 加密訊息格式

### 結構（類似 JWE Compact）

```
header.ciphertext.tag
```

### Header (Base64URL encoded JSON)

```json
{
  "v": 1,                           // 協議版本
  "alg": "A256GCM",                 // 加密演算法
  "did": "device-uuid",             // 設備 ID
  "ts": 1704067200000,              // 時間戳 (ms)
  "seq": 12345,                     // 序列號
  "nonce": "base64-encoded-nonce"   // 12 bytes nonce
}
```

### Payload（加密前）

```json
{
  "type": "req",
  "id": "request-uuid",
  "method": "node.invoke",
  "params": {
    "capability": "screen.capture",
    "args": {}
  }
}
```

### 加密過程

```
1. 序列化 payload 為 JSON bytes
2. 生成隨機 12-byte nonce
3. 使用 AES-256-GCM 加密:
   - key: encryptKey (方向決定用哪個)
   - nonce: 隨機生成
   - aad: header JSON bytes (附加認證資料)
   - plaintext: payload bytes
4. 輸出: ciphertext + 16-byte tag
5. 組合: base64url(header) + "." + base64url(ciphertext) + "." + base64url(tag)
```

---

## 驗證流程

### 接收訊息時的驗證步驟

```java
public DecryptedMessage verifyAndDecrypt(String encryptedMessage) {
    // 1. 解析 header
    String[] parts = encryptedMessage.split("\\.");
    Header header = parseHeader(parts[0]);

    // 2. 驗證設備 ID
    DeviceKey deviceKey = keyStore.getKey(header.deviceId);
    if (deviceKey == null) {
        throw new UnknownDeviceException();
    }

    // 3. 驗證時間戳（±5分鐘）
    long now = System.currentTimeMillis();
    if (Math.abs(now - header.timestamp) > 5 * 60 * 1000) {
        throw new MessageExpiredException();
    }

    // 4. 驗證序列號（防重放）
    if (header.sequence <= deviceKey.getLastSequence()) {
        throw new ReplayAttackException();
    }

    // 5. 解密並驗證 tag
    byte[] plaintext = decrypt(
        deviceKey.getEncryptKey(),
        header.nonce,
        parts[0].getBytes(),  // AAD
        base64Decode(parts[1]),  // ciphertext
        base64Decode(parts[2])   // tag
    );

    // 6. 更新序列號
    deviceKey.setLastSequence(header.sequence);
    keyStore.save(deviceKey);

    return parsePayload(plaintext);
}
```

---

## 連線模式

### 模式 1: Agent 主動連線（推薦）

```
Agent ────── WebSocket (wss://platform/ws/agent) ──────▶ Platform

優點:
- 不需要 Agent 開放 port
- 穿透 NAT/防火牆
- 適合家用網路
```

### 模式 2: 平台主動連線（遠端控制）

```
Platform ────── HTTPS (https://agent-ip:port/api) ──────▶ Agent

使用場景:
- Agent 有固定 IP
- 企業內網環境
- 需要低延遲

設定方式:
1. Agent 設定「允許外部連線」
2. Agent 設定監聽 Port (預設 9999)
3. 平台記錄 Agent 的 IP:Port
4. 平台發起連線時使用相同的加密機制
```

### Agent 端點配置

```json
{
  "deviceId": "uuid",
  "name": "我的 MacBook",
  "connectionMode": "bidirectional",  // passive | active | bidirectional
  "listenAddress": "0.0.0.0:9999",    // 監聽位址
  "externalAddress": "203.0.113.50:9999",  // 外部位址 (可選)
  "platformUrl": "wss://n3n.example.com/ws/agent"
}
```

---

## 安全防護措施

### 1. 防重放攻擊

```
- 時間戳：±5 分鐘容差
- 序列號：遞增，記錄最後使用的序列號
- Nonce：每次隨機生成，永不重複
```

### 2. 防中間人攻擊

```
- 配對時交換公鑰，使用 X25519 ECDH
- 後續通訊使用共享密鑰加密
- 平台指紋驗證（首次配對時顯示給使用者確認）
```

### 3. 密鑰保護

```
macOS: Keychain (Secure Enclave 如果可用)
Windows: Credential Manager / DPAPI
Linux: libsecret / Keyring
```

### 4. 設備綁定

```
- 設備指紋 = hash(硬體ID + 作業系統 + 安裝時間)
- 如果指紋改變，要求重新配對
```

### 5. 權限控制

```
- 每個設備可設定允許的能力
- 敏感操作需要使用者確認
- 可設定 IP 白名單
```

---

## API 設計

### 配對 API

```
POST /api/agent/pair
Content-Type: application/json

{
  "pairingCode": "123456",
  "deviceId": "uuid",
  "devicePublicKey": "base64-x25519-public-key",
  "deviceInfo": {
    "name": "MacBook Pro",
    "platform": "macos",
    "version": "1.0.0",
    "fingerprint": "sha256-hash"
  }
}

Response:
{
  "success": true,
  "platformPublicKey": "base64-x25519-public-key",
  "platformFingerprint": "sha256-hash",
  "deviceToken": "jwt-token-for-reconnect"
}
```

### 加密訊息 API

```
POST /api/agent/message
Content-Type: application/jose

eyJ2IjoxLCJhbGciOiJBMjU2R0NNIiwiZGlkIjoiZGV2aWNlLXV1aWQiLC...

Response:
eyJ2IjoxLCJhbGciOiJBMjU2R0NNIiwiZGlkIjoicGxhdGZvcm0iLC...
```

### WebSocket 協議

```
1. 連線: wss://platform/ws/agent
2. 認證: 發送加密的 auth 訊息
3. 心跳: 定期發送加密的 ping/pong
4. 訊息: 所有訊息都是加密的
```

---

## 使用者操作流程

### 首次配對（一次性）

```
1. 平台: 點擊「新增設備」→ 顯示 6 位配對碼
2. Agent: 安裝後輸入配對碼
3. 完成! 之後自動連線
```

### 日常使用

```
- Agent 開機自動啟動
- 自動連線到平台
- 使用者無需任何操作
```

### 遠端連線設定（可選）

```
1. Agent: 開啟「允許外部連線」
2. Agent: 設定 Port 和外部 IP
3. 平台: 可透過 IP 直接控制
```

---

## 實作優先順序

1. **Phase 1**: 基本配對和加密通訊
2. **Phase 2**: WebSocket 連線模式
3. **Phase 3**: 直接 IP 連線模式
4. **Phase 4**: 進階安全功能（IP 白名單、操作確認）
