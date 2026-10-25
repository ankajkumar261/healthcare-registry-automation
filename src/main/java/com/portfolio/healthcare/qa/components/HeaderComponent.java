package com.portfolio.healthcare.qa.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.pages.auth.LoginPage;

/**
 * The application header's account menu (a <b>Component Object</b>).
 *
 * <p>Component objects model a reusable UI fragment that appears across many
 * pages (here, the header present on every authenticated screen), so page
 * objects compose components instead of duplicating the same locators. The
 * account menu is opened via its avatar button (aria-label "Open account menu")
 * and exposes "Sign Out".</p>
 */
public class HeaderComponent {

    private final Page page;

    public HeaderComponent(Page page) {
        this.page = page;
    }

    private Locator accountMenuTrigger() {
        return page.getByLabel("Open account menu");
    }

    private Locator signOutItem() {
        return page.getByText("Sign Out");
    }

    public void openAccountMenu() {
        accountMenuTrigger().click();
    }

    /**
     * Opens the account menu, clicks Sign Out, waits for the redirect back to
     * the login page, and returns a fresh {@link LoginPage}.
     */
    public LoginPage logout() {
        openAccountMenu();
        signOutItem().click();
        page.waitForURL(url -> url.contains("/login"));
        return new LoginPage(page);
    }
}
