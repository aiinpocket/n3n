# N3N Local Agent & Platform Extensions 設計文件

## 概覽

本設計包含以下功能：

1. **N3N Local Agent** - 本地代理程式，讓 n3n 平台能夠控制使用者的 macOS/Windows/Linux 設備
2. **Browser Control** - 瀏覽器自動化控制（CDP 協議）
3. **Multi-Channel Integration** - 多通道訊息整合（Telegram、Discord、Line、WhatsApp）
4. **Plugin Marketplace** - 插件下載市場（Local Agent、Skills、Nodes、Themes）

---

## OpenClaw vs N3N 功能比較

### N3N 已有功能

| 類別 | 功能 |
|------|------|
| 流程設計 | 視覺化 Flow 編輯器 (React Flow) |
| 排程 | Cron 排程、間隔執行 (Quartz) |
| Webhook | HTTP 觸發器 |
| AI 整合 | Claude, OpenAI, Gemini, Ollama |
| 資料庫 | PostgreSQL, MySQL, MongoDB, Redis, Elasticsearch, BigQuery |
| 雲端 | GCP (Sheets, Drive, Calendar, Gmail, Storage, Pub/Sub) |
| 社群媒體 | Slack, Facebook, Instagram, Threads |
| 憑證 | 加密儲存、Recovery Key |
| 版本控制 | Flow 版本管理 |

### OpenClaw 有但 N3N 缺少的功能

| 功能 | 優先級 | 複雜度 | 說明 |
|------|--------|--------|------|
| **Local Agent** | 高 | 高 | macOS/Windows/Linux 本地控制 |
| **Browser Control** | 高 | 中 | Chromium 自動化 |
| **WhatsApp** | 中 | 中 | WhatsApp Business API |
| **Telegram** | 中 | 低 | Telegram Bot API |
| **Discord** | 中 | 低 | Discord Bot |
| **Signal** | 中 | 中 | Signal CLI |
| **iMessage** | 中 | 高 | 需要 macOS + BlueBubbles |
| **Canvas (A2UI)** | 低 | 高 | Agent 視覺畫布 |

---

## Part 1: N3N Local Agent

### 1.1 系統架構

```
┌─────────────────────────────────────────────────────────────────┐
│  N3N Cloud Platform (Docker/K8s)                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Gateway WebSocket Server                                  │  │
│  │  ws://n3n-server:8080/ws/agent                            │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐                │  │
│  │  │ Session  │  │  Node    │  │  Skill   │                │  │
│  │  │ Manager  │  │ Registry │  │ Registry │                │  │
│  │  └──────────┘  └──────────┘  └──────────┘                │  │
│  └──────────────────────┬───────────────────────────────────┘  │
└─────────────────────────┼───────────────────────────────────────┘
                          │ WebSocket (wss://)
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ macOS Agent   │ │ Windows Agent │ │ Linux Agent   │
│ (Menu Bar)    │ │ (System Tray) │ │ (Daemon)      │
│ ┌───────────┐ │ │ ┌───────────┐ │ │ ┌───────────┐ │
│ │AppleScript│ │ │ │ PowerShell│ │ │ │   Bash    │ │
│ │ Screen    │ │ │ │ Screen    │ │ │ │   X11     │ │
│ │ Notify    │ │ │ │ Notify    │ │ │ │   Notify  │ │
│ │ Camera    │ │ │ │ Camera    │ │ │ │   Camera  │ │
│ └───────────┘ │ │ └───────────┘ │ │ └───────────┘ │
└───────────────┘ └───────────────┘ └───────────────┘
```

### 1.2 Gateway WebSocket 協議

#### 訊息格式

```typescript
// 請求訊息
interface AgentRequest {
  type: 'req';
  id: string;              // UUID for request tracking
  method: string;          // e.g., 'node.invoke', 'node.register'
  params: Record<string, any>;
  ts: number;              // Unix timestamp
}

// 回應訊息
interface AgentResponse {
  type: 'res';
  id: string;              // Matches request id
  ok: boolean;
  payload?: Record<string, any>;
  error?: {
    code: string;
    message: string;
  };
}

// 事件訊息 (Server Push)
interface AgentEvent {
  type: 'event';
  event: string;           // e.g., 'node.status', 'execution.progress'
  payload: Record<string, any>;
  seq: number;
  ts: number;
}
```

#### 連線握手流程

```
Client                                    Server
  │                                         │
  │──────── WebSocket Connect ─────────────▶│
  │                                         │
  │◀─────── connect.challenge ──────────────│
  │         { nonce, ts }                   │
  │                                         │
  │──────── connect ────────────────────────▶│
  │         { client, auth, caps }          │
  │                                         │
  │◀─────── connect.ok ─────────────────────│
  │         { deviceToken, role, scopes }   │
  │                                         │
  │──────── node.register ──────────────────▶│
  │         { capabilities, permissions }   │
  │                                         │
  │◀─────── node.registered ────────────────│
  │         { nodeId }                      │
  │                                         │
```

#### 認證方式

```typescript
interface ConnectParams {
  client: {
    id: string;              // Stable device fingerprint
    displayName: string;     // User-friendly name
    version: string;         // Agent version
    platform: 'macos' | 'windows' | 'linux' | 'ios' | 'android';
    arch: 'x64' | 'arm64';
    instanceId: string;      // Unique per session
  };
  auth: {
    // Option 1: Device token (after first auth)
    deviceToken?: string;
    // Option 2: User credentials
    userToken?: string;      // JWT from n3n login
    // Option 3: Pairing code
    pairingCode?: string;    // 6-digit code shown on platform
  };
  caps: string[];            // Declared capabilities
}
```

### 1.3 Node 能力定義

#### macOS 能力

```typescript
interface MacOSCapabilities {
  // 系統命令
  'system.run': {
    cmd: string;
    args?: string[];
    cwd?: string;
    env?: Record<string, string>;
    timeout?: number;
    shell?: boolean;
  };

  // AppleScript 執行
  'system.applescript': {
    script: string;
    args?: string[];
  };

  // 系統通知
  'system.notify': {
    title: string;
    body: string;
    sound?: string;
    actions?: Array<{ id: string; title: string }>;
  };

  // 螢幕操作
  'screen.capture': {
    display?: number;        // 0 = all, 1+ = specific
    region?: { x: number; y: number; width: number; height: number };
    format?: 'png' | 'jpg';
  };

  'screen.ocr': {
    region?: { x: number; y: number; width: number; height: number };
    language?: string;
  };

  // 滑鼠/鍵盤
  'input.click': {
    x: number;
    y: number;
    button?: 'left' | 'right' | 'middle';
    clickCount?: number;
  };

  'input.type': {
    text: string;
    delay?: number;          // ms between keystrokes
  };

  'input.key': {
    key: string;             // e.g., 'enter', 'cmd+c'
  };

  // 應用程式控制
  'app.open': {
    bundleId?: string;
    path?: string;
    args?: string[];
  };

  'app.list': {
    running?: boolean;
  };

  'app.focus': {
    bundleId: string;
  };

  'app.quit': {
    bundleId: string;
    force?: boolean;
  };

  // 剪貼簿
  'clipboard.read': {
    format?: 'text' | 'image' | 'files';
  };

  'clipboard.write': {
    text?: string;
    image?: string;          // base64
    files?: string[];        // paths
  };

  // 檔案系統
  'fs.read': {
    path: string;
    encoding?: 'utf8' | 'base64';
  };

  'fs.write': {
    path: string;
    content: string;
    encoding?: 'utf8' | 'base64';
  };

  'fs.list': {
    path: string;
    recursive?: boolean;
  };

  // 瀏覽器自動化
  'browser.open': {
    url: string;
    browser?: 'default' | 'chrome' | 'safari' | 'firefox';
  };

  // Shortcuts 執行
  'shortcuts.run': {
    name: string;
    input?: any;
  };

  'shortcuts.list': {};
}
```

#### 權限控制

```typescript
interface NodePermissions {
  // macOS TCC 權限
  accessibility: boolean;    // 輔助使用
  screenRecording: boolean;  // 螢幕錄製
  fullDiskAccess: boolean;   // 完整磁碟存取
  camera: boolean;           // 相機
  microphone: boolean;       // 麥克風

  // 應用程式內權限
  allowedCommands: string[]; // 允許的 shell 命令 pattern
  blockedCommands: string[]; // 封鎖的命令 pattern
  allowedPaths: string[];    // 允許存取的路徑
  blockedPaths: string[];    // 封鎖的路徑
}
```

### 1.4 命令執行流程

```
Platform (Flow Execution)              Gateway                    Local Agent
        │                                 │                            │
        │─── node.invoke ────────────────▶│                            │
        │    { nodeId, capability,        │                            │
        │      command, args }            │                            │
        │                                 │─── invoke ────────────────▶│
        │                                 │    { capability, args }    │
        │                                 │                            │
        │                                 │    [權限檢查]              │
        │                                 │    [TCC 檢查]              │
        │                                 │    [執行命令]              │
        │                                 │                            │
        │                                 │◀── invoke.result ──────────│
        │                                 │    { exitCode, stdout,     │
        │◀── node.result ─────────────────│      stderr, duration }    │
        │    { result }                   │                            │
        │                                 │                            │
```

### 1.5 macOS Agent 實作規劃

#### 技術選型

| 元件 | 技術 | 說明 |
|------|------|------|
| 主程式 | Swift + SwiftUI | 原生 macOS 體驗 |
| Menu Bar | NSStatusItem | 系統狀態列整合 |
| WebSocket | URLSessionWebSocketTask | 原生 WebSocket |
| 螢幕擷取 | ScreenCaptureKit | macOS 12.3+ |
| 輸入模擬 | CGEvent / AXUIElement | 滑鼠/鍵盤控制 |
| AppleScript | NSAppleScript | 腳本執行 |
| Shortcuts | SFShortcutExtractor | 快捷指令整合 |
| 更新機制 | Sparkle | 自動更新 |
| 簽章 | Apple Developer ID | 公證與簽章 |

#### 專案結構

```
n3n-agent-macos/
├── N3NAgent/
│   ├── App/
│   │   ├── N3NAgentApp.swift       # App entry point
│   │   ├── MenuBarController.swift  # Menu bar UI
│   │   └── SettingsView.swift       # Settings window
│   ├── Gateway/
│   │   ├── GatewayClient.swift      # WebSocket client
│   │   ├── MessageHandler.swift     # Message routing
│   │   └── AuthManager.swift        # Authentication
│   ├── Capabilities/
│   │   ├── CapabilityRegistry.swift # Capability management
│   │   ├── SystemCapability.swift   # system.* handlers
│   │   ├── ScreenCapability.swift   # screen.* handlers
│   │   ├── InputCapability.swift    # input.* handlers
│   │   ├── AppCapability.swift      # app.* handlers
│   │   ├── FSCapability.swift       # fs.* handlers
│   │   └── ShortcutsCapability.swift # shortcuts.* handlers
│   ├── Permissions/
│   │   ├── PermissionManager.swift  # TCC permission handling
│   │   └── ApprovalManager.swift    # Command approval
│   ├── Security/
│   │   ├── KeychainManager.swift    # Secure storage
│   │   └── DeviceIdentity.swift     # Device fingerprint
│   └── Resources/
│       └── Assets.xcassets
├── N3NAgentTests/
├── N3NAgent.xcodeproj
└── README.md
```

---

## Part 2: Browser Control (瀏覽器自動化)

### 2.1 概念設計

Browser Control 讓 n3n 能夠自動化操作瀏覽器，支援：

- 開啟網頁、導航
- 截圖、OCR 文字識別
- 點擊、輸入、滾動
- 執行 JavaScript
- Cookie 和 Session 管理

```
┌─────────────────────────────────────────────────────────────────┐
│  N3N Platform                                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Browser Node Handler                                      │  │
│  │  ┌───────────────┐                                        │  │
│  │  │ browser.open  │                                        │  │
│  │  │ browser.goto  │                                        │  │
│  │  │ browser.click │                                        │  │
│  │  │ browser.type  │                                        │  │
│  │  │ browser.snap  │                                        │  │
│  │  │ browser.eval  │                                        │  │
│  │  └───────────────┘                                        │  │
│  └──────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────┘
                              │ CDP (Chrome DevTools Protocol)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Chromium / Chrome (Headless or Headed)                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Tab 1    │  Tab 2    │  Tab 3                           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 技術選型

| 元件 | 技術 | 說明 |
|------|------|------|
| 瀏覽器引擎 | Chromium/Chrome | 標準瀏覽器引擎 |
| 控制協議 | CDP (Chrome DevTools Protocol) | 原生瀏覽器控制 |
| Java Client | [CDP4J](https://github.com/nicoulaj/cdp4j) 或 Playwright | Java CDP 客戶端 |
| 螢幕截圖 | CDP Page.captureScreenshot | 截圖功能 |
| 文字識別 | Tesseract OCR | OCR 功能 |

### 2.3 Browser Node Handler 操作

```java
// 瀏覽器操作定義
interface BrowserOperations {
  // Session 管理
  'session.create': { headless?: boolean; profile?: string };
  'session.close': { sessionId: string };

  // 導航
  'page.goto': { url: string; waitUntil?: 'load' | 'domcontentloaded' | 'networkidle' };
  'page.back': {};
  'page.forward': {};
  'page.reload': {};

  // 截圖
  'page.screenshot': { fullPage?: boolean; selector?: string; format?: 'png' | 'jpg' };
  'page.pdf': { format?: 'A4' | 'Letter' };

  // 元素操作
  'element.click': { selector: string };
  'element.type': { selector: string; text: string; delay?: number };
  'element.select': { selector: string; value: string };
  'element.check': { selector: string };
  'element.uncheck': { selector: string };
  'element.hover': { selector: string };

  // 滾動
  'page.scroll': { x?: number; y?: number; selector?: string };

  // JavaScript
  'page.evaluate': { script: string };

  // 等待
  'page.waitForSelector': { selector: string; timeout?: number };
  'page.waitForNavigation': { timeout?: number };

  // Cookie
  'cookie.get': { name?: string };
  'cookie.set': { name: string; value: string; domain?: string };
  'cookie.clear': {};

  // 內容
  'page.content': {};          // 取得 HTML
  'page.text': { selector?: string };  // 取得文字
  'page.title': {};
  'page.url': {};
}
```

### 2.4 實作選項

#### 選項 A: 內建於 Docker (推薦)

```dockerfile
# 在 n3n Docker image 中內建 Chromium
FROM eclipse-temurin:21-jre-alpine

# 安裝 Chromium
RUN apk add --no-cache chromium chromium-chromedriver

ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROME_PATH=/usr/lib/chromium/
```

**優點**: 開箱即用，無需額外設置
**缺點**: Docker image 較大 (~500MB)

#### 選項 B: 透過 Local Agent

```
N3N Platform ─── WebSocket ───▶ Local Agent ─── CDP ───▶ Chrome
```

**優點**: 可使用使用者本地瀏覽器
**缺點**: 需要安裝 Local Agent

---

## Part 2.5: Multi-Channel Integration (多通道整合)

### 現有 vs 缺少的通道

| 通道 | 狀態 | API | 說明 |
|------|------|-----|------|
| Slack | 已有 | Web API | 訊息、檔案 |
| Facebook | 已有 | Graph API | 貼文、訊息 |
| Instagram | 已有 | Graph API | 貼文、Stories |
| Threads | 已有 | Threads API | 貼文、回覆 |
| Email | 已有 | SMTP | 發送郵件 |
| **Telegram** | 缺少 | Bot API | 機器人訊息 |
| **Discord** | 缺少 | Bot API | 伺服器訊息 |
| **WhatsApp** | 缺少 | Business API | 商業訊息 |
| **Line** | 缺少 | Messaging API | 推播訊息 |
| **Signal** | 缺少 | signal-cli | 加密訊息 |
| **iMessage** | 缺少 | BlueBubbles | 需 macOS |
| **Teams** | 缺少 | Graph API | 企業訊息 |

### 優先實作順序

1. **Telegram** - 簡單、免費、廣泛使用
2. **Discord** - 社群導向、Webhook 支援
3. **Line** - 亞洲市場
4. **WhatsApp** - 需付費 Business API

### Telegram Node Handler 設計

```typescript
interface TelegramOperations {
  // 訊息
  'message.send': {
    chatId: string | number;
    text: string;
    parseMode?: 'Markdown' | 'HTML';
    replyToMessageId?: number;
  };

  'message.edit': {
    chatId: string | number;
    messageId: number;
    text: string;
  };

  'message.delete': {
    chatId: string | number;
    messageId: number;
  };

  // 媒體
  'photo.send': {
    chatId: string | number;
    photo: string;  // URL or file_id
    caption?: string;
  };

  'document.send': {
    chatId: string | number;
    document: string;
    caption?: string;
  };

  // 聊天
  'chat.getInfo': { chatId: string | number };
  'chat.getMembers': { chatId: string | number };

  // Webhook
  'webhook.set': { url: string };
  'webhook.delete': {};
  'webhook.getInfo': {};

  // Bot
  'bot.getMe': {};
  'bot.getUpdates': { offset?: number; limit?: number };
}
```

### Discord Node Handler 設計

```typescript
interface DiscordOperations {
  // 訊息
  'message.send': {
    channelId: string;
    content: string;
    embeds?: DiscordEmbed[];
    components?: DiscordComponent[];
  };

  'message.edit': {
    channelId: string;
    messageId: string;
    content?: string;
    embeds?: DiscordEmbed[];
  };

  'message.delete': {
    channelId: string;
    messageId: string;
  };

  'message.react': {
    channelId: string;
    messageId: string;
    emoji: string;
  };

  // 頻道
  'channel.get': { channelId: string };
  'channel.list': { guildId: string };
  'channel.create': {
    guildId: string;
    name: string;
    type: 'text' | 'voice' | 'category';
  };

  // Webhook
  'webhook.execute': {
    webhookUrl: string;
    content?: string;
    embeds?: DiscordEmbed[];
    username?: string;
    avatarUrl?: string;
  };

  // 使用者
  'user.get': { userId: string };
  'user.dm': { userId: string; content: string };
}
```

---

## Part 3: Plugin Marketplace (插件市場)

### 3.1 概念

```
┌─────────────────────────────────────────────────────────────────┐
│                    N3N Plugin Marketplace                        │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Categories                                                │  │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐      │  │
│  │  │  Local  │  │  Skills │  │  Nodes  │  │ Themes  │      │  │
│  │  │ Agents  │  │         │  │         │  │         │      │  │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘      │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Featured Plugins                                          │  │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐ │  │
│  │  │ macOS Agent   │  │ Gmail Skill   │  │ Notion Node   │ │  │
│  │  │ 5 星 4.8    │  │ 4 星 4.2    │  │ 5 星 4.9    │ │  │
│  │  │ 10K downloads │  │ 5K downloads  │  │ 8K downloads  │ │  │
│  │  └───────────────┘  └───────────────┘  └───────────────┘ │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 插件類型

```typescript
type PluginType =
  | 'local-agent'     // 本地代理程式 (macOS, Windows, Linux, iOS, Android)
  | 'skill'           // 可複用的能力模組
  | 'node'            // 流程節點
  | 'theme'           // UI 主題
  | 'integration';    // 第三方整合

interface Plugin {
  id: string;
  type: PluginType;

  // 基本資訊
  name: string;
  displayName: string;
  description: string;
  longDescription: string;
  icon: string;
  screenshots: string[];

  // 版本
  version: string;
  changelog: string;

  // 作者
  author: {
    id: string;
    name: string;
    email?: string;
    website?: string;
    verified: boolean;
  };

  // 分類
  category: string;
  tags: string[];

  // 相容性
  compatibility: {
    n3nVersion: string;      // e.g., ">=1.0.0"
    platforms?: string[];    // e.g., ["macos", "windows"]
  };

  // 下載資訊
  downloads: {
    total: number;
    weekly: number;
  };

  // 評價
  rating: {
    average: number;
    count: number;
  };

  // 價格
  pricing: {
    free: boolean;
    price?: number;
    currency?: string;
  };

  // 安裝資訊
  installation: {
    // Local Agent
    macosUrl?: string;
    windowsUrl?: string;
    linuxUrl?: string;

    // Skill / Node
    packageUrl?: string;

    // Instructions
    instructions?: string;
  };

  // 時間
  createdAt: Date;
  updatedAt: Date;
  publishedAt: Date;
}
```

### 3.3 Local Agent 下載流程

```
使用者                  N3N Platform                Plugin Server           Local Agent
  │                         │                           │                      │
  │─── 瀏覽插件市場 ────────▶│                           │                      │
  │                         │◀── 取得插件列表 ───────────│                      │
  │◀── 顯示可用 Agent ──────│                           │                      │
  │                         │                           │                      │
  │─── 點擊下載 macOS ──────▶│                           │                      │
  │                         │─── 產生下載連結 ──────────▶│                      │
  │◀── 下載 DMG ─────────────────────────────────────────│                      │
  │                         │                           │                      │
  │─── 安裝 Agent ───────────────────────────────────────────────────────────────▶│
  │                         │                           │                      │
  │─── 開啟 Agent ────────────────────────────────────────────────────────────────▶│
  │                         │                           │                      │
  │◀── 顯示配對碼 ─────────────────────────────────────────────────────────────────│
  │                         │                           │                      │
  │─── 在 N3N 輸入配對碼 ───▶│                           │                      │
  │                         │─── 配對請求 ──────────────────────────────────────▶│
  │                         │◀── 配對成功 ──────────────────────────────────────│
  │◀── 連線成功 ────────────│                           │                      │
```

---

## Part 4: 實作計畫

### 4.1 階段規劃

#### Phase 1: Gateway Protocol (2 週)

```
目標：建立 Gateway WebSocket 協議和基礎設施

新增檔案：
src/main/java/com/aiinpocket/n3n/gateway/
├── protocol/
│   ├── GatewayMessage.java          # 訊息基類
│   ├── GatewayRequest.java          # 請求訊息
│   ├── GatewayResponse.java         # 回應訊息
│   ├── GatewayEvent.java            # 事件訊息
│   └── ProtocolVersion.java         # 協議版本
├── handler/
│   ├── GatewayWebSocketHandler.java # WebSocket 處理器
│   ├── MessageRouter.java           # 訊息路由
│   └── AuthHandler.java             # 認證處理
├── node/
│   ├── NodeRegistry.java            # Node 註冊表
│   ├── NodeConnection.java          # Node 連線管理
│   ├── NodeCapability.java          # 能力定義
│   └── NodeInvoker.java             # 命令調用
└── session/
    ├── GatewaySession.java          # Session 管理
    └── SessionStore.java            # Session 存儲
```

#### Phase 2: macOS Agent (4 週)

```
目標：開發 macOS 本地代理程式

專案結構：
n3n-agent-macos/
├── N3NAgent.xcodeproj
├── N3NAgent/
│   ├── App/                         # 應用程式入口
│   ├── Gateway/                     # Gateway 通訊
│   ├── Capabilities/                # 能力實作
│   ├── Permissions/                 # 權限管理
│   ├── Security/                    # 安全相關
│   └── UI/                          # SwiftUI 介面
└── scripts/
    ├── build.sh                     # 建置腳本
    ├── sign.sh                      # 簽章腳本
    └── notarize.sh                  # 公證腳本
```

#### Phase 3: Plugin Marketplace (2 週)

```
目標：建立插件市場

後端：
src/main/java/com/aiinpocket/n3n/marketplace/
├── entity/
│   ├── Plugin.java
│   ├── PluginVersion.java
│   ├── PluginReview.java
│   └── PluginDownload.java
├── repository/
│   ├── PluginRepository.java
│   └── PluginVersionRepository.java
├── service/
│   ├── PluginService.java
│   └── PluginSearchService.java
└── controller/
    └── PluginController.java

前端：
src/main/frontend/src/pages/
├── MarketplacePage.tsx
├── PluginDetailPage.tsx
└── components/
    ├── PluginCard.tsx
    ├── PluginSearch.tsx
    └── PluginInstaller.tsx
```

#### Phase 4: Multi-Channel Integration (2 週)

```
目標：實作多通道訊息整合

後端：
src/main/java/com/aiinpocket/n3n/execution/handler/handlers/messaging/
├── TelegramNodeHandler.java
├── DiscordNodeHandler.java
├── LineNodeHandler.java
└── WhatsAppNodeHandler.java

測試：
- Telegram Bot 訊息收發
- Discord 頻道訊息
- Line 推播訊息
```

### 4.2 資料庫 Schema

```sql
-- Plugin Marketplace
CREATE TABLE plugins (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    icon_url VARCHAR(500),
    author_id UUID REFERENCES users(id),
    category VARCHAR(50),
    tags TEXT[],
    rating_avg DECIMAL(3,2) DEFAULT 0,
    rating_count INTEGER DEFAULT 0,
    download_count INTEGER DEFAULT 0,
    pricing_free BOOLEAN DEFAULT true,
    pricing_amount DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plugin_versions (
    id UUID PRIMARY KEY,
    plugin_id UUID REFERENCES plugins(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    changelog TEXT,
    macos_url VARCHAR(500),
    windows_url VARCHAR(500),
    linux_url VARCHAR(500),
    package_url VARCHAR(500),
    min_n3n_version VARCHAR(50),
    published_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plugin_id, version)
);

-- Connected Agents
CREATE TABLE connected_agents (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    device_id VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    platform VARCHAR(50) NOT NULL,       -- macos, windows, linux
    arch VARCHAR(20),                    -- x64, arm64
    version VARCHAR(50),
    capabilities TEXT[],
    permissions JSONB DEFAULT '{}',
    last_connected_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, device_id)
);

-- Indexes
CREATE INDEX idx_plugins_category ON plugins(category);
CREATE INDEX idx_plugins_tags ON plugins USING GIN (tags);
CREATE INDEX idx_connected_agents_user ON connected_agents(user_id);
```

---

## 總結

### 核心價值

1. **Local Agent** - 突破雲端限制，讓 AI 能控制本地設備
2. **Browser Control** - 自動化瀏覽器操作，支援網頁抓取和自動化
3. **Multi-Channel** - 整合多種訊息通道（Telegram、Discord、Line 等）
4. **Plugin Marketplace** - 建立生態系統，讓社群能貢獻和受益

### 差異化優勢

| 功能 | OpenClaw | N3N |
|------|----------|-----|
| 定位 | 個人 AI 助手 | 流程自動化平台 |
| Local Agent | 單一使用者 | 多使用者/多租戶 |
| Browser Control | Chromium 控制 | CDP 標準協議 |
| 訊息整合 | Telegram, WhatsApp | 多通道統一 API |
| 插件市場 | ClawHub (技能) | 完整插件生態 |
| 部署方式 | 本地優先 | 雲端 + 本地 Agent |

### 預估時程

| 階段 | 時間 | 產出 |
|------|------|------|
| Phase 1 | 2 週 | Gateway Protocol |
| Phase 2 | 4 週 | macOS Agent |
| Phase 3 | 2 週 | Plugin Marketplace |
| Phase 4 | 2 週 | Multi-Channel Integration |
| **總計** | **10 週** | 完整功能 |
