package com.portfolio.healthcare.qa.tests.auth;

import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.DashboardPage;
import com.portfolio.healthcare.qa.pages.auth.ForgotPasswordPage;
import com.portfolio.healthcare.qa.pages.auth.LoginPage;
import com.portfolio.healthcare.qa.pages.auth.RequestAccessPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Module 1 - Authentication & Access Control.
 *
 * Coverage in this class:
 *   TC-AUTH-001  Valid login for all credential-holding roles (data-driven)
 *   TC-AUTH-002  Invalid password -> stays on login with error banner
 *   (negative)   Blank credentials -> form does not submit
 *
 * Blocked/deferred (see triage): Super Admin login (no creds), RBAC guard,
 * forgot/reset password, request-access, logout.
 */
@Epic("Authentication")
@Feature("Login")
public class AuthenticationTest extends BaseTest {

    @Test(dataProvider = "authenticatableRoles",
            dataProviderClass = com.portfolio.healthcare.qa.dataproviders.RoleDataProvider.class,
            description = "TC-AUTH-001: Valid login redirects each role to the dashboard")
    @Story("TC-AUTH-001 Valid login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Each credential-holding role logs in with valid credentials and is "
            + "redirected away from /login to /dashboard.")
    public void validLoginRedirectsToDashboard(UserRole role) {
        DashboardPage dashboard = new LoginPage(page()).open().loginAs(role);

        Assert.assertTrue(dashboard.isLoaded(),
                "[" + role.displayName() + "] expected redirect to dashboard, but URL was: "
                        + dashboard.currentUrl());
    }

    @Test(description = "TC-AUTH-002: Invalid password keeps the user on login with an error")
    @Story("TC-AUTH-002 Invalid credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("A valid email with a wrong password stays on /healthcare/login and surfaces "
            + "the 'Invalid email or password' banner.")
    public void invalidPasswordShowsErrorAndStaysOnLogin() {
        LoginPage loginPage = new LoginPage(page()).open();
        loginPage.login(com.portfolio.healthcare.qa.config.ConfigManager.config().surgeonEmail(), "WrongPassword!123");

        Assert.assertTrue(loginPage.currentUrl().contains("/login"),
                "Expected to remain on /login after invalid password, but URL was: " + loginPage.currentUrl());
        Assert.assertTrue(loginPage.waitForErrorBanner(),
                "Expected the invalid-credentials error banner to become visible.");
        Assert.assertTrue(loginPage.errorBannerText().toLowerCase().contains("invalid email or password"),
                "Unexpected error banner text: " + loginPage.errorBannerText());
    }

    @Test(description = "Blank credentials do not submit the login form")
    @Story("TC-AUTH-002 Invalid credentials")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submitting with empty email/password keeps the submit button disabled, "
            + "so the user never leaves /healthcare/login.")
    public void blankCredentialsDoNotSubmit() {
        LoginPage loginPage = new LoginPage(page()).open();
        loginPage.login("", "");

        Assert.assertTrue(loginPage.currentUrl().contains("/login"),
                "Expected to remain on /login when submitting blank credentials, but URL was: "
                        + loginPage.currentUrl());
    }

    @Test(description = "TC-AUTH-003: A Surgeon is blocked from admin routes by the RBAC guard")
    @Feature("Access Control")
    @Story("TC-AUTH-003 RBAC guard")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Logged in as Surgeon, direct navigation to /admin/users renders an "
            + "'Access Denied' page instead of exposing admin data.")
    public void surgeonIsBlockedFromAdminRoutes() {
        DashboardPage dashboard = new LoginPage(page()).open().loginAs(UserRole.SURGEON);
        Assert.assertTrue(dashboard.isLoaded(), "Precondition failed: Surgeon did not reach the dashboard.");

        Assert.assertTrue(dashboard.isAccessDeniedAt("/admin/users"),
                "Expected an 'Access Denied' guard for a Surgeon at /admin/users, URL was: "
                        + dashboard.currentUrl());
    }

    @Test(description = "TC-AUTH-007: Logout invalidates the session")
    @Feature("Logout")
    @Story("TC-AUTH-007 Session invalidation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("After logout the user is returned to the login page, and revisiting "
            + "/dashboard bounces back to login (token wiped).")
    public void logoutInvalidatesSession() {
        DashboardPage dashboard = new LoginPage(page()).open().loginAs(UserRole.SURGEON);
        Assert.assertTrue(dashboard.isLoaded(), "Precondition failed: Surgeon did not reach the dashboard.");

        LoginPage afterLogout = dashboard.logout();
        Assert.assertTrue(afterLogout.currentUrl().contains("/login"),
                "Expected to land on the login page after logout, URL was: " + afterLogout.currentUrl());
        Assert.assertTrue(dashboard.isSessionInvalidated(),
                "Expected the session to be invalidated (revisiting /dashboard should redirect to login).");
    }

    @Test(description = "TC-AUTH-004: Forgot-password request shows the reset-link confirmation")
    @Feature("Password Recovery")
    @Story("TC-AUTH-004 Forgot password")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submitting an email on /forgot-password shows the anti-enumeration "
            + "confirmation. Uses a throwaway address, so no real account is affected. "
            + "The reset-token half (TC-AUTH-005) is blocked without mailbox access.")
    public void forgotPasswordShowsResetConfirmation() {
        ForgotPasswordPage forgot = new ForgotPasswordPage(page())
                .open()
                .requestReset("qa-forgot-noreply@example.test");

        Assert.assertTrue(forgot.isResetLinkConfirmationShown(),
                "Expected the 'reset link has been sent' confirmation after requesting a reset.");
    }

    @Test(description = "TC-AUTH-006: Request-access form renders and gates its submit button")
    @Feature("Access Requests")
    @Story("TC-AUTH-006 Request access")
    @Severity(SeverityLevel.NORMAL)
    @Description("The /request-access form shows all required fields and keeps Submit "
            + "disabled until valid. Full submission is intentionally not driven to avoid "
            + "seeding PENDING_APPROVAL records into the Super Admin queue.")
    public void requestAccessFormRendersAndGatesSubmit() {
        RequestAccessPage requestAccess = new RequestAccessPage(page()).open();

        Assert.assertTrue(requestAccess.allExpectedFieldsPresent(),
                "Expected all request-access fields (name, email, phone, membership, role, hospital) to render.");
        Assert.assertTrue(requestAccess.isSubmitDisabled(),
                "Expected the Submit Request button to be disabled until the form is valid.");
    }

}
