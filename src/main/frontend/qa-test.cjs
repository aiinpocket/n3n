const puppeteer = require('puppeteer');

const BASE_URL = 'http://localhost:8080';
const TEST_EMAIL = `qatest${Date.now()}@test.com`;
const TEST_PASSWORD = 'QaTest123456';
const TEST_NAME = 'QA Test User';

async function runTests() {
  console.log('🚀 Starting QA Browser Tests...\n');

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  let passed = 0;
  let failed = 0;

  // Collect console errors
  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  page.on('pageerror', err => {
    consoleErrors.push(err.message);
  });

  try {
    // Test 1: Home page loads and redirects to login
    console.log('📋 Test 1: Home page loads');
    await page.goto(BASE_URL, { waitUntil: 'networkidle0', timeout: 30000 });
    const url1 = page.url();
    if (url1.includes('/login')) {
      console.log('   ✅ Redirected to login page');
      passed++;
    } else {
      console.log(`   ❌ Expected redirect to /login, got: ${url1}`);
      failed++;
    }

    // Test 2: Login page renders correctly
    console.log('📋 Test 2: Login page renders');
    const loginTitle = await page.$eval('h2', el => el.textContent).catch(() => null);
    if (loginTitle && loginTitle.includes('N3N')) {
      console.log('   ✅ Login page title found');
      passed++;
    } else {
      console.log('   ❌ Login page title not found');
      failed++;
    }

    // Check for email input
    const emailInput = await page.$('input[placeholder="Email"]');
    if (emailInput) {
      console.log('   ✅ Email input field found');
      passed++;
    } else {
      console.log('   ❌ Email input field not found');
      failed++;
    }

    // Test 3: Navigate to register page
    console.log('📋 Test 3: Register page');
    await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle0', timeout: 30000 });
    const registerTitle = await page.$eval('h2', el => el.textContent).catch(() => null);
    if (registerTitle) {
      console.log('   ✅ Register page loaded');
      passed++;
    } else {
      console.log('   ❌ Register page failed to load');
      failed++;
    }

    // Test 4: Fill and submit registration form
    console.log('📋 Test 4: User registration');
    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Confirm Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Name"]', TEST_NAME);

    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);

    // Wait a bit for any redirect
    await new Promise(r => setTimeout(r, 2000));

    const afterRegisterUrl = page.url();
    console.log(`   Current URL after register: ${afterRegisterUrl}`);

    if (afterRegisterUrl.includes('/login') || afterRegisterUrl === `${BASE_URL}/`) {
      console.log('   ✅ Registration successful (redirected)');
      passed++;
    } else {
      // Check for error message
      const errorMsg = await page.$eval('.ant-alert-message', el => el.textContent).catch(() => null);
      if (errorMsg) {
        console.log(`   ⚠️ Registration message: ${errorMsg}`);
      }
      console.log('   ❌ Registration may have failed');
      failed++;
    }

    // Test 5: Login with registered user
    console.log('📋 Test 5: User login');
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0', timeout: 30000 });
    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);

    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);

    await new Promise(r => setTimeout(r, 2000));

    const afterLoginUrl = page.url();
    console.log(`   Current URL after login: ${afterLoginUrl}`);

    if (!afterLoginUrl.includes('/login')) {
      console.log('   ✅ Login successful');
      passed++;

      // Test 6: Main layout renders
      console.log('📋 Test 6: Main layout');
      const siderMenu = await page.$('.ant-layout-sider');
      if (siderMenu) {
        console.log('   ✅ Side menu found');
        passed++;
      } else {
        console.log('   ❌ Side menu not found');
        failed++;
      }

      // Test 7: Navigate to Services page
      console.log('📋 Test 7: Services page');
      await page.goto(`${BASE_URL}/services`, { waitUntil: 'networkidle0', timeout: 30000 });
      await new Promise(r => setTimeout(r, 1000));

      const servicesPageUrl = page.url();
      if (servicesPageUrl.includes('/services')) {
        console.log('   ✅ Services page accessible');
        passed++;

        // Check for "新增服務" button
        const addButton = await page.$eval('button', el => el.textContent).catch(() => null);
        if (addButton) {
          console.log('   ✅ Page has buttons');
          passed++;
        }
      } else {
        console.log('   ❌ Services page not accessible');
        failed++;
      }

      // Test 8: Navigate to Flows page
      console.log('📋 Test 8: Flows page');
      await page.goto(`${BASE_URL}/flows`, { waitUntil: 'networkidle0', timeout: 30000 });
      await new Promise(r => setTimeout(r, 1000));

      const flowsPageUrl = page.url();
      if (flowsPageUrl.includes('/flows') || flowsPageUrl === `${BASE_URL}/`) {
        console.log('   ✅ Flows page accessible');
        passed++;
      } else {
        console.log('   ❌ Flows page not accessible');
        failed++;
      }

    } else {
      console.log('   ❌ Login failed');
      failed++;

      // Check for error message
      const loginError = await page.$eval('.ant-alert-message', el => el.textContent).catch(() => null);
      if (loginError) {
        console.log(`   Error: ${loginError}`);
      }
    }

    // Report console errors
    if (consoleErrors.length > 0) {
      console.log('\n⚠️ Browser Console Errors:');
      consoleErrors.slice(0, 5).forEach(err => console.log(`   - ${err}`));
    }

  } catch (error) {
    console.log(`\n❌ Test Error: ${error.message}`);
    failed++;
  } finally {
    await browser.close();
  }

  // Summary
  console.log('\n' + '='.repeat(50));
  console.log('📊 QA Test Summary');
  console.log('='.repeat(50));
  console.log(`✅ Passed: ${passed}`);
  console.log(`❌ Failed: ${failed}`);
  console.log(`📈 Pass Rate: ${Math.round(passed/(passed+failed)*100)}%`);

  if (failed > 0) {
    console.log('\n⚠️ Some tests failed. Please review the issues above.');
    process.exit(1);
  } else {
    console.log('\n🎉 All tests passed!');
    process.exit(0);
  }
}

runTests().catch(console.error);
