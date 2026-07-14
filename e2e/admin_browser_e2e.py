from pathlib import Path
from playwright.sync_api import sync_playwright


def main() -> None:
    with sync_playwright() as playwright:
        # Reuse the locally installed Edge so CI/dev machines do not need to
        # download a separate Playwright browser binary.
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 1000})
        page.goto("http://localhost:5173", wait_until="domcontentloaded")
        page.get_by_placeholder("admin", exact=True).fill("admin")
        page.get_by_placeholder("默认密码 admin", exact=True).fill("admin")
        page.get_by_role("button", name="登录后台").click()
        page.get_by_text("校园事件审核", exact=True).wait_for()

        # --- Review workspace: queue tab switching ---
        # Default tab should be "待人工" (QUEUE)
        queue_tab = page.get_by_role("button", name="待人工")
        assert queue_tab.is_visible(), "QUEUE tab should be visible"

        # Switch to "全部" (ALL) tab
        all_tab = page.get_by_role("button", name="全部")
        all_tab.click()
        # Wait for event list to render with all items
        page.wait_for_timeout(500)

        # Switch to "已发布" (PUBLISHED) tab
        published_tab = page.get_by_role("button", name="已发布")
        published_tab.click()
        page.wait_for_timeout(500)

        # Switch to "已下线" (OFFLINE) tab
        offline_tab = page.get_by_role("button", name="已下线")
        offline_tab.click()
        page.wait_for_timeout(500)

        # Switch back to ALL for batch operations
        all_tab.click()
        page.wait_for_timeout(500)

        # --- Batch select operations ---
        # Check if there are any events in the list
        event_rows = page.locator(".event-row.workbench-row")
        event_count = event_rows.count()

        if event_count > 0:
            # Click "全选当前列表" checkbox
            select_all = page.locator(".batch-check input[type='checkbox']")
            select_all.click()
            page.wait_for_timeout(300)

            # Verify batch buttons are enabled (not disabled)
            batch_approve = page.get_by_role("button", name="批量通过")
            batch_offline = page.get_by_role("button", name="批量下线")
            assert batch_approve.is_enabled(), "Batch approve button should be enabled after selecting items"
            assert batch_offline.is_enabled(), "Batch offline button should be enabled after selecting items"

            # Verify selection count is shown
            batch_count = page.locator(".batch-count")
            assert batch_count.is_visible(), "Batch count should be visible"

            # Uncheck all by clicking again
            select_all.click()
            page.wait_for_timeout(300)

        # --- Navigate to data sources page ---
        page.get_by_text("数据源", exact=True).first.click()
        page.get_by_text("接入源健康度").wait_for()
        assert page.evaluate("localStorage.getItem('campusmind-admin-session')") is None
        page.get_by_text("Version History", exact=True).wait_for()
        page.locator(".history-entry").first.wait_for()

        # --- Navigate to notification operations page ---
        nav_notifications = page.locator('nav').get_by_text("通知运营", exact=True)
        if nav_notifications.count() > 0:
            nav_notifications.first.click()
            page.wait_for_timeout(1000)
            # Verify notification stats are loaded
            page.get_by_text("投递统计").wait_for(timeout=5000)

        page.screenshot(path=str(Path(__file__).with_name("admin_browser_e2e.png")), full_page=True)
        browser.close()


if __name__ == "__main__":
    main()
