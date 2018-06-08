from robot.api.deco import keyword
from selenium import webdriver
from SeleniumLibrary import SeleniumLibrary


class CustomSeleniumLibrary(SeleniumLibrary):

    @keyword
    def open_custom_browser(self, url, browser="firefox", remote_url=False):
        if "chrome" in browser.lower():
            options = webdriver.ChromeOptions()
            options.add_argument("--start-maximized")
            options.add_argument("--disable-web-security")
            options.add_argument("--allow-running-insecure-content")
            options.add_argument("--safebrowsing-disable-extension-blacklist")
            options.add_argument("--safebrowsing-disable-download-protection")
            options.add_argument("--dns-prefetch-disable")
            if browser.lower() == "headlesschrome":
                options.add_argument("--headless")
                options.add_argument("--disable-gpu")
                options.add_argument("--window-size=1400,1200")
            prefs = {'safebrowsing.enabled': True,
                     'credentials_enable_service': False,
                     'profile.password_manager_enabled': False}
            options.add_experimental_option("prefs", prefs)
            if remote_url:
                self.open_browser(url, browser, None, remote_url, options.to_capabilities())
            else:
                self.create_webdriver('Chrome', desired_capabilities=options.to_capabilities())
                self.go_to(url)
        else:
            self.open_browser(url, browser, None, remote_url)
