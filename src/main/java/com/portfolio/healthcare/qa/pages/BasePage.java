package com.portfolio.healthcare.qa.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.healthcare.qa.config.ConfigManager;

/**
 * Generic superclass for every page object.
 *
 * <p>Centralises the interaction + smart-wait helpers so concrete pages stop
 * hand-rolling {@code waitForURL}/{@code isEnabled} guards (DRY). Every wait is
 * <b>explicit and bounded</b> by the configured timeout &mdash; never a
 * {@code Thread.sleep}. Playwright's own actions (click, fill) already auto-wait
 * for actionability, so these helpers add only the higher-level waits Playwright
 * doesn't infer (URL changes, optional-element visibility).</p>
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Default explicit-wait budget in milliseconds, from config. */
    protected int timeoutMs() {
        return ConfigManager.config().timeoutMs();
    }

    /** Fills an input (Playwright auto-waits for it to be editable). */
    protected void type(Locator locator, String text) {
        locator.fill(text);
    }

    /** Clicks only if the element is enabled; a disabled control is a no-op. */
    protected void clickIfEnabled(Locator locator) {
        if (locator.isEnabled()) {
            locator.click();
        }
    }

    /** True if the locator matches at least one element and the first is visible. */
    protected boolean isVisible(Locator locator) {
        return locator.count() > 0 && locator.first().isVisible();
    }

    /** Waits for the locator to become visible, bounded by the config timeout. */
    protected void waitUntilVisible(Locator locator) {
        locator.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs()));
    }

    /**
     * Waits for the URL to no longer contain {@code fragment}. Returns
     * {@code true} if it changed within the timeout, {@code false} otherwise
     * (instead of throwing) so callers can assert on the boolean.
     */
    protected boolean waitForUrlToLeave(String fragment) {
        try {
            page.waitForURL(url -> !url.contains(fragment),
                    new Page.WaitForURLOptions().setTimeout(timeoutMs()));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /** The current page URL. */
    public String currentUrl() {
        return page.url();
    }
}
