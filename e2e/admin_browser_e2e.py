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
        page.get_by_text("数据源", exact=True).first.click()
        page.get_by_text("接入源健康度").wait_for()
        assert page.evaluate("localStorage.getItem('campusmind-admin-session')") is None
        page.get_by_text("Version History", exact=True).wait_for()
        page.locator(".history-entry").first.wait_for()
        page.screenshot(path=str(Path(__file__).with_name("admin_browser_e2e.png")), full_page=True)
        browser.close()


if __name__ == "__main__":
    main()
