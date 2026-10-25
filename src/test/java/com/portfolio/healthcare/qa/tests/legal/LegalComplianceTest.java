package com.portfolio.healthcare.qa.tests.legal;

import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.config.FrameworkConfig;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.auth.LoginPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Module 10 - Legal, Compliance & Audit Trail.
 *
 * <p>Read-only - no records are created (the agreement test never actually signs
 * in).</p>
 *
 * <p>Coverage in this class:</p>
 * <ul>
 *   <li>TC-LEG-002  Participation-agreement / Terms acceptance gate on login</li>
 * </ul>
 *
 * <p>TC-LEG-001 (informed-consent mandatory check) was removed: the New Case
 * Entry wizard has no "Informed Consent Obtained" checkbox on any step, so there
 * is no such control to test - reported to the product/compliance team.</p>
 *
 * <p><b>Findings vs the QA doc (verified live):</b></p>
 * <ul>
 *   <li>TC-LEG-002: acceptance is enforced as a <b>consent checkbox on the login
 *       screen</b> (the "Registry Registry Participation Agreement", with Terms of Use
 *       and Privacy Policy links) that gates the Sign In button - not a post-login
 *       modal as the doc describes. The test verifies that gate.</li>
 * </ul>
 */
@Epic("Legal & Compliance")
@Feature("Consent & Terms Acceptance")
public class LegalComplianceTest extends BaseTest {

    @Test(description = "TC-LEG-002: Participation-agreement acceptance gates access on login")
    @Story("TC-LEG-002 Terms of Service agreement")
    @Severity(SeverityLevel.CRITICAL)
    @Description("On the login screen the Registry Registry Participation Agreement (with Terms of Use and "
            + "Privacy Policy) must be accepted before access: with valid credentials entered, the "
            + "Sign In button stays disabled until the agreement checkbox is ticked, then becomes "
            + "enabled. NOTE: this is a login-screen gate, not the post-login modal the doc describes.")
    public void participationAgreementGatesLogin() {
        FrameworkConfig config = ConfigManager.config();
        LoginPage login = new LoginPage(page()).open()
                .enterEmail(UserRole.SURGEON.email(config))
                .enterPassword(UserRole.SURGEON.password(config));

        Assert.assertTrue(login.showsParticipationAgreement(),
                "Expected the participation agreement / Terms of Use to be shown on login.");
        Assert.assertFalse(login.isSignInEnabled(),
                "Sign In must be blocked until the participation agreement is accepted.");

        login.acceptConsent();
        Assert.assertTrue(login.isSignInEnabled(),
                "Sign In should be enabled once the participation agreement is accepted.");
    }
}
