package com.portfolio.healthcare.qa.pages.auth;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The {@code /forgot-password} page.
 *
 * <p>Only the <i>request</i> half of the reset flow is automatable without a
 * mailbox. On submit the page shows an anti-enumeration confirmation
 * ("If an account with that email exists, a reset link has been sent...")
 * regardless of whether the email is real, so tests can use a throwaway address
 * and never trigger a real reset for a live account.</p>
 */
public class ForgotPasswordPage extends BasePage {

    public ForgotPasswordPage(Page page) {
        super(page);
    }

    private Locator emailInput() {
        return page.locator("#email");
    }

    private Locator sendResetButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send Reset Link"));
    }

    private Locator confirmation() {
        return page.getByText("reset link has been sent", new Page.GetByTextOptions().setExact(false));
    }

    public ForgotPasswordPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/forgot-password");
        waitUntilVisible(emailInput());
        return this;
    }

    public ForgotPasswordPage requestReset(String email) {
        type(emailInput(), email);
        clickIfEnabled(sendResetButton());
        return this;
    }

    /** Waits for the "reset link has been sent" confirmation to appear. */
    public boolean isResetLinkConfirmationShown() {
        try {
            waitUntilVisible(confirmation());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }
}
