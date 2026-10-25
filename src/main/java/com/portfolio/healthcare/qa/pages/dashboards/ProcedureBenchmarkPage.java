package com.portfolio.healthcare.qa.pages.dashboards;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The Procedure Benchmarks page at {@code /procedure-benchmark}.
 *
 * <p>Ranks procedure types in a table (columns {@code # | Procedure | Cases |
 * Mean TWL% | Mean EWL% | DM Remission% | Insulin Cessation% | Complication%})
 * driven by three native {@code <select>}s: <b>Rank by</b> (Cases / Mean TWL% /
 * ...), <b>Order</b> (Highest to Lowest / Lowest to Highest) and <b>Compare vs</b>
 * (a baseline procedure). Backed by {@code GET /api/dashboard/procedure-comparison}.
 * Selectors verified against the live DOM.</p>
 *
 * <p><b>Deviation from the QA doc:</b> TC-DASH-004 describes filtering by "age
 * group 18-40" and "Procedure RNY"; this page has <b>no age or single-procedure
 * filter</b> - the real controls are Rank by / Order / Compare vs. The test
 * exercises the ranking controls (the analytics-filtering behaviour that does
 * exist).</p>
 */
public class ProcedureBenchmarkPage extends BasePage {

    public ProcedureBenchmarkPage(Page page) {
        super(page);
    }

    public ProcedureBenchmarkPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/procedure-benchmark");
        waitUntilVisible(page.getByText("Procedure Benchmarks", new Page.GetByTextOptions().setExact(false)).first());
        waitUntilVisible(rows().first());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator rows() {
        return page.locator("table tbody tr");
    }

    /** The "Order" select (identified by its distinctive options). */
    private Locator orderSelect() {
        return page.locator("select").filter(new Locator.FilterOptions().setHasText("Highest to Lowest")).first();
    }

    /** The "Rank by" select (the one offering the metric columns). */
    private Locator rankBySelect() {
        return page.locator("select").filter(new Locator.FilterOptions().setHasText("Mean TWL %")).first();
    }

    // --- Actions -------------------------------------------------------------
    /** Sets the ranking order, e.g. {@code "Lowest to Highest"}. */
    public ProcedureBenchmarkPage setOrder(String label) {
        orderSelect().selectOption(new SelectOption().setLabel(label));
        page.waitForTimeout(500); // brief settle for the client-side re-sort
        return this;
    }

    /** Sets the ranking metric, e.g. {@code "Mean TWL %"}. */
    public ProcedureBenchmarkPage setRankBy(String label) {
        rankBySelect().selectOption(new SelectOption().setLabel(label));
        page.waitForTimeout(500);
        return this;
    }

    // --- Queries -------------------------------------------------------------
    public int rowCount() {
        return rows().count();
    }

    /** The procedure name in the top-ranked row (second column). */
    public String topRankedProcedure() {
        return rows().first().locator("td").nth(1).innerText().trim();
    }
}
