package com.portfolio.healthcare.qa.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.util.regex.Pattern;

/**
 * The Hospitals admin page at {@code /admin/hospitals} (Registry Admin).
 *
 * <p>A searchable table (Name, Consultant, City, State, Code, Surgeons,
 * Surgeries, Status, Actions) with an "Add Hospital" dialog. The create form has
 * four fields - Hospital Name, City, State, Hospital Code - located by their
 * example placeholders. Backed by {@code GET/POST/DELETE /api/admin/hospitals}.
 * Selectors verified against the live DOM.</p>
 *
 * <p><b>Deviations from the QA doc:</b> hospital administration is an
 * <b>Registry Admin</b> capability (Super Admin is denied on {@code /admin/*}); the
 * create form has <b>no "assign Hospital Admin email"</b> field (so no welcome
 * email is triggered from here).</p>
 */
public class AdminHospitalsPage extends BasePage {

    public AdminHospitalsPage(Page page) {
        super(page);
    }

    public AdminHospitalsPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/admin/hospitals");
        waitUntilVisible(addHospitalButton());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator addHospitalButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Hospital")).first();
    }

    /** The dialog's submit button (the "Add Hospital" rendered last, in the portal). */
    private Locator submitButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Hospital")).last();
    }

    private Locator nameInput() {
        return page.getByPlaceholder(Pattern.compile("Apollo Hospital", Pattern.CASE_INSENSITIVE));
    }

    private Locator cityInput() {
        return page.getByPlaceholder(Pattern.compile("e\\.g\\. Mumbai", Pattern.CASE_INSENSITIVE));
    }

    private Locator stateInput() {
        return page.getByPlaceholder(Pattern.compile("e\\.g\\. Maharashtra", Pattern.CASE_INSENSITIVE));
    }

    private Locator codeInput() {
        return page.getByPlaceholder(Pattern.compile("APOLLO-MUM", Pattern.CASE_INSENSITIVE));
    }

    private Locator searchInput() {
        return page.getByPlaceholder(Pattern.compile("Search by name", Pattern.CASE_INSENSITIVE));
    }

    // --- Actions -------------------------------------------------------------
    /** Creates a hospital via the Add Hospital dialog and waits for it to close. */
    public AdminHospitalsPage addHospital(String name, String city, String state, String code) {
        addHospitalButton().click();
        waitUntilVisible(nameInput());
        type(nameInput(), name);
        type(cityInput(), city);
        type(stateInput(), state);
        type(codeInput(), code);
        submitButton().click();
        // The dialog closes on success.
        nameInput().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN).setTimeout(timeoutMs()));
        return this;
    }

    /** Filters the table by a search term (name, consultant or code). */
    public AdminHospitalsPage search(String term) {
        type(searchInput(), term);
        return this;
    }

    // --- Queries -------------------------------------------------------------
    /** True if a table row containing the given text is visible. */
    public boolean showsHospital(String text) {
        try {
            waitUntilVisible(page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHasText(text)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }
}
