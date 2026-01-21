const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://localhost:8080';
const SCREENSHOT_DIR = '/tmp/n3n-sit-screenshots-v2';
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
  console.log('║     N3N 認證管理與流程分享功能 SIT 測試 V2                 ║');
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

    // ========== TEST 2: 註冊用戶 ==========
    await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1000));

    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Confirm Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Name"]', TEST_NAME);

    const screenshot2a = await takeScreenshot(page, 'register_form');

    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);
    await new Promise(r => setTimeout(r, 2000));

    const afterRegisterUrl = page.url();
    logTest(
      '用戶註冊成功',
      afterRegisterUrl.includes('/login') || afterRegisterUrl === `${BASE_URL}/`,
      `重導向到: ${afterRegisterUrl}`,
      screenshot2a
    );

    // ========== TEST 3: 登入 ==========
    if (!afterRegisterUrl.includes('/login')) {
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0', timeout: 30000 });
    }

    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);

    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);
    await new Promise(r => setTimeout(r, 2000));

    const afterLoginUrl = page.url();
    const screenshot3 = await takeScreenshot(page, 'after_login');

    const loginSuccess = !afterLoginUrl.includes('/login');
    logTest(
      '用戶登入成功',
      loginSuccess,
      `登入後 URL: ${afterLoginUrl}`,
      screenshot3
    );

    if (!loginSuccess) {
      throw new Error('登入失敗，無法繼續測試');
    }

    // ========== TEST 4: 側邊選單檢查 ==========
    const menuItems = await page.evaluate(() => {
      const items = document.querySelectorAll('.ant-menu-item');
      return Array.from(items).map(i => i.textContent.trim());
    });

    const screenshot4 = await takeScreenshot(page, 'main_menu');

    const hasCredentialMenu = menuItems.some(item => item.includes('認證管理'));
    logTest(
      '側邊選單包含「認證管理」',
      hasCredentialMenu,
      `選單項目: ${menuItems.join(', ')}`,
      screenshot4
    );

    // ========== TEST 5: 進入認證管理頁面 ==========
    await page.goto(`${BASE_URL}/credentials`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    const credentialsUrl = page.url();
    const screenshot5 = await takeScreenshot(page, 'credentials_page');

    logTest(
      '認證管理頁面載入',
      credentialsUrl.includes('/credentials'),
      `URL: ${credentialsUrl}`,
      screenshot5
    );

    // ========== TEST 6: 新增認證按鈕存在 ==========
    const pageContent = await page.content();
    const hasAddCredentialButton = pageContent.includes('新增認證');

    logTest(
      '新增認證按鈕存在',
      hasAddCredentialButton,
      hasAddCredentialButton ? '找到新增認證按鈕' : '未找到新增認證按鈕'
    );

    // ========== TEST 7: 點擊新增認證按鈕 ==========
    const addButton = await page.$('button');
    if (addButton) {
      await addButton.click();
      await new Promise(r => setTimeout(r, 1000));
    }

    const modalVisible = await page.$('.ant-modal');
    const screenshot7 = await takeScreenshot(page, 'credential_modal');

    logTest(
      '新增認證對話框顯示',
      modalVisible !== null,
      modalVisible ? '對話框已打開' : '對話框未顯示',
      screenshot7
    );

    // Close modal
    const closeButton = await page.$('.ant-modal-close');
    if (closeButton) {
      await closeButton.click();
      await new Promise(r => setTimeout(r, 500));
    }

    // ========== TEST 8: 安全提示顯示 ==========
    const hasSecurityTip = pageContent.includes('AES-256') || pageContent.includes('加密');

    logTest(
      '認證頁面顯示安全提示',
      hasSecurityTip,
      hasSecurityTip ? '找到 AES-256 加密提示' : '未找到安全提示'
    );

    // ========== TEST 9: 進入外部服務頁面 ==========
    await page.goto(`${BASE_URL}/services`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    const servicesUrl = page.url();
    const screenshot9 = await takeScreenshot(page, 'services_page');

    logTest(
      '外部服務列表頁面載入',
      servicesUrl.includes('/services'),
      `URL: ${servicesUrl}`,
      screenshot9
    );

    // ========== TEST 10: 進入新增服務表單 ==========
    await page.goto(`${BASE_URL}/services/new`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1000));

    const newServiceUrl = page.url();
    const screenshot10 = await takeScreenshot(page, 'new_service_form');

    logTest(
      '新增服務表單頁面載入',
      newServiceUrl.includes('/services/new'),
      `URL: ${newServiceUrl}`,
      screenshot10
    );

    // ========== TEST 11: 認證選擇欄位存在 ==========
    const serviceFormContent = await page.content();
    const hasCredentialField = serviceFormContent.includes('選擇認證') || serviceFormContent.includes('credentialId');

    logTest(
      '外部服務表單包含認證選擇欄位',
      hasCredentialField,
      hasCredentialField ? '找到認證選擇欄位' : '未找到認證選擇欄位'
    );

    // ========== TEST 12: 安全提示在服務表單 ==========
    const hasServiceSecurityTip = serviceFormContent.includes('安全提示') || serviceFormContent.includes('AES-256');

    logTest(
      '服務表單顯示安全提示',
      hasServiceSecurityTip,
      hasServiceSecurityTip ? '找到安全提示' : '未找到安全提示'
    );

    // ========== TEST 13: 建立新認證按鈕 ==========
    const hasCreateCredentialLink = serviceFormContent.includes('建立新認證');
    const screenshot13 = await takeScreenshot(page, 'service_form_credential');

    logTest(
      '服務表單包含建立新認證連結',
      hasCreateCredentialLink,
      hasCreateCredentialLink ? '找到建立新認證連結' : '未找到建立新認證連結',
      screenshot13
    );

    // ========== TEST 14: 填寫並提交服務表單 ==========
    const testServiceName = `test-service-${Date.now()}`;

    const nameInput = await page.$('#name');
    const displayNameInput = await page.$('#displayName');
    const baseUrlInput = await page.$('#baseUrl');

    if (nameInput) await nameInput.type(testServiceName);
    if (displayNameInput) await displayNameInput.type('SIT 測試服務');
    if (baseUrlInput) await baseUrlInput.type('https://jsonplaceholder.typicode.com');

    const screenshot14a = await takeScreenshot(page, 'service_form_filled');

    const submitBtn = await page.$('button[type="submit"]');
    if (submitBtn) {
      await submitBtn.click();
      await new Promise(r => setTimeout(r, 3000));
    }

    const afterCreateUrl = page.url();
    const screenshot14b = await takeScreenshot(page, 'after_service_create');

    const createSuccess = afterCreateUrl.includes('/services/') && !afterCreateUrl.includes('/new');
    logTest(
      '建立外部服務成功',
      createSuccess,
      `建立後 URL: ${afterCreateUrl}`,
      screenshot14b
    );

    // ========== TEST 15: 流程列表頁面 ==========
    await page.goto(`${BASE_URL}/flows`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    const flowsUrl = page.url();
    const screenshot15 = await takeScreenshot(page, 'flows_page');

    logTest(
      '流程列表頁面載入',
      flowsUrl.includes('/flows') || flowsUrl === `${BASE_URL}/`,
      `URL: ${flowsUrl}`,
      screenshot15
    );

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
  console.log('                    SIT 測試報告 V2');
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
  const reportPath = path.join(SCREENSHOT_DIR, 'sit-report-v2.json');
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
