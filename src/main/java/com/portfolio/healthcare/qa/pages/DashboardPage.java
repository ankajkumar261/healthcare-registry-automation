package com.portfolio.healthcare.qa.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.healthcare.qa.components.HeaderComponent;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.auth.LoginPage;

/**
 * Post-login landing page (all roles route to {@code /dashboard}).
 *
 * <p>Each role sees a different dashboard, but all share the same "metric card"
 * shape: a label (e.g. {@code "Total Cases"}, {@code "Total Surgeons"},
 * {@code "Total Surgeries"}) sitting above its value. {@link #metricValue(String)}
 * and {@link #metricShowsValue(String, String)} read a card by its label so a
 * test can assert the rendered figure against the backing dashboard API. Metric
 * labels verified live: surgeon (Total Cases, Avg %TWL, Complication Rate),
 * hospital admin (Total Surgeons, Total Cases, Avg %TWL), Registry admin (Total
 * Surgeries, Total Surgeons, Total Hospitals).</p>
 */
public class DashboardPage extends BasePage {

    public DashboardPage(Page page) {
        super(page);
    }

    /**
     * Waits for the post-login redirect to complete. Auth is async, so this is
     * a bounded smart wait for the URL to leave {@code /login}; returns
     * {@code false} on timeout rather than throwing.
     */
    public boolean isLoaded() {
        return waitForUrlToLeave("/login");
    }

    /** The header/account-menu component present on every authenticated page. */
    public HeaderComponent header() {
        return new HeaderComponent(page);
    }

    /** Logs out via the account menu, returning the resulting {@link LoginPage}. */
    public LoginPage logout() {
        return header().logout();
    }

    /**
     * Navigates directly to an admin route and reports whether the RBAC guard
     * blocked access (an "Access Denied" page). Used to prove role isolation.
     */
    public boolean isAccessDeniedAt(String path) {
        page.navigate(ConfigManager.config().appBaseUrl() + path);
        try {
            waitUntilVisible(page.getByText("Access Denied", new Page.GetByTextOptions().setExact(false)));
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** True once the session is invalidated: visiting the dashboard bounces to login. */
    public boolean isSessionInvalidated() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/dashboard");
        return waitForUrlToLeave("/dashboard") && currentUrl().contains("/login");
    }

    // --- Metric cards --------------------------------------------------------
    /**
     * The metric card for a given label: the closest ancestor of the label whose
     * text begins with that label, which scopes the value lookup to that one card
     * (dashboards render many similarly-shaped cards).
     */
    private Locator metricCard(String label) {
        String lit = xpathLiteral(label);
        return page.locator("xpath=//*[normalize-space(text())=" + lit + "]"
                + "/ancestor::div[starts-with(normalize-space(.), " + lit + ")][1]").first();
    }

    /** True if the metric card for {@code label} renders exactly {@code value} (e.g. "8", "289"). */
    public boolean metricShowsValue(String label, String value) {
        try {
            Locator card = metricCard(label);
            waitUntilVisible(card);
            return card.getByText(value, new Locator.GetByTextOptions().setExact(true)).count() > 0;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** True if the metric card for {@code label} is present on the dashboard. */
    public boolean hasMetric(String label) {
        try {
            waitUntilVisible(page.getByText(label, new Page.GetByTextOptions().setExact(true)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** True if the given text (e.g. a hospital or surgeon name) is visible anywhere. */
    public boolean showsText(String text) {
        try {
            waitUntilVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** Quotes a value as an XPath string literal, tolerating embedded quotes. */
    private static String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return "concat('" + value.replace("'", "',\"'\",'") + "')";
    }
}
