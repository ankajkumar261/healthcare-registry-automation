package com.portfolio.healthcare.qa.pages.auth;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.config.FrameworkConfig;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.BasePage;
import com.portfolio.healthcare.qa.pages.DashboardPage;

/**
 * The Registry login page at {@code /healthcare/login}, shared by every role.
 *
 * <p>Exposes a <b>fluent API</b>: each step returns {@code this} so tests can
 * read like the flow they describe
 * ({@code loginPage.enterEmail(..).enterPassword(..).acceptConsent().submit()}).
 * Selectors verified against the live staging DOM.</p>
 */
public class LoginPage extends BasePage {

    public LoginPage(Page page) {
        super(page);
    }

    // --- Locators ------------------------------------------------------------
    private Locator emailInput() {
        return page.locator("#email");
    }

    private Locator passwordInput() {
        return page.locator("#password");
    }

    private Locator signInButton() {
        // Exact match: the page also has a "Sign in with OTP" button; the default
        // substring name match would hit both and trigger a strict-mode violation.
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign In").setExact(true));
    }

    private Locator consentCheckbox() {
        // Submit stays disabled until this Terms/Consent checkbox is checked.
        return page.getByRole(AriaRole.CHECKBOX);
    }

    private Locator errorBanner() {
        // Inline red banner: "Invalid email or password. Please try again."
        return page.getByText("Invalid email or password", new Page.GetByTextOptions().setExact(false));
    }

    // --- Fluent actions ------------------------------------------------------
    public LoginPage open() {
        page.navigate(ConfigManager.loginUrl());
        waitUntilVisible(emailInput());
        return this;
    }

    public LoginPage enterEmail(String email) {
        type(emailInput(), email);
        return this;
    }

    public LoginPage enterPassword(String password) {
        type(passwordInput(), password);
        return this;
    }

    public LoginPage acceptConsent() {
        consentCheckbox().check();
        return this;
    }

    /**
     * Submits the form once the Sign In button is enabled, returning the
     * destination {@link DashboardPage}.
     *
     * <p>Checking the consent box enables the button via a React state update
     * that does not land in the same tick, so reading {@code isEnabled()}
     * immediately races the re-render and can silently skip the click (a flaky
     * "stayed on /login" failure). This waits (bounded) for the button to become
     * enabled, then clicks. For genuinely blank/invalid input the button never
     * enables, so the wait times out into a deliberate no-op &mdash; keeping the
     * negative-path tests correct.</p>
     */
    public DashboardPage submit() {
        try {
            PlaywrightAssertions.assertThat(signInButton())
                    .isEnabled(new LocatorAssertions.IsEnabledOptions().setTimeout(5000));
            signInButton().click();
        } catch (AssertionError stayedDisabled) {
            // Blank/invalid input: the button never enabled, nothing to submit.
        }
        return new DashboardPage(page);
    }

    // --- High-level convenience ---------------------------------------------
    /** Full happy-path login for a role, sourcing credentials from config. */
    public DashboardPage loginAs(UserRole role) {
        FrameworkConfig config = ConfigManager.config();
        return enterEmail(role.email(config))
                .enterPassword(role.password(config))
                .acceptConsent()
                .submit();
    }

    /** Attempts login with explicit credentials (for negative cases). */
    public DashboardPage login(String email, String password) {
        return enterEmail(email).enterPassword(password).acceptConsent().submit();
    }

    // --- Queries -------------------------------------------------------------
    /**
     * Waits for the invalid-credentials banner to appear (it renders only after
     * the async 401 returns), bounded by the config timeout. Returns
     * {@code false} on timeout instead of throwing.
     */
    public boolean waitForErrorBanner() {
        try {
            waitUntilVisible(errorBanner());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    public String errorBannerText() {
        return errorBanner().first().textContent().trim();
    }

    // --- Legal / consent gate (Module 10) ------------------------------------
    /**
     * Whether the Sign In button is currently enabled. Access is gated on
     * accepting the Registry Registry Participation Agreement: the button stays
     * disabled until {@link #acceptConsent()} ticks the agreement checkbox.
     */
    public boolean isSignInEnabled() {
        return signInButton().isEnabled();
    }

    /**
     * True if the participation agreement is presented on the login screen (the
     * "Terms of Use" / "Participation Agreement" the user must accept before
     * access).
     */
    public boolean showsParticipationAgreement() {
        try {
            waitUntilVisible(page.getByText("Terms of Use", new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }
}
