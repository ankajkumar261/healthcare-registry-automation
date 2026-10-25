package com.portfolio.healthcare.qa.pages.patients;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.util.regex.Pattern;

/**
 * The "Edit Patient" form at {@code /patients/{internalId}/edit}.
 *
 * <p>It is the same wizard as {@link NewPatientPage} but pre-filled, with the
 * step tabs directly clickable (all steps are already valid). Saving happens on
 * the Review step.</p>
 */
public class EditPatientPage extends BasePage {

    public EditPatientPage(Page page) {
        super(page);
    }

    private Locator ageInput() {
        return page.locator("#age");
    }

    /** Navigates to an edit href (e.g. {@code /patients/<id>/edit}). */
    public EditPatientPage open(String editHref) {
        page.navigate(ConfigManager.config().appBaseUrl() + editHref);
        waitUntilVisible(ageInput());
        return this;
    }

    public String currentAge() {
        return ageInput().inputValue();
    }

    public EditPatientPage setAge(String age) {
        type(ageInput(), age);
        return this;
    }

    /**
     * Advances through the (pre-filled) steps via Next to Review and clicks the
     * save/update action, then waits for the redirect off the edit form. The
     * step tabs themselves are disabled, so navigation must use Next.
     */
    public void save() {
        Locator save = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("update|save|submit", Pattern.CASE_INSENSITIVE)));
        Locator next = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Next")).first();
        for (int i = 0; i < 5 && !(save.count() > 0 && save.first().isVisible()); i++) {
            next.click();
            try {
                save.first().waitFor(new Locator.WaitForOptions().setTimeout(2500));
            } catch (com.microsoft.playwright.PlaywrightException ignored) {
                // not on Review yet; loop clicks Next again
            }
        }
        save.first().click();
        waitForUrlToLeave("/edit");
    }
}
