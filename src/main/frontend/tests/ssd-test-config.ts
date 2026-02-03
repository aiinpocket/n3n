/**
 * Screenshot-Driven (SSD) Test Configuration
 *
 * Defines UI/UX test cases based on the design system
 */

export const BASE_URL = 'http://localhost:8080';

export const TEST_CREDENTIALS = {
  email: 'admin@n3n.local',
  password: 'admin123',
};

export const VIEWPORT = {
  desktop: { width: 1920, height: 1080 },
  tablet: { width: 1024, height: 768 },
  mobile: { width: 375, height: 812 },
};

// Design System Colors (Dark Theme Target)
export const DESIGN_SYSTEM = {
  colors: {
    bgPrimary: '#020617',
    bgSecondary: '#0F172A',
    bgElevated: '#1E293B',
    primary: '#6366F1',
    success: '#22C55E',
    warning: '#F59E0B',
    danger: '#EF4444',
    textPrimary: '#F8FAFC',
    textSecondary: '#94A3B8',
  },
  fonts: {
    sans: 'Plus Jakarta Sans',
    mono: 'JetBrains Mono',
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
  },
  borderRadius: {
    sm: 4,
    md: 8,
    lg: 12,
    xl: 16,
  },
};

// UI/UX Test Cases
export interface UITestCase {
  id: string;
  name: string;
  description: string;
  category: 'visual' | 'interaction' | 'accessibility' | 'responsive';
  priority: 'critical' | 'high' | 'medium' | 'low';
  page: string;
  checks: UICheck[];
}

export interface UICheck {
  type: 'screenshot' | 'element' | 'color' | 'font' | 'spacing' | 'interaction';
  selector?: string;
  expected?: string | number;
  tolerance?: number;
}

export const TEST_CASES: UITestCase[] = [
  // ============ LOGIN PAGE ============
  {
    id: 'login-001',
    name: '登入頁面視覺',
    description: '驗證登入頁面整體視覺設計',
    category: 'visual',
    priority: 'critical',
    page: '/login',
    checks: [
      { type: 'screenshot' },
      { type: 'element', selector: '.login-form', expected: 'visible' },
      { type: 'element', selector: 'input[type="email"]', expected: 'visible' },
      { type: 'element', selector: 'input[type="password"]', expected: 'visible' },
    ],
  },
  {
    id: 'login-002',
    name: '登入頁面深色主題',
    description: '驗證登入頁面深色主題配色',
    category: 'visual',
    priority: 'high',
    page: '/login',
    checks: [
      { type: 'color', selector: 'body', expected: DESIGN_SYSTEM.colors.bgPrimary },
      { type: 'color', selector: '.login-form', expected: DESIGN_SYSTEM.colors.bgSecondary },
    ],
  },

  // ============ MAIN LAYOUT ============
  {
    id: 'layout-001',
    name: '側邊欄視覺',
    description: '驗證側邊欄設計和分組',
    category: 'visual',
    priority: 'critical',
    page: '/flows',
    checks: [
      { type: 'screenshot' },
      { type: 'element', selector: '.ant-layout-sider', expected: 'visible' },
      { type: 'element', selector: '.ant-menu', expected: 'visible' },
    ],
  },
  {
    id: 'layout-002',
    name: '側邊欄深色主題',
    description: '驗證側邊欄深色配色',
    category: 'visual',
    priority: 'high',
    page: '/flows',
    checks: [
      { type: 'color', selector: '.ant-layout-sider', expected: DESIGN_SYSTEM.colors.bgSecondary },
    ],
  },
  {
    id: 'layout-003',
    name: '側邊欄收合功能',
    description: '驗證側邊欄收合展開',
    category: 'interaction',
    priority: 'medium',
    page: '/flows',
    checks: [
      { type: 'interaction', selector: '.ant-layout-sider-trigger' },
      { type: 'screenshot' },
    ],
  },

  // ============ FLOW LIST PAGE ============
  {
    id: 'flowlist-001',
    name: '流程列表頁面',
    description: '驗證流程列表整體視覺',
    category: 'visual',
    priority: 'critical',
    page: '/flows',
    checks: [
      { type: 'screenshot' },
      { type: 'element', selector: '.ant-table', expected: 'visible' },
      { type: 'element', selector: 'button', expected: 'visible' },
    ],
  },
  {
    id: 'flowlist-002',
    name: '流程列表表格樣式',
    description: '驗證表格深色主題樣式',
    category: 'visual',
    priority: 'high',
    page: '/flows',
    checks: [
      { type: 'color', selector: '.ant-table', expected: DESIGN_SYSTEM.colors.bgElevated },
    ],
  },

  // ============ FLOW EDITOR ============
  {
    id: 'editor-001',
    name: '流程編輯器整體',
    description: '驗證流程編輯器整體布局',
    category: 'visual',
    priority: 'critical',
    page: '/flows/:id/edit',
    checks: [
      { type: 'screenshot' },
      { type: 'element', selector: '.react-flow', expected: 'visible' },
    ],
  },
  {
    id: 'editor-002',
    name: '流程編輯器畫布',
    description: '驗證畫布深色背景',
    category: 'visual',
    priority: 'high',
    page: '/flows/:id/edit',
    checks: [
      { type: 'color', selector: '.react-flow__background', expected: DESIGN_SYSTEM.colors.bgPrimary },
    ],
  },
  {
    id: 'editor-003',
    name: '節點視覺樣式',
    description: '驗證節點新設計語言',
    category: 'visual',
    priority: 'critical',
    page: '/flows/:id/edit',
    checks: [
      { type: 'screenshot' },
      { type: 'element', selector: '.react-flow__node', expected: 'visible' },
    ],
  },
  {
    id: 'editor-004',
    name: '節點選中狀態',
    description: '驗證節點選中視覺反饋',
    category: 'interaction',
    priority: 'high',
    page: '/flows/:id/edit',
    checks: [
      { type: 'interaction', selector: '.react-flow__node' },
      { type: 'screenshot' },
    ],
  },
  {
    id: 'editor-005',
    name: '配置面板',
    description: '驗證節點配置面板設計',
    category: 'visual',
    priority: 'critical',
    page: '/flows/:id/edit',
    checks: [
      { type: 'screenshot' },
      { type: 'element', selector: '.ant-drawer, .config-panel', expected: 'visible' },
    ],
  },

  // ============ ACCESSIBILITY ============
  {
    id: 'a11y-001',
    name: '色彩對比度',
    description: '驗證文字色彩對比度達 WCAG AA',
    category: 'accessibility',
    priority: 'high',
    page: '/flows',
    checks: [
      { type: 'color', selector: 'body', expected: '4.5:1' }, // contrast ratio
    ],
  },
  {
    id: 'a11y-002',
    name: '焦點狀態可見',
    description: '驗證鍵盤焦點狀態可見',
    category: 'accessibility',
    priority: 'high',
    page: '/flows',
    checks: [
      { type: 'interaction', selector: 'button' },
      { type: 'screenshot' },
    ],
  },

  // ============ RESPONSIVE ============
  {
    id: 'responsive-001',
    name: '平板響應式',
    description: '驗證平板裝置顯示',
    category: 'responsive',
    priority: 'medium',
    page: '/flows',
    checks: [
      { type: 'screenshot' },
    ],
  },
  {
    id: 'responsive-002',
    name: '側邊欄響應式',
    description: '驗證側邊欄在小螢幕自動收合',
    category: 'responsive',
    priority: 'medium',
    page: '/flows',
    checks: [
      { type: 'element', selector: '.ant-layout-sider-collapsed', expected: 'visible' },
    ],
  },

  // ============ INTERACTIONS ============
  {
    id: 'interaction-001',
    name: '按鈕 Hover 效果',
    description: '驗證按鈕 hover 有視覺反饋',
    category: 'interaction',
    priority: 'medium',
    page: '/flows',
    checks: [
      { type: 'interaction', selector: 'button.ant-btn-primary' },
      { type: 'screenshot' },
    ],
  },
  {
    id: 'interaction-002',
    name: '表格行 Hover',
    description: '驗證表格行 hover 效果',
    category: 'interaction',
    priority: 'low',
    page: '/flows',
    checks: [
      { type: 'interaction', selector: '.ant-table-row' },
      { type: 'screenshot' },
    ],
  },
];

// Get test cases by category
export const getTestsByCategory = (category: UITestCase['category']) =>
  TEST_CASES.filter((tc) => tc.category === category);

// Get test cases by priority
export const getTestsByPriority = (priority: UITestCase['priority']) =>
  TEST_CASES.filter((tc) => tc.priority === priority);

// Get critical tests
export const getCriticalTests = () =>
  TEST_CASES.filter((tc) => tc.priority === 'critical');
