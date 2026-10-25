package com.portfolio.healthcare.qa.pages.dashboards;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The Hospital Benchmarks page at {@code /benchmark} (available to Hospital Admin
 * and Registry Admin).
 *
 * <p>Ranks hospitals against one another in a table and a set of bar charts,
 * driven by native {@code <select>}s: <b>Rank by</b> (metric), <b>Order</b> and
 * <b>Show top</b> ({@code 10 / 25 / 50 / 100}). Backed by
 * {@code GET /api/dashboard/hospital-benchmark}. Selectors verified live.</p>
 *
 * <p><b>Note vs the QA doc:</b> route is {@code /benchmark} (the doc says
 * {@code /dashboards/hospital-benchmark}); the comparison is rendered as ranked
 * bar charts rather than a radar chart.</p>
 */
public class HospitalBenchmarkPage extends BasePage {

    public HospitalBenchmarkPage(Page page) {
        super(page);
    }

    public HospitalBenchmarkPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/benchmark");
        waitUntilVisible(page.getByText("Hospital Benchmarks", new Page.GetByTextOptions().setExact(false)).first());
        waitUntilVisible(rows().first());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator rows() {
        return page.locator("table tbody tr");
    }

    /** The "Show top" select (identified by its numeric options). */
    private Locator showTopSelect() {
        return page.locator("select").filter(new Locator.FilterOptions().setHasText("100")).first();
    }

    // --- Actions -------------------------------------------------------------
    /** Sets the visible-rows cap, e.g. {@code "50"}. */
    public HospitalBenchmarkPage showTop(String label) {
        showTopSelect().selectOption(new SelectOption().setLabel(label));
        page.waitForTimeout(500);
        return this;
    }

    // --- Queries -------------------------------------------------------------
    public int rowCount() {
        return rows().count();
    }

    /** The number of rendered chart SVGs (the ranked bar charts). */
    public int chartCount() {
        return page.locator("svg").count();
    }
}
