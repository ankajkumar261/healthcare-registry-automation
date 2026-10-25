package com.portfolio.healthcare.qa.pages.exports;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Data Export page at {@code /exports} (Registry Admin).
 *
 * <p>A filter-then-download form: {@code From Date} ({@code #dateFrom}),
 * {@code To Date} ({@code #dateTo}), a Procedure Type picker, and
 * {@code Download CSV} / {@code Download Excel} buttons. Downloading a CSV calls
 * {@code GET /api/exports/csv} and streams the national registry (one row per
 * patient, 40 clinical columns). Selectors verified against the live DOM.</p>
 *
 * <p><b>Findings vs the QA doc (verified live):</b> there is <b>no per-column
 * field selection</b> and <b>no "De-identify Patient Data" toggle</b>; the export
 * always omits direct PII (no patient Name / Phone / Email column) but retains the
 * Hospital Patient ID. The date-range inputs are sent to the API
 * ({@code ?dateFrom=..&dateTo=..}) but the backend currently <b>ignores them</b>
 * - a far-past range still returns every row - so there is no empty-result
 * state.</p>
 */
public class ExportPage extends BasePage {

    public ExportPage(Page page) {
        super(page);
    }

    public ExportPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/exports");
        waitUntilVisible(csvButton());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator csvButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Download CSV"));
    }

    private Locator fromDate() {
        return page.locator("#dateFrom");
    }

    private Locator toDate() {
        return page.locator("#dateTo");
    }

    // --- Actions -------------------------------------------------------------
    /** Sets the export date range (ISO {@code yyyy-MM-dd}). */
    public ExportPage setDateRange(String fromIso, String toIso) {
        type(fromDate(), fromIso);
        type(toDate(), toIso);
        return this;
    }

    /**
     * Clicks "Download CSV", saves the download to a temp file, and returns its
     * path. The caller can then read the header row and data rows.
     */
    public Path downloadCsv() {
        Download download = page.waitForDownload(() -> csvButton().click());
        try {
            Path target = Files.createTempFile("registry-export-", ".csv");
            download.saveAs(target);
            target.toFile().deleteOnExit();
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save exported CSV", e);
        }
    }

    // --- Queries -------------------------------------------------------------
    /** True if a "no records"-style empty-result warning is shown (see class note). */
    public boolean showsNoRecordsWarning() {
        try {
            waitUntilVisible(page.getByText("No records", new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }
}
