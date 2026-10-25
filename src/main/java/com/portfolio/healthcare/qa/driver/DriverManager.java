package com.portfolio.healthcare.qa.driver;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.config.FrameworkConfig;
import com.portfolio.healthcare.qa.factory.BrowserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Owns the per-thread browser stack ({@link Browser} &rarr; {@link BrowserContext}
 * &rarr; {@link Page}) and guarantees clean teardown.
 *
 * <p>This is the single entry point tests use for a driver. It keeps each
 * artifact in a {@link ThreadLocal} so parallel tests never share a page, and
 * {@link #quit()} closes everything in the reverse order it was opened
 * (context, browser, then the thread's Playwright engine) &mdash; the automatic
 * cleanup the spec requires.</p>
 */
public final class DriverManager {

    private static final Logger log = LoggerFactory.getLogger(DriverManager.class);

    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private DriverManager() {
        // Static holder - no instances.
    }

    /**
     * Creates a fresh browser &rarr; context &rarr; page for the current thread
     * and returns the page. Any existing driver on this thread is discarded via
     * {@link #quit()} first, so a stray call can never leak a browser.
     *
     * @return the newly created {@link Page}
     */
    public static Page createDriver() {
        quit();

        FrameworkConfig config = ConfigManager.config();
        Playwright playwright = PlaywrightFactory.get();
        Browser browser = BrowserFactory.createBrowser(playwright);
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(config.viewportWidth(), config.viewportHeight()));
        Page page = context.newPage();

        BROWSER.set(browser);
        CONTEXT.set(context);
        PAGE.set(page);

        log.debug("Driver created on thread '{}'", Thread.currentThread().getName());
        return page;
    }

    /**
     * Replaces the current thread's context with a fresh one seeded from a saved
     * storage state (an authenticated session), reusing the existing browser.
     * The old page/context are closed. Returns the new authenticated page.
     */
    public static Page useStorageState(Path storageState) {
        Browser browser = BROWSER.get();
        if (browser == null) {
            throw new IllegalStateException("No browser on this thread; call createDriver() first.");
        }
        BrowserContext old = CONTEXT.get();
        if (old != null) {
            closeQuietly("context", old::close);
        }
        PAGE.remove();

        FrameworkConfig config = ConfigManager.config();
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(config.viewportWidth(), config.viewportHeight())
                .setStorageStatePath(storageState));
        Page page = context.newPage();
        CONTEXT.set(context);
        PAGE.set(page);
        return page;
    }

    /** The current thread's page, or {@code null} if no driver is active. */
    public static Page getPage() {
        return PAGE.get();
    }

    /** The current thread's browser context, or {@code null} if none is active. */
    public static BrowserContext getContext() {
        return CONTEXT.get();
    }

    /**
     * Closes the current thread's context, browser and Playwright engine (in
     * that order) and detaches all thread-locals. Safe to call when nothing is
     * open; safe to call twice. Closing the context also closes its page, so we
     * only need to detach the page thread-local.
     */
    public static void quit() {
        BrowserContext context = CONTEXT.get();
        if (context != null) {
            closeQuietly("context", context::close);
            CONTEXT.remove();
        }
        PAGE.remove();

        Browser browser = BROWSER.get();
        if (browser != null) {
            closeQuietly("browser", browser::close);
            BROWSER.remove();
        }

        PlaywrightFactory.close();
    }

    private static void closeQuietly(String label, Runnable closeAction) {
        try {
            closeAction.run();
        } catch (RuntimeException e) {
            log.warn("Failed to close {} cleanly: {}", label, e.getMessage());
        }
    }
}
