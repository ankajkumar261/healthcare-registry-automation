package com.portfolio.healthcare.qa.pages.patients;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The "Bulk Import Cases" page at {@code /patients/import}.
 *
 * <p>A three-step flow: (1) download the Excel template, (2) upload a filled
 * {@code .xlsx/.xls/.csv}, (3) preview. Validation runs entirely
 * <b>client-side</b> (a {@code bulkImportValidation} module) - no server round
 * trip - so the preview shows "{@code N rows parsed}", either
 * "{@code Every row passed validation.}" or an error table with columns
 * {@code Row | Column | Problem}, and a submit button whose label reflects the
 * outcome: "{@code Import N cases}" when all rows are valid, or
 * "{@code Import N valid case (skip M)}" when some rows are invalid. Only on
 * submit does the app call {@code POST /api/patients/bulk}, which returns
 * {@code {total, successCount, failureCount, results[]}}; the result panel then
 * shows "{@code N imported / M failed}". Selectors verified against the live DOM.</p>
 *
 * <p><b>Findings vs the QA doc:</b> the invalid-format rejection reads
 * {@code "<name>" is not a spreadsheet. Upload a .xlsx, .xls or .csv file.}
 * (not the doc's "Only .xlsx, .xls, or .csv files are allowed"); invalid rows are
 * <b>skipped</b>, not rolled back (no transactional fail-fast); and the
 * "up to 500 rows" copy is advisory - it is <b>not enforced</b> (501 rows still
 * preview and import).</p>
 */
public class BulkImportPage extends BasePage {

    private static final Pattern IMPORT_ALL = Pattern.compile("^Import \\d+ case");
    private static final Pattern IMPORT_VALID_SKIP = Pattern.compile("^Import \\d+ valid case");

    public BulkImportPage(Page page) {
        super(page);
    }

    // --- Navigation ----------------------------------------------------------
    public BulkImportPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/patients/import");
        waitUntilVisible(page.getByText("Bulk Import Cases", new Page.GetByTextOptions().setExact(false)).first());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    /** The (visually hidden) file input; {@code setInputFiles} works on it directly. */
    private Locator fileInput() {
        return page.locator("input[type=file]").first();
    }

    private Locator importAllButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(IMPORT_ALL));
    }

    private Locator importValidSkippingButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(IMPORT_VALID_SKIP));
    }

    private Locator cancelButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel"));
    }

    // --- Actions -------------------------------------------------------------
    /**
     * Selects a file for import. For a spreadsheet this renders the preview; for
     * any other type it renders the inline "not a spreadsheet" rejection. Waits
     * for whichever outcome appears so callers can assert immediately.
     */
    public BulkImportPage selectFile(Path file) {
        fileInput().setInputFiles(file);
        page.getByText(Pattern.compile("rows parsed|is not a spreadsheet"))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs()));
        return this;
    }

    /** Clicks "Import N cases" (all rows valid) and waits for the result panel. */
    public BulkImportPage importAll() {
        importAllButton().click();
        waitForResult();
        return this;
    }

    /** Clicks "Import N valid case (skip M)" and waits for the result panel. */
    public BulkImportPage importValidSkippingInvalid() {
        importValidSkippingButton().click();
        waitForResult();
        return this;
    }

    public BulkImportPage cancel() {
        cancelButton().click();
        return this;
    }

    private void waitForResult() {
        page.getByText("Import result", new Page.GetByTextOptions().setExact(false))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs()));
    }

    // --- Queries -------------------------------------------------------------
    private boolean showsText(String text) {
        try {
            waitUntilVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** True if the inline invalid-format rejection ("... is not a spreadsheet ...") is shown. */
    public boolean showsFormatRejection() {
        return showsText("is not a spreadsheet");
    }

    /** True if the preview reports the given parsed-row count (e.g. {@code 3} -> "3 rows parsed"). */
    public boolean showsRowsParsed(int rows) {
        return showsText(rows + " rows parsed");
    }

    /** True if every parsed row passed validation. */
    public boolean showsAllRowsValid() {
        return showsText("Every row passed validation");
    }

    /** True if the preview grid shows the given cell value (e.g. a hospital patient id). */
    public boolean previewShows(String value) {
        return showsText(value);
    }

    /** True if the invalid-row error table reports the given number of unimportable rows. */
    public boolean showsCannotImportCount(int rows) {
        String noun = rows == 1 ? " row cannot be imported" : " rows cannot be imported";
        return showsText(rows + noun);
    }

    /** True if the error table contains the given problem text (verbatim). */
    public boolean showsRowError(String problemText) {
        return showsText(problemText);
    }

    /** True if the result panel reports the given imported count (e.g. "3 imported"). */
    public boolean showsImported(int count) {
        return showsText(count + " imported");
    }

    /** True if the result panel reports the given failed count (e.g. "0 failed"). */
    public boolean showsFailed(int count) {
        return showsText(count + " failed");
    }

    /** True while the "Import N valid case (skip M)" submit button is offered. */
    public boolean offersSkipInvalidImport() {
        return importValidSkippingButton().count() > 0;
    }
}
