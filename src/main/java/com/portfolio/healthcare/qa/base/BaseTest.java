package com.portfolio.healthcare.qa.base;

import com.microsoft.playwright.Page;
import com.portfolio.healthcare.qa.auth.AuthStateManager;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.driver.DriverManager;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.DashboardPage;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayInputStream;

/**
 * Base class for all Healthcare Registry QA tests.
 *
 * <p>Responsibility is now only lifecycle <i>orchestration</i>: create a driver
 * before each test, tear it down after, and attach a screenshot on failure. The
 * <i>how</i> of driver creation (engine, browser, context, cleanup) lives in the
 * driver layer ({@link DriverManager}), which keeps this class small and lets the
 * suite run in parallel without any changes here.</p>
 */
public class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        // Create the driver only; each test navigates where it needs (auth tests
        // open the login page, authenticated tests seed a session via loginAs()).
        DriverManager.createDriver();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        if (result.getStatus() == ITestResult.FAILURE && page() != null) {
            attachScreenshot("Failure: " + result.getName());
        }
        DriverManager.quit();
    }

    /** Attaches a full-page screenshot to the Allure report. */
    protected void attachScreenshot(String name) {
        try {
            byte[] bytes = page().screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment(name, new ByteArrayInputStream(bytes));
        } catch (RuntimeException e) {
            log.warn("Could not capture screenshot '{}': {}", name, e.getMessage());
        }
    }

    /** The active page for the current thread. */
    protected Page page() {
        return DriverManager.getPage();
    }

    /** Whether this environment deletes the records tests create (true on live, false on dev). */
    protected boolean dataCleanupEnabled() {
        return ConfigManager.config().dataCleanup();
    }

    /**
     * The role used to register patients in this environment: the Surgeon when
     * the surgeon can create directly (live), otherwise the Hospital Admin (dev,
     * where the surgeon's hospital picker is broken).
     */
    protected UserRole patientCreateRole() {
        return ConfigManager.config().surgeonCreatesDirectly()
                ? UserRole.SURGEON
                : UserRole.HOSPITAL_ADMIN;
    }

    /**
     * Convenience for authenticated tests: seed this thread's context with the
     * role's cached storage state (no UI login) and land on the dashboard.
     *
     * <p>The first call for a role triggers a one-time UI login inside
     * {@link AuthStateManager}; subsequent calls just restore the saved session,
     * so authenticated tests skip the login flow entirely.</p>
     */
    protected DashboardPage loginAs(UserRole role) {
        Page page = DriverManager.useStorageState(AuthStateManager.storageStateFor(role));
        page.navigate(ConfigManager.config().appBaseUrl() + "/dashboard");
        // Wait for a genuine authenticated signal (the header account menu), not
        // just the URL: restoring the session is async, so a URL-only check races
        // the app init and a follow-up navigation can bounce back to login.
        try {
            page.getByLabel("Open account menu")
                    .waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                            .setTimeout(ConfigManager.config().timeoutMs()));
        } catch (com.microsoft.playwright.PlaywrightException e) {
            throw new IllegalStateException("Reused auth state was not valid for role "
                    + role.displayName() + " - landed at " + page.url());
        }
        return new DashboardPage(page);
    }
}
