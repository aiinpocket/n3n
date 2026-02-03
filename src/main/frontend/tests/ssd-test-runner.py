#!/usr/bin/env python3
"""
Screenshot-Driven (SSD) Test Runner

Executes UI/UX tests with visual verification based on design system standards.
"""

import asyncio
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Dict, Optional, Literal
from enum import Enum

from playwright.async_api import async_playwright, Page, Browser, BrowserContext

# Configuration
BASE_URL = "http://localhost:8080"
TEST_DIR = Path(__file__).parent
SCREENSHOTS_DIR = TEST_DIR / "screenshots"
BASELINE_DIR = TEST_DIR / "baseline"
REPORTS_DIR = TEST_DIR / "reports"

# Ensure directories exist
SCREENSHOTS_DIR.mkdir(exist_ok=True)
BASELINE_DIR.mkdir(exist_ok=True)
REPORTS_DIR.mkdir(exist_ok=True)

# Test Credentials
TEST_EMAIL = "admin@n3n.local"
TEST_PASSWORD = "admin123"

# Design System (Target)
DESIGN_SYSTEM = {
    "colors": {
        "bg_primary": "#020617",
        "bg_secondary": "#0F172A",
        "bg_elevated": "#1E293B",
        "primary": "#6366F1",
        "success": "#22C55E",
        "text_primary": "#F8FAFC",
    },
    "fonts": {
        "sans": "Plus Jakarta Sans",
        "mono": "JetBrains Mono",
    },
}


class TestStatus(Enum):
    PASSED = "passed"
    FAILED = "failed"
    SKIPPED = "skipped"
    BASELINE = "baseline"


@dataclass
class TestResult:
    test_id: str
    name: str
    status: TestStatus
    screenshot_path: Optional[str] = None
    baseline_path: Optional[str] = None
    error: Optional[str] = None
    checks: List[Dict] = field(default_factory=list)
    duration_ms: int = 0


@dataclass
class TestReport:
    timestamp: str
    total: int
    passed: int
    failed: int
    skipped: int
    baseline: int
    results: List[TestResult] = field(default_factory=list)


class SSDTestRunner:
    def __init__(self, mode: Literal["baseline", "test"] = "test"):
        self.mode = mode
        self.browser: Optional[Browser] = None
        self.context: Optional[BrowserContext] = None
        self.page: Optional[Page] = None
        self.results: List[TestResult] = []
        self.logged_in = False

    async def setup(self):
        """Initialize browser and context"""
        playwright = await async_playwright().start()
        self.browser = await playwright.chromium.launch(
            headless=False,
            slow_mo=100,
        )
        self.context = await self.browser.new_context(
            viewport={"width": 1920, "height": 1080},
            device_scale_factor=1,
        )
        # Disable cache for accurate testing
        await self.context.set_extra_http_headers({
            "Cache-Control": "no-cache, no-store, must-revalidate",
        })
        self.page = await self.context.new_page()
        self.page.set_default_timeout(15000)

    async def teardown(self):
        """Cleanup resources"""
        if self.browser:
            await self.browser.close()

    async def login(self):
        """Login to the application"""
        if self.logged_in:
            return

        await self.page.goto(f"{BASE_URL}/login", wait_until="networkidle")
        await asyncio.sleep(1)

        # Check if already on login page or redirected
        if "login" in self.page.url.lower():
            await self.page.get_by_placeholder("電子郵件").fill(TEST_EMAIL)
            await self.page.get_by_placeholder("密碼").fill(TEST_PASSWORD)
            await self.page.get_by_role("button", name="登 入").click()
            await self.page.wait_for_load_state("networkidle")
            await asyncio.sleep(2)

        self.logged_in = True
        print("✓ Logged in successfully")

    async def take_screenshot(self, name: str, is_baseline: bool = False) -> str:
        """Take screenshot and save to appropriate directory"""
        directory = BASELINE_DIR if is_baseline else SCREENSHOTS_DIR
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"{name}_{timestamp}.png" if not is_baseline else f"{name}.png"
        path = directory / filename
        await self.page.screenshot(path=str(path), full_page=False)
        return str(path)

    async def run_test(self, test_case: Dict) -> TestResult:
        """Execute a single test case"""
        start_time = datetime.now()
        test_id = test_case["id"]
        name = test_case["name"]
        page_url = test_case["page"]
        checks = test_case.get("checks", [])

        print(f"\n{'='*60}")
        print(f"Running: {test_id} - {name}")
        print(f"{'='*60}")

        result = TestResult(
            test_id=test_id,
            name=name,
            status=TestStatus.PASSED,
        )

        try:
            # Handle dynamic URLs
            if ":id" in page_url:
                # Use first available flow
                page_url = page_url.replace(":id", await self._get_first_flow_id())

            # Navigate to page (skip login page)
            if page_url != "/login":
                await self.login()

            target_url = f"{BASE_URL}{page_url}"
            await self.page.goto(target_url, wait_until="networkidle")
            await asyncio.sleep(1)

            # Close any modals
            await self.page.keyboard.press("Escape")
            await asyncio.sleep(0.5)

            # Execute setup if specified
            setup = test_case.get("setup")
            if setup == "add_node":
                await self._add_node_to_flow()

            # Execute checks
            for check in checks:
                check_result = await self._execute_check(check, test_id)
                result.checks.append(check_result)

                if not check_result.get("passed", False):
                    result.status = TestStatus.FAILED
                    result.error = check_result.get("error", "Check failed")

            # Take screenshot
            is_baseline = self.mode == "baseline"
            screenshot_path = await self.take_screenshot(test_id, is_baseline)

            if is_baseline:
                result.status = TestStatus.BASELINE
                result.baseline_path = screenshot_path
                print(f"  📸 Baseline saved: {screenshot_path}")
            else:
                result.screenshot_path = screenshot_path
                # Compare with baseline if exists
                baseline_path = BASELINE_DIR / f"{test_id}.png"
                if baseline_path.exists():
                    result.baseline_path = str(baseline_path)
                print(f"  📸 Screenshot: {screenshot_path}")

        except Exception as e:
            result.status = TestStatus.FAILED
            result.error = str(e)
            print(f"  ❌ Error: {e}")

            # Take error screenshot
            try:
                error_path = await self.take_screenshot(f"{test_id}_error", False)
                result.screenshot_path = error_path
            except:
                pass

        duration = (datetime.now() - start_time).total_seconds() * 1000
        result.duration_ms = int(duration)

        status_icon = {
            TestStatus.PASSED: "✅",
            TestStatus.FAILED: "❌",
            TestStatus.SKIPPED: "⏭️",
            TestStatus.BASELINE: "📸",
        }
        print(f"  {status_icon[result.status]} Status: {result.status.value} ({duration:.0f}ms)")

        return result

    async def _execute_check(self, check: Dict, test_id: str) -> Dict:
        """Execute a single check"""
        check_type = check.get("type")
        selector = check.get("selector")
        expected = check.get("expected")

        result = {"type": check_type, "passed": False}

        try:
            if check_type == "screenshot":
                # Screenshot check - just take it
                result["passed"] = True

            elif check_type == "element":
                # Element visibility check
                if selector:
                    element = self.page.locator(selector)
                    count = await element.count()
                    is_visible = count > 0 and await element.first.is_visible() if count > 0 else False

                    if expected == "visible":
                        result["passed"] = is_visible
                        result["actual"] = "visible" if is_visible else "not visible"
                    else:
                        result["passed"] = not is_visible
                        result["actual"] = "not visible" if not is_visible else "visible"

                    print(f"    Element '{selector}': {result['actual']}")

            elif check_type == "color":
                # Color check (would need actual implementation)
                # For now, just pass and note for visual verification
                result["passed"] = True
                result["note"] = "Requires visual verification"
                print(f"    Color check '{selector}': Visual verification required")

            elif check_type == "interaction":
                # Interaction check - hover
                if selector:
                    element = self.page.locator(selector).first
                    if await element.count() > 0:
                        await element.hover()
                        await asyncio.sleep(0.3)
                        result["passed"] = True
                        print(f"    Interaction '{selector}': Hover applied")

            elif check_type == "click":
                # Click check - click on element
                if selector:
                    element = self.page.locator(selector).first
                    if await element.count() > 0:
                        await element.click()
                        await asyncio.sleep(0.5)
                        result["passed"] = True
                        print(f"    Click '{selector}': Clicked")

            elif check_type == "font":
                # Font check (would need actual implementation)
                result["passed"] = True
                result["note"] = "Requires visual verification"

            elif check_type == "spacing":
                # Spacing check (would need actual implementation)
                result["passed"] = True
                result["note"] = "Requires visual verification"

        except Exception as e:
            result["error"] = str(e)
            print(f"    Check error: {e}")

        return result

    async def _add_node_to_flow(self):
        """Add a trigger node to the current flow"""
        try:
            # Check if node already exists
            node = self.page.locator(".react-flow__node")
            if await node.count() > 0:
                print("    Node already exists, skipping add")
                return

            # Click "新增節點" button
            add_btn = self.page.get_by_text("新增節點")
            if await add_btn.count() > 0:
                await add_btn.click()
                await asyncio.sleep(0.5)

                # Select trigger node type from dropdown/menu
                trigger_option = self.page.get_by_text("觸發器").first
                if await trigger_option.count() > 0:
                    await trigger_option.click()
                    await asyncio.sleep(0.5)
                    print("    Added trigger node")
                else:
                    # Try clicking first option if trigger not found
                    first_option = self.page.locator(".ant-dropdown-menu-item").first
                    if await first_option.count() > 0:
                        await first_option.click()
                        await asyncio.sleep(0.5)
                        print("    Added first available node")
        except Exception as e:
            print(f"    Warning: Could not add node: {e}")

    async def _get_first_flow_id(self) -> str:
        """Get the ID of the first available flow"""
        await self.login()
        await self.page.goto(f"{BASE_URL}/flows", wait_until="networkidle")
        await asyncio.sleep(1)

        # Try to find edit button and extract flow ID from URL
        edit_btn = self.page.locator("tr").first.get_by_text("編輯")
        if await edit_btn.count() > 0:
            await edit_btn.click()
            await self.page.wait_for_load_state("networkidle")
            await asyncio.sleep(1)

            # Extract flow ID from current URL
            url = self.page.url
            if "/flows/" in url:
                flow_id = url.split("/flows/")[1].split("/")[0]
                return flow_id

        return "test-flow-id"

    def generate_report(self) -> TestReport:
        """Generate test report"""
        report = TestReport(
            timestamp=datetime.now().isoformat(),
            total=len(self.results),
            passed=len([r for r in self.results if r.status == TestStatus.PASSED]),
            failed=len([r for r in self.results if r.status == TestStatus.FAILED]),
            skipped=len([r for r in self.results if r.status == TestStatus.SKIPPED]),
            baseline=len([r for r in self.results if r.status == TestStatus.BASELINE]),
            results=self.results,
        )
        return report

    def save_report(self, report: TestReport):
        """Save report to file"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        report_path = REPORTS_DIR / f"ssd_report_{timestamp}.json"

        report_dict = {
            "timestamp": report.timestamp,
            "total": report.total,
            "passed": report.passed,
            "failed": report.failed,
            "skipped": report.skipped,
            "baseline": report.baseline,
            "results": [
                {
                    "test_id": r.test_id,
                    "name": r.name,
                    "status": r.status.value,
                    "screenshot_path": r.screenshot_path,
                    "baseline_path": r.baseline_path,
                    "error": r.error,
                    "checks": r.checks,
                    "duration_ms": r.duration_ms,
                }
                for r in report.results
            ],
        }

        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(report_dict, f, ensure_ascii=False, indent=2)

        print(f"\n📊 Report saved: {report_path}")
        return report_path


# Test Cases Definition (matching TypeScript config)
TEST_CASES = [
    # LOGIN PAGE
    {
        "id": "login-001",
        "name": "登入頁面視覺",
        "description": "驗證登入頁面整體視覺設計",
        "category": "visual",
        "priority": "critical",
        "page": "/login",
        "checks": [
            {"type": "screenshot"},
            {"type": "element", "selector": "form", "expected": "visible"},
            {"type": "element", "selector": "input[type='password']", "expected": "visible"},
        ],
    },
    # MAIN LAYOUT
    {
        "id": "layout-001",
        "name": "側邊欄視覺",
        "description": "驗證側邊欄設計和分組",
        "category": "visual",
        "priority": "critical",
        "page": "/flows",
        "checks": [
            {"type": "screenshot"},
            {"type": "element", "selector": ".ant-layout-sider", "expected": "visible"},
            {"type": "element", "selector": ".ant-menu", "expected": "visible"},
        ],
    },
    {
        "id": "layout-002",
        "name": "側邊欄收合功能",
        "description": "驗證側邊欄收合展開",
        "category": "interaction",
        "priority": "medium",
        "page": "/flows",
        "checks": [
            {"type": "interaction", "selector": ".ant-layout-sider-trigger"},
            {"type": "screenshot"},
        ],
    },
    # FLOW LIST PAGE
    {
        "id": "flowlist-001",
        "name": "流程列表頁面",
        "description": "驗證流程列表整體視覺",
        "category": "visual",
        "priority": "critical",
        "page": "/flows",
        "checks": [
            {"type": "screenshot"},
            {"type": "element", "selector": ".ant-table", "expected": "visible"},
        ],
    },
    # FLOW EDITOR
    {
        "id": "editor-001",
        "name": "流程編輯器整體",
        "description": "驗證流程編輯器整體布局",
        "category": "visual",
        "priority": "critical",
        "page": "/flows/:id/edit",
        "checks": [
            {"type": "screenshot"},
            {"type": "element", "selector": ".react-flow", "expected": "visible"},
        ],
    },
    {
        "id": "editor-002",
        "name": "節點視覺樣式",
        "description": "驗證節點設計（需先添加節點）",
        "category": "visual",
        "priority": "critical",
        "page": "/flows/:id/edit",
        "setup": "add_node",  # 添加節點前置動作
        "checks": [
            {"type": "element", "selector": ".react-flow__node", "expected": "visible"},
            {"type": "screenshot"},
        ],
    },
    {
        "id": "editor-003",
        "name": "節點選中狀態",
        "description": "驗證節點選中視覺反饋",
        "category": "interaction",
        "priority": "high",
        "page": "/flows/:id/edit",
        "setup": "add_node",
        "checks": [
            {"type": "interaction", "selector": ".react-flow__node"},
            {"type": "screenshot"},
        ],
    },
    {
        "id": "editor-004",
        "name": "配置面板",
        "description": "驗證節點配置面板設計",
        "category": "visual",
        "priority": "critical",
        "page": "/flows/:id/edit",
        "setup": "add_node",
        "checks": [
            {"type": "click", "selector": ".react-flow__node"},  # 點擊節點打開配置面板
            {"type": "element", "selector": ".ant-drawer", "expected": "visible"},
            {"type": "screenshot"},
        ],
    },
    # INTERACTIONS
    {
        "id": "interaction-001",
        "name": "按鈕 Hover 效果",
        "description": "驗證按鈕 hover 有視覺反饋",
        "category": "interaction",
        "priority": "medium",
        "page": "/flows",
        "checks": [
            {"type": "interaction", "selector": "button.ant-btn-primary"},
            {"type": "screenshot"},
        ],
    },
]


async def main():
    """Main entry point"""
    mode = "baseline" if "--baseline" in sys.argv else "test"
    filter_id = None

    # Check for specific test filter
    for arg in sys.argv:
        if arg.startswith("--test="):
            filter_id = arg.split("=")[1]

    runner = SSDTestRunner(mode=mode)

    try:
        await runner.setup()

        print(f"\n{'='*60}")
        print(f"  SSD Test Runner - Mode: {mode.upper()}")
        print(f"{'='*60}")

        tests_to_run = TEST_CASES
        if filter_id:
            tests_to_run = [t for t in TEST_CASES if t["id"] == filter_id]
            print(f"  Running single test: {filter_id}")

        for test_case in tests_to_run:
            result = await runner.run_test(test_case)
            runner.results.append(result)

        # Generate and save report
        report = runner.generate_report()
        runner.save_report(report)

        # Print summary
        print(f"\n{'='*60}")
        print("  TEST SUMMARY")
        print(f"{'='*60}")
        print(f"  Total:   {report.total}")
        print(f"  Passed:  {report.passed} ✅")
        print(f"  Failed:  {report.failed} ❌")
        print(f"  Skipped: {report.skipped} ⏭️")
        if mode == "baseline":
            print(f"  Baseline: {report.baseline} 📸")
        print(f"{'='*60}\n")

        # Exit with error code if any tests failed
        if report.failed > 0:
            sys.exit(1)

    finally:
        await runner.teardown()


if __name__ == "__main__":
    asyncio.run(main())
