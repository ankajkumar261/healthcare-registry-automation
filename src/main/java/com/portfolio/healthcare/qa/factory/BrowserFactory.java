package com.portfolio.healthcare.qa.factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.config.FrameworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Factory</b> that launches a {@link Browser} for the configured engine.
 *
 * <p>Single responsibility: translate framework configuration
 * ({@code browser}, {@code headless}, {@code slowmo}) into Playwright
 * {@link BrowserType.LaunchOptions} and delegate the actual launch to the
 * chosen {@link SupportedBrowser} strategy.</p>
 */
public final class BrowserFactory {

    private static final Logger log = LoggerFactory.getLogger(BrowserFactory.class);

    private BrowserFactory() {
        // Static factory - no instances.
    }

    /**
     * Launches the browser described by the active {@link FrameworkConfig}.
     *
     * @param playwright the engine for the current thread
     * @return a live {@link Browser} the caller owns and must close
     */
    public static Browser createBrowser(Playwright playwright) {
        FrameworkConfig config = ConfigManager.config();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(config.headless())
                .setSlowMo(config.slowMo());

        SupportedBrowser strategy = SupportedBrowser.from(config.browser());
        log.info("Launching {} (headless={}, slowMo={}ms)",
                strategy.name().toLowerCase(), config.headless(), config.slowMo());

        return strategy.browserType(playwright).launch(launchOptions);
    }
}
