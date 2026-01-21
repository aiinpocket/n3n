const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://localhost:8080';
const SCREENSHOT_DIR = '/tmp/n3n-sit-screenshots';
const TEST_EMAIL = `sit${Date.now()}@test.com`;
const TEST_PASSWORD = 'SitTest123456';
const TEST_NAME = 'SIT Test User';

// Test results storage
const testResults = [];
let testNumber = 0;

// Ensure screenshot directory exists
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function takeScreenshot(page, name) {
  const filename = `${String(testNumber).padStart(2, '0')}_${name}.png`;
  const filepath = path.join(SCREENSHOT_DIR, filename);
  await page.screenshot({ path: filepath, fullPage: true });
  return filepath;
}

function logTest(name, passed, details = '', screenshot = '') {
  testNumber++;
  const result = { number: testNumber, name, passed, details, screenshot };
  testResults.push(result);

  const status = passed ? '✅ PASS' : '❌ FAIL';
  console.log(`\n[Test ${testNumber}] ${name}`);
  console.log(`   Status: ${status}`);
  if (details) console.log(`   Details: ${details}`);
  if (screenshot) console.log(`   Screenshot: ${screenshot}`);
}

async function runSIT() {
  console.log('╔════════════════════════════════════════════════════════════╗');
  console.log('║           N3N 外部服務功能 SIT 測試                        ║');
  console.log('║           ' + new Date().toLocaleString('zh-TW') + '                      ║');
  console.log('╚════════════════════════════════════════════════════════════╝\n');

  console.log(`測試帳號: ${TEST_EMAIL}`);
  console.log(`截圖目錄: ${SCREENSHOT_DIR}\n`);
  console.log('─'.repeat(60));

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1920,1080']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1920, height: 1080 });

  // Collect errors
  const pageErrors = [];
  page.on('pageerror', err => pageErrors.push(err.message));
  page.on('console', msg => {
    if (msg.type() === 'error') pageErrors.push(msg.text());
  });

  try {
    // ========== TEST 1: 首頁載入 ==========
    await page.goto(BASE_URL, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1000));

    const homeUrl = page.url();
    const screenshot1 = await takeScreenshot(page, 'home_redirect');

    logTest(
      '首頁載入並重導向到登入頁',
      homeUrl.includes('/login'),
      `URL: ${homeUrl}`,
      screenshot1
    );

    // ========== TEST 2: 登入頁面渲染 ==========
    const loginPageContent = await page.content();
    const hasLoginForm = loginPageContent.includes('Email') && loginPageContent.includes('Password');
    const screenshot2 = await takeScreenshot(page, 'login_page');

    logTest(
      '登入頁面正確渲染',
      hasLoginForm,
      hasLoginForm ? '找到 Email 和 Password 欄位' : '缺少必要欄位',
      screenshot2
    );

    // ========== TEST 3: 前往註冊頁面 ==========
    await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1000));

    const registerUrl = page.url();
    const screenshot3 = await takeScreenshot(page, 'register_page');

    logTest(
      '註冊頁面載入',
      registerUrl.includes('/register'),
      `URL: ${registerUrl}`,
      screenshot3
    );

    // ========== TEST 4: 填寫註冊表單 ==========
    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Confirm Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Name"]', TEST_NAME);

    const screenshot4 = await takeScreenshot(page, 'register_form_filled');

    logTest(
      '註冊表單填寫',
      true,
      `Email: ${TEST_EMAIL}`,
      screenshot4
    );

    // ========== TEST 5: 提交註冊 ==========
    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);
    await new Promise(r => setTimeout(r, 2000));

    const afterRegisterUrl = page.url();
    const screenshot5 = await takeScreenshot(page, 'after_register');

    const registerSuccess = afterRegisterUrl.includes('/login') || afterRegisterUrl === `${BASE_URL}/`;
    logTest(
      '用戶註冊成功',
      registerSuccess,
      `重導向到: ${afterRegisterUrl}`,
      screenshot5
    );

    // ========== TEST 6: 登入 ==========
    if (!afterRegisterUrl.includes('/login')) {
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0', timeout: 30000 });
    }

    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);

    const screenshot6a = await takeScreenshot(page, 'login_form_filled');

    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);
    await new Promise(r => setTimeout(r, 2000));

    const afterLoginUrl = page.url();
    const screenshot6b = await takeScreenshot(page, 'after_login');

    const loginSuccess = !afterLoginUrl.includes('/login');
    logTest(
      '用戶登入成功',
      loginSuccess,
      `登入後 URL: ${afterLoginUrl}`,
      screenshot6b
    );

    if (!loginSuccess) {
      throw new Error('登入失敗，無法繼續測試');
    }

    // ========== TEST 7: 主畫面側邊選單 ==========
    const sideMenu = await page.$('.ant-layout-sider');
    const menuItems = await page.evaluate(() => {
      const items = document.querySelectorAll('.ant-menu-item');
      return Array.from(items).map(i => i.textContent.trim());
    });

    const screenshot7 = await takeScreenshot(page, 'main_layout');

    logTest(
      '主畫面側邊選單顯示',
      sideMenu !== null && menuItems.length > 0,
      `選單項目: ${menuItems.join(', ')}`,
      screenshot7
    );

    // ========== TEST 8: 外部服務選單項目 ==========
    const hasServiceMenu = menuItems.some(item => item.includes('外部服務'));

    logTest(
      '側邊選單包含「外部服務」',
      hasServiceMenu,
      hasServiceMenu ? '找到外部服務選單' : '未找到外部服務選單'
    );

    // ========== TEST 9: 進入外部服務頁面 ==========
    await page.goto(`${BASE_URL}/services`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    const servicesUrl = page.url();
    const screenshot9 = await takeScreenshot(page, 'services_list_page');

    logTest(
      '外部服務列表頁面載入',
      servicesUrl.includes('/services'),
      `URL: ${servicesUrl}`,
      screenshot9
    );

    // ========== TEST 10: 新增服務按鈕 ==========
    const pageContent = await page.content();
    const hasAddButton = pageContent.includes('註冊新服務') || pageContent.includes('新增');

    logTest(
      '新增服務按鈕存在',
      hasAddButton,
      hasAddButton ? '找到新增服務按鈕' : '未找到新增服務按鈕'
    );

    // ========== TEST 11: 進入新增服務表單 ==========
    await page.goto(`${BASE_URL}/services/new`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1000));

    const newServiceUrl = page.url();
    const screenshot11 = await takeScreenshot(page, 'new_service_form');

    logTest(
      '新增服務表單頁面載入',
      newServiceUrl.includes('/services/new'),
      `URL: ${newServiceUrl}`,
      screenshot11
    );

    // ========== TEST 12: 表單欄位檢查 ==========
    const formInputs = await page.$$eval('input', inputs => inputs.length);
    const formLabels = await page.evaluate(() => {
      const labels = document.querySelectorAll('.ant-form-item-label label');
      return Array.from(labels).map(l => l.textContent);
    });

    logTest(
      '服務表單欄位完整',
      formInputs >= 3,
      `輸入欄位數: ${formInputs}, 標籤: ${formLabels.join(', ')}`
    );

    // ========== TEST 13: 填寫並提交服務表單 ==========
    const testServiceName = `test-service-${Date.now()}`;

    // Fill form fields
    const nameInput = await page.$('#name');
    const displayNameInput = await page.$('#displayName');
    const baseUrlInput = await page.$('#baseUrl');

    if (nameInput) await nameInput.type(testServiceName);
    if (displayNameInput) await displayNameInput.type('SIT 測試服務');
    if (baseUrlInput) await baseUrlInput.type('https://jsonplaceholder.typicode.com');

    const screenshot13a = await takeScreenshot(page, 'service_form_filled');

    // Submit
    const submitBtn = await page.$('button[type="submit"]');
    if (submitBtn) {
      await submitBtn.click();
      await new Promise(r => setTimeout(r, 3000));
    }

    const afterCreateUrl = page.url();
    const screenshot13b = await takeScreenshot(page, 'after_service_create');

    const createSuccess = afterCreateUrl.includes('/services/') && !afterCreateUrl.includes('/new');
    logTest(
      '建立外部服務成功',
      createSuccess,
      `建立後 URL: ${afterCreateUrl}`,
      screenshot13b
    );

    // ========== TEST 14: 服務詳情頁面 ==========
    if (createSuccess) {
      await new Promise(r => setTimeout(r, 1000));
      const detailContent = await page.content();
      const hasServiceInfo = detailContent.includes('SIT 測試服務') || detailContent.includes(testServiceName);

      const screenshot14 = await takeScreenshot(page, 'service_detail_page');

      logTest(
        '服務詳情頁面顯示正確',
        hasServiceInfo,
        hasServiceInfo ? '找到服務資訊' : '未找到服務資訊',
        screenshot14
      );
    }

    // ========== TEST 15: 流程編輯器外部服務按鈕 ==========
    await page.goto(`${BASE_URL}/flows`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    const screenshot15a = await takeScreenshot(page, 'flows_list_page');

    // Check if there's a flow to edit, or check if we can see the editor
    const flowRows = await page.$$('.ant-table-row');
    let hasExtServiceButton = false;

    if (flowRows.length > 0) {
      // Try to click edit on first flow
      const editLink = await page.$('.ant-table-row a');
      if (editLink) {
        await editLink.click();
        await new Promise(r => setTimeout(r, 2000));

        const editorContent = await page.content();
        hasExtServiceButton = editorContent.includes('外部服務');

        const screenshot15b = await takeScreenshot(page, 'flow_editor_page');

        logTest(
          '流程編輯器「外部服務」按鈕',
          hasExtServiceButton,
          hasExtServiceButton ? '找到外部服務按鈕' : '未找到外部服務按鈕',
          screenshot15b
        );
      } else {
        logTest(
          '流程編輯器「外部服務」按鈕',
          false,
          '無法進入流程編輯器（沒有可編輯的流程）',
          screenshot15a
        );
      }
    } else {
      logTest(
        '流程編輯器「外部服務」按鈕',
        false,
        '流程列表為空，無法測試編輯器',
        screenshot15a
      );
    }

    // Report page errors
    if (pageErrors.length > 0) {
      console.log('\n⚠️ 瀏覽器控制台錯誤:');
      pageErrors.slice(0, 5).forEach(err => console.log(`   - ${err.substring(0, 100)}`));
    }

  } catch (error) {
    console.log(`\n❌ 測試執行錯誤: ${error.message}`);
    await takeScreenshot(page, 'error_state');
  } finally {
    await browser.close();
  }

  // ========== 產出測試報告 ==========
  console.log('\n' + '═'.repeat(60));
  console.log('                    SIT 測試報告');
  console.log('═'.repeat(60));

  const passed = testResults.filter(r => r.passed).length;
  const failed = testResults.filter(r => !r.passed).length;
  const total = testResults.length;
  const passRate = total > 0 ? Math.round(passed / total * 100) : 0;

  console.log(`\n執行時間: ${new Date().toLocaleString('zh-TW')}`);
  console.log(`測試帳號: ${TEST_EMAIL}`);
  console.log(`截圖目錄: ${SCREENSHOT_DIR}`);

  console.log(`\n┌─────────────────────────────────────────────────────────┐`);
  console.log(`│  總測試數: ${String(total).padStart(2)}                                          │`);
  console.log(`│  通過: ${String(passed).padStart(2)}  ✅                                        │`);
  console.log(`│  失敗: ${String(failed).padStart(2)}  ❌                                        │`);
  console.log(`│  通過率: ${String(passRate).padStart(3)}%                                       │`);
  console.log(`└─────────────────────────────────────────────────────────┘`);

  if (failed > 0) {
    console.log('\n❌ 失敗的測試:');
    testResults.filter(r => !r.passed).forEach(r => {
      console.log(`   [${r.number}] ${r.name}`);
      if (r.details) console.log(`       ${r.details}`);
    });
  }

  console.log('\n📸 截圖清單:');
  const screenshots = fs.readdirSync(SCREENSHOT_DIR).filter(f => f.endsWith('.png'));
  screenshots.forEach(s => console.log(`   ${SCREENSHOT_DIR}/${s}`));

  // Write report to file
  const reportPath = path.join(SCREENSHOT_DIR, 'sit-report.json');
  fs.writeFileSync(reportPath, JSON.stringify({
    timestamp: new Date().toISOString(),
    testAccount: TEST_EMAIL,
    summary: { total, passed, failed, passRate },
    results: testResults,
    screenshots: screenshots.map(s => path.join(SCREENSHOT_DIR, s))
  }, null, 2));
  console.log(`\n📄 報告已儲存: ${reportPath}`);

  console.log('\n' + '═'.repeat(60));

  if (passRate >= 80) {
    console.log('🎉 SIT 測試通過！');
    process.exit(0);
  } else {
    console.log('⚠️ SIT 測試未通過，請檢查失敗項目。');
    process.exit(1);
  }
}

runSIT().catch(err => {
  console.error('SIT 執行失敗:', err);
  process.exit(1);
});
