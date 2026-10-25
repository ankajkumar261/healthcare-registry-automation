package com.portfolio.healthcare.qa.pages.auth;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The {@code /request-access} registration page.
 *
 * <p>We automate that the form renders with all required fields and that the
 * submit button is correctly <b>gated</b> (disabled until the form is valid).
 * End-to-end submission is intentionally not driven here &mdash; it would seed a
 * real {@code PENDING_APPROVAL} record into the Super Admin queue on every run.
 * Full submission belongs in a flow with dedicated test data + cleanup.</p>
 */
public class RequestAccessPage extends BasePage {

    public RequestAccessPage(Page page) {
        super(page);
    }

    public RequestAccessPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/request-access");
        waitUntilVisible(page.locator("#firstName"));
        return this;
    }

    private Locator submitButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit Request"));
    }

    /** True only if every field the registration form requires is present. */
    public boolean allExpectedFieldsPresent() {
        return isVisible(page.locator("#firstName"))
                && isVisible(page.locator("#lastName"))
                && isVisible(page.locator("#email"))
                && isVisible(page.locator("#phone"))
                && isVisible(page.locator("#healthcareMembershipNo"))
                && isVisible(page.locator("#role"))
                && isVisible(page.locator("#hospital-search"));
    }

    /** The submit button should be disabled until the form is valid. */
    public boolean isSubmitDisabled() {
        return !submitButton().isEnabled();
    }
}
