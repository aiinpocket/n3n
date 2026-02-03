# N3N Flow Platform UI/UX 改善計畫

> 基於 UI/UX Pro Max 設計系統生成，目標是打造超越 n8n 的使用體驗

---

## 目錄

1. [現況分析](#現況分析)
2. [設計系統](#設計系統)
3. [改善優先級](#改善優先級)
4. [詳細改善項目](#詳細改善項目)
5. [實施計畫](#實施計畫)

---

## 現況分析

### 目前問題

| 問題類型 | 具體問題 | 嚴重程度 |
|---------|---------|---------|
| **視覺設計** | 使用 Ant Design 預設樣式，缺乏品牌特色 | 高 |
| **配色方案** | 純白背景單調，缺乏層次感 | 中 |
| **字體排版** | 使用系統預設字體，不夠現代 | 中 |
| **側邊欄** | 選單項目過多，沒有分組，難以找到功能 | 高 |
| **流程編輯器** | 節點樣式簡陋，缺乏視覺吸引力 | 高 |
| **配置面板** | 抽屜式設計佔用空間，打斷工作流程 | 中 |
| **互動反饋** | hover 效果單調，缺乏微動畫 | 中 |
| **深色模式** | 不支援深色模式 | 中 |

### 與 n8n 比較

| 功能 | n8n | N3N 現況 | 目標 |
|-----|-----|---------|------|
| 節點視覺 | 圓角卡片 + 圖示 | 基本色塊 | 超越 |
| 連接線 | 平滑貝茲曲線 + 動畫 | 基本線條 | 達到 |
| 工具列 | 浮動工具列 | 頂部固定 | 超越 |
| 節點搜尋 | Cmd+K 快速搜尋 | 下拉選單 | 超越 |
| 執行狀態 | 即時動畫反饋 | 基本狀態色 | 達到 |
| 深色模式 | 完整支援 | 無 | 達到 |

---

## 設計系統

### 配色方案（深色主題）

```css
:root {
  /* 主色調 - Dark Mode */
  --color-bg-primary: #020617;      /* 主背景 - 深黑 */
  --color-bg-secondary: #0F172A;    /* 次要背景 - slate-900 */
  --color-bg-elevated: #1E293B;     /* 提升區域 - slate-800 */
  --color-bg-hover: #334155;        /* Hover 背景 - slate-700 */

  /* 強調色 */
  --color-primary: #6366F1;         /* 主要 - Indigo */
  --color-secondary: #818CF8;       /* 次要 - Indigo lighter */
  --color-success: #22C55E;         /* 成功 - Green */
  --color-warning: #F59E0B;         /* 警告 - Amber */
  --color-danger: #EF4444;          /* 危險 - Red */
  --color-info: #3B82F6;            /* 資訊 - Blue */

  /* 文字 */
  --color-text-primary: #F8FAFC;    /* 主要文字 */
  --color-text-secondary: #94A3B8;  /* 次要文字 */
  --color-text-muted: #64748B;      /* 輔助文字 */

  /* 邊框 */
  --color-border: #334155;          /* 邊框 */
  --color-border-hover: #475569;    /* Hover 邊框 */

  /* 節點類型顏色 */
  --node-trigger: #22C55E;          /* 觸發器 - 綠色 */
  --node-action: #3B82F6;           /* 動作 - 藍色 */
  --node-condition: #F59E0B;        /* 條件 - 黃色 */
  --node-loop: #A855F7;             /* 迴圈 - 紫色 */
  --node-code: #14B8A6;             /* 程式碼 - 青色 */
  --node-http: #6366F1;             /* HTTP - 靛藍 */
  --node-agent: #EC4899;            /* Agent - 粉紅 */
  --node-ai: #8B5CF6;               /* AI - 紫羅蘭 */
}
```

### 字體

```css
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');

:root {
  --font-sans: 'Plus Jakarta Sans', system-ui, sans-serif;
  --font-mono: 'JetBrains Mono', monospace;
}
```

### 間距系統

```css
:root {
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
  --space-12: 48px;
}
```

### 圓角

```css
:root {
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-2xl: 24px;
  --radius-full: 9999px;
}
```

---

## 改善優先級

### Phase 1: 基礎體驗（高優先）

| 項目 | 工作量 | 影響 | 說明 |
|-----|-------|------|------|
| 深色主題 | 3天 | 高 | 全局深色模式支援 |
| 側邊欄重構 | 2天 | 高 | 分組、收合、搜尋 |
| 節點樣式升級 | 3天 | 高 | 新設計語言、圖示 |
| 字體替換 | 0.5天 | 中 | Plus Jakarta Sans |

### Phase 2: 流程編輯器（中優先）

| 項目 | 工作量 | 影響 | 說明 |
|-----|-------|------|------|
| 節點快速搜尋 | 2天 | 高 | Cmd+K 命令面板 |
| 右側配置面板 | 3天 | 高 | 取代抽屜設計 |
| 執行狀態動畫 | 2天 | 中 | 即時視覺反饋 |
| 小地圖導航 | 1天 | 低 | 大流程導航 |

### Phase 3: 細節優化（低優先）

| 項目 | 工作量 | 影響 | 說明 |
|-----|-------|------|------|
| 微動畫系統 | 2天 | 低 | Hover、過渡效果 |
| 快捷鍵系統 | 2天 | 中 | 完整鍵盤操作 |
| 可存取性 | 2天 | 中 | WCAG AA 達標 |
| 響應式設計 | 2天 | 中 | 平板支援 |

---

## 詳細改善項目

### 1. 側邊欄重構

**現況問題：**
- 9+ 選單項目平鋪顯示
- 沒有分組邏輯
- 不支援搜尋

**改善方案：**

```tsx
const menuGroups = [
  {
    key: 'workflows',
    label: '工作流程',
    icon: <WorkflowIcon />,
    items: [
      { key: '/flows', label: '流程管理', icon: <ApartmentOutlined /> },
      { key: '/executions', label: '執行記錄', icon: <PlayCircleOutlined /> },
      { key: '/webhooks', label: 'Webhooks', icon: <LinkOutlined /> },
    ]
  },
  {
    key: 'integrations',
    label: '整合',
    icon: <ApiOutlined />,
    items: [
      { key: '/services', label: '外部服務', icon: <ApiOutlined /> },
      { key: '/credentials', label: '憑證', icon: <KeyOutlined /> },
      { key: '/devices', label: '裝置', icon: <DesktopOutlined /> },
    ]
  },
  {
    key: 'tools',
    label: '工具',
    icon: <ToolOutlined />,
    items: [
      { key: '/skills', label: '技能', icon: <ToolOutlined /> },
      { key: '/marketplace', label: '市場', icon: <ShopOutlined /> },
      { key: '/ai-assistant', label: 'AI 助手', icon: <RobotOutlined /> },
    ]
  },
];
```

### 2. 節點視覺升級

**新節點設計：**

```tsx
const NodeDesign = {
  // 基礎樣式
  base: {
    borderRadius: '12px',
    padding: '12px 16px',
    minWidth: '200px',
    backdropFilter: 'blur(8px)',
    boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
    border: '1px solid rgba(255,255,255,0.1)',
  },

  // 類型特定樣式
  trigger: {
    background: 'linear-gradient(135deg, #22C55E20 0%, #22C55E10 100%)',
    borderLeft: '4px solid #22C55E',
  },

  action: {
    background: 'linear-gradient(135deg, #3B82F620 0%, #3B82F610 100%)',
    borderLeft: '4px solid #3B82F6',
  },

  // Hover 效果
  hover: {
    transform: 'translateY(-2px)',
    boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
    borderColor: 'var(--color-primary)',
  },

  // 選中狀態
  selected: {
    boxShadow: '0 0 0 2px var(--color-primary)',
  },

  // 執行中狀態
  running: {
    animation: 'pulse 1.5s infinite',
    boxShadow: '0 0 20px var(--node-color)',
  },
};
```

### 3. 命令面板（Cmd+K）

**功能設計：**

```tsx
interface CommandPaletteItem {
  id: string;
  type: 'node' | 'action' | 'navigation' | 'setting';
  title: string;
  description?: string;
  icon: ReactNode;
  keywords: string[];
  shortcut?: string;
  action: () => void;
}

const commands: CommandPaletteItem[] = [
  // 快速新增節點
  { type: 'node', title: 'HTTP Request', icon: <HttpIcon />, keywords: ['http', 'api', 'request'] },
  { type: 'node', title: 'Code', icon: <CodeIcon />, keywords: ['code', 'javascript', 'script'] },
  { type: 'node', title: 'Agent', icon: <AgentIcon />, keywords: ['agent', 'device', 'macos'] },

  // 快速操作
  { type: 'action', title: '執行流程', shortcut: '⌘+Enter', action: runFlow },
  { type: 'action', title: '儲存', shortcut: '⌘+S', action: saveFlow },

  // 導航
  { type: 'navigation', title: '流程列表', shortcut: '⌘+1', action: () => navigate('/flows') },
];
```

### 4. 右側配置面板

**取代抽屜設計，使用固定面板：**

```tsx
<div className="flow-editor-layout">
  {/* 左側節點面板 */}
  <aside className="node-palette">
    <NodeSearch />
    <NodeCategories />
  </aside>

  {/* 中央畫布 */}
  <main className="canvas">
    <ReactFlow ... />
    <Toolbar />
    <MiniMap />
  </main>

  {/* 右側配置面板（固定顯示） */}
  <aside className="config-panel">
    {selectedNode ? (
      <NodeConfigPanel node={selectedNode} />
    ) : (
      <FlowSettings />
    )}
  </aside>
</div>
```

### 5. 執行狀態動畫

```css
/* 節點執行動畫 */
@keyframes node-running {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(var(--node-color-rgb), 0.4);
  }
  50% {
    box-shadow: 0 0 20px 10px rgba(var(--node-color-rgb), 0.2);
  }
}

@keyframes node-success {
  0% { transform: scale(1); }
  50% { transform: scale(1.05); }
  100% { transform: scale(1); }
}

@keyframes edge-flow {
  0% { stroke-dashoffset: 20; }
  100% { stroke-dashoffset: 0; }
}

.node-running {
  animation: node-running 1.5s ease-in-out infinite;
}

.node-success {
  animation: node-success 0.3s ease-out;
}

.edge-running {
  stroke-dasharray: 5;
  animation: edge-flow 0.5s linear infinite;
}
```

---

## 實施計畫

### 第一階段：基礎體驗（1-2 週）

```
Week 1:
├── Day 1-2: 設計系統建立（CSS 變數、Tailwind 配置）
├── Day 3-4: 深色主題實現
└── Day 5: 字體替換 + 基礎樣式調整

Week 2:
├── Day 1-2: 側邊欄重構
├── Day 3-4: 節點視覺升級
└── Day 5: 測試 + 修復
```

### 第二階段：編輯器強化（2 週）

```
Week 3:
├── Day 1-2: 命令面板（Cmd+K）
├── Day 3-4: 右側配置面板
└── Day 5: 節點拖拽面板

Week 4:
├── Day 1-2: 執行狀態動畫
├── Day 3: 小地圖
├── Day 4-5: 整合測試
```

### 第三階段：細節打磨（1 週）

```
Week 5:
├── Day 1: 微動畫系統
├── Day 2: 快捷鍵系統
├── Day 3: 可存取性檢查
├── Day 4: 響應式調整
└── Day 5: 最終測試 + 文件
```

---

## 技術實現方案

### 方案 A：漸進式升級（推薦）

保留 Ant Design，透過 CSS 變數和主題覆蓋實現新設計。

**優點：** 風險低、工作量適中
**缺點：** 部分元件難以完全自訂

```tsx
// 使用 Ant Design ConfigProvider 自訂主題
<ConfigProvider
  theme={{
    algorithm: theme.darkAlgorithm,
    token: {
      colorPrimary: '#6366F1',
      fontFamily: "'Plus Jakarta Sans', system-ui",
      borderRadius: 8,
    },
    components: {
      Button: { borderRadius: 8 },
      Card: { borderRadius: 12 },
    },
  }}
>
  <App />
</ConfigProvider>
```

### 方案 B：混合方案

核心 UI（側邊欄、節點、配置面板）使用自訂元件 + Tailwind，表格等複雜元件保留 Ant Design。

**優點：** 設計自由度高
**缺點：** 工作量較大

### 方案 C：完全重寫

使用 shadcn/ui + Tailwind 完全重寫 UI。

**優點：** 最高自訂度
**缺點：** 工作量最大、風險高

---

## 附錄：設計參考

### 競品參考
- [n8n](https://n8n.io) - 流程編輯器
- [Retool Workflows](https://retool.com/products/workflows) - 企業工作流
- [Linear](https://linear.app) - 深色主題參考
- [Vercel Dashboard](https://vercel.com) - 現代 SaaS 設計

### 資源
- [Heroicons](https://heroicons.com) - SVG 圖示
- [Lucide](https://lucide.dev) - 圖示庫
- [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans) - 字體

---

*由 UI/UX Pro Max Skill 協助生成 | 2026-02-03*
