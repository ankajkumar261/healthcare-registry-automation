package com.portfolio.healthcare.qa.pages.patients;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The Patient Registry list at {@code /patients}: a searchable/filterable grid.
 *
 * <p>The search box has a framework-generated, non-deterministic id, so it is
 * located by its placeholder. Rows are read dynamically (never hard-coded), so
 * the search test survives changes to the underlying data set.</p>
 */
public class PatientListPage extends BasePage {

    private static final String SEARCH_PLACEHOLDER = "Search by name or registry ID...";
    private static final String NO_RESULTS_TEXT = "No patients found.";

    public PatientListPage(Page page) {
        super(page);
    }

    // --- Locators ------------------------------------------------------------
    private Locator searchBox() {
        return page.getByPlaceholder(SEARCH_PLACEHOLDER);
    }

    private Locator rows() {
        return page.locator("table tbody tr");
    }

    private Locator rowContaining(String text) {
        return rows().filter(new Locator.FilterOptions().setHasText(text));
    }

    private Locator noResults() {
        return page.getByText(NO_RESULTS_TEXT);
    }

    // --- Actions -------------------------------------------------------------
    public PatientListPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/patients");
        waitUntilVisible(searchBox());
        waitForGridSettled();
        return this;
    }

    /** Waits until the async grid has rendered either its first row or the empty state. */
    private void waitForGridSettled() {
        rows().first().or(noResults()).first().waitFor(
                new Locator.WaitForOptions().setTimeout(timeoutMs()));
    }

    /** Types a search term (debounced client-side filtering follows). */
    public PatientListPage search(String term) {
        type(searchBox(), term);
        return this;
    }

    /**
     * Waits for the debounced grid to settle at exactly {@code expected} rows
     * (web-first count assertion that retries until the timeout).
     */
    public PatientListPage waitForRowCount(int expected) {
        PlaywrightAssertions.assertThat(rows()).hasCount(expected,
                new LocatorAssertions.HasCountOptions().setTimeout(timeoutMs()));
        return this;
    }

    /** Waits for the empty-state message after a non-matching search. */
    public PatientListPage waitForNoResults() {
        waitUntilVisible(noResults());
        return this;
    }

    // --- Queries -------------------------------------------------------------
    public int rowCount() {
        return rows().count();
    }

    /** The registry ID in the first row's first cell (e.g. {@code HOSP-P123}). */
    public String firstRowRegistryId() {
        return rows().first().locator("td").first().textContent().trim();
    }

    /** The edit-page href for the first row (uses the patient's internal id). */
    public String firstRowEditHref() {
        return rows().first().locator("a[href$='/edit']").first().getAttribute("href");
    }

    public boolean isNoResultsShown() {
        return isVisible(noResults());
    }
}
