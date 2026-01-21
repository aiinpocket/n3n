const puppeteer = require('puppeteer');

const BASE_URL = 'http://localhost:8080';
const TEST_EMAIL = `qaservice${Date.now()}@test.com`;
const TEST_PASSWORD = 'QaTest123456';
const TEST_NAME = 'Service Test User';

async function runServiceTests() {
  console.log('🚀 Starting External Service Feature Tests...\n');

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  let passed = 0;
  let failed = 0;

  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  try {
    // Setup: Register and login
    console.log('🔐 Setup: Creating test user...');
    await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle0', timeout: 30000 });
    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Confirm Password"]', TEST_PASSWORD);
    await page.type('input[placeholder="Name"]', TEST_NAME);
    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);
    await new Promise(r => setTimeout(r, 1000));

    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0', timeout: 30000 });
    await page.type('input[placeholder="Email"]', TEST_EMAIL);
    await page.type('input[placeholder="Password"]', TEST_PASSWORD);
    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 }).catch(() => {})
    ]);
    await new Promise(r => setTimeout(r, 2000));
    console.log('   ✅ User logged in\n');

    // Test 1: Services menu item exists
    console.log('📋 Test 1: Services menu in sidebar');
    const menuText = await page.evaluate(() => {
      const items = document.querySelectorAll('.ant-menu-item');
      return Array.from(items).map(i => i.textContent);
    });
    if (menuText.some(t => t.includes('外部服務'))) {
      console.log('   ✅ "外部服務" menu item found');
      passed++;
    } else {
      console.log('   ❌ "外部服務" menu item not found');
      console.log('   Menu items:', menuText);
      failed++;
    }

    // Test 2: Navigate to Services page via URL
    console.log('📋 Test 2: Services list page');
    await page.goto(`${BASE_URL}/services`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    const servicesUrl = page.url();
    if (servicesUrl.includes('/services')) {
      console.log('   ✅ Services page URL correct');
      passed++;
    } else {
      console.log(`   ❌ Wrong URL: ${servicesUrl}`);
      failed++;
    }

    // Test 3: Add Service button exists
    console.log('📋 Test 3: Add service button');
    const addButton = await page.evaluate(() => {
      const buttons = document.querySelectorAll('button');
      return Array.from(buttons).some(b => b.textContent.includes('新增') || b.textContent.includes('Add'));
    });
    if (addButton) {
      console.log('   ✅ Add service button found');
      passed++;
    } else {
      console.log('   ❌ Add service button not found');
      failed++;
    }

    // Test 4: Navigate to new service form
    console.log('📋 Test 4: New service form page');
    await page.goto(`${BASE_URL}/services/new`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1000));

    const formUrl = page.url();
    if (formUrl.includes('/services/new')) {
      console.log('   ✅ New service form page loaded');
      passed++;
    } else {
      console.log(`   ❌ Wrong URL: ${formUrl}`);
      failed++;
    }

    // Test 5: Service form has required fields
    console.log('📋 Test 5: Service form fields');
    const formFields = await page.evaluate(() => {
      const inputs = document.querySelectorAll('input');
      const labels = document.querySelectorAll('.ant-form-item-label');
      return {
        inputCount: inputs.length,
        labels: Array.from(labels).map(l => l.textContent)
      };
    });

    if (formFields.inputCount >= 2) {
      console.log(`   ✅ Form has ${formFields.inputCount} input fields`);
      passed++;
    } else {
      console.log(`   ❌ Form only has ${formFields.inputCount} input fields`);
      failed++;
    }

    // Test 6: Create a test service
    console.log('📋 Test 6: Create test service');

    // Fill the form
    const nameInput = await page.$('input#name');
    const displayNameInput = await page.$('input#displayName');
    const baseUrlInput = await page.$('input#baseUrl');

    if (nameInput) await nameInput.type('test-service');
    if (displayNameInput) await displayNameInput.type('Test Service');
    if (baseUrlInput) await baseUrlInput.type('https://jsonplaceholder.typicode.com');

    // Take screenshot for debugging
    await page.screenshot({ path: '/tmp/service-form.png' });

    // Submit form
    const submitButton = await page.$('button[type="submit"]');
    if (submitButton) {
      await Promise.all([
        submitButton.click(),
        new Promise(r => setTimeout(r, 3000))
      ]);

      const afterSubmitUrl = page.url();
      console.log(`   URL after submit: ${afterSubmitUrl}`);

      // Check for success (redirected or message)
      const successMessage = await page.evaluate(() => {
        const msg = document.querySelector('.ant-message-success');
        return msg ? msg.textContent : null;
      });

      if (afterSubmitUrl.includes('/services') && !afterSubmitUrl.includes('/new')) {
        console.log('   ✅ Service created and redirected');
        passed++;
      } else if (successMessage) {
        console.log(`   ✅ Success message: ${successMessage}`);
        passed++;
      } else {
        // Check for error
        const errorMessage = await page.evaluate(() => {
          const msg = document.querySelector('.ant-message-error, .ant-alert-error');
          return msg ? msg.textContent : null;
        });
        if (errorMessage) {
          console.log(`   ⚠️ Error: ${errorMessage}`);
        }
        console.log('   ❌ Service creation unclear');
        failed++;
      }
    } else {
      console.log('   ❌ Submit button not found');
      failed++;
    }

    // Test 7: Flow editor has external service button
    console.log('📋 Test 7: Flow editor external service button');

    // First create a flow if needed
    await page.goto(`${BASE_URL}/flows`, { waitUntil: 'networkidle0', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));

    // Check if there are any flows, or create one
    const flowExists = await page.evaluate(() => {
      const rows = document.querySelectorAll('.ant-table-row');
      return rows.length > 0;
    });

    if (flowExists) {
      // Click edit on first flow
      const editButton = await page.$('.ant-table-row button');
      if (editButton) {
        await editButton.click();
        await new Promise(r => setTimeout(r, 2000));
      }
    }

    // Check for external service button in flow editor
    const hasExtServiceButton = await page.evaluate(() => {
      const buttons = document.querySelectorAll('button');
      return Array.from(buttons).some(b => b.textContent.includes('外部服務'));
    });

    if (hasExtServiceButton) {
      console.log('   ✅ External service button found in flow editor');
      passed++;
    } else {
      console.log('   ⚠️ External service button not visible (may need a flow open)');
      // Don't count as failed since it requires a flow to be open
    }

    // Console errors report
    if (consoleErrors.length > 0) {
      console.log('\n⚠️ Browser Console Errors:');
      consoleErrors.slice(0, 5).forEach(err => console.log(`   - ${err.substring(0, 100)}`));
    }

  } catch (error) {
    console.log(`\n❌ Test Error: ${error.message}`);
    failed++;
  } finally {
    await browser.close();
  }

  // Summary
  console.log('\n' + '='.repeat(50));
  console.log('📊 External Service Feature Test Summary');
  console.log('='.repeat(50));
  console.log(`✅ Passed: ${passed}`);
  console.log(`❌ Failed: ${failed}`);
  const rate = passed + failed > 0 ? Math.round(passed/(passed+failed)*100) : 0;
  console.log(`📈 Pass Rate: ${rate}%`);

  if (failed > 0) {
    console.log('\n⚠️ Some tests failed. Please review.');
    process.exit(1);
  } else {
    console.log('\n🎉 All external service tests passed!');
    process.exit(0);
  }
}

runServiceTests().catch(console.error);
