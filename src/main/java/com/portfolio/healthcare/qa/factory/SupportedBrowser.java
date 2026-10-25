package com.portfolio.healthcare.qa.factory;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * The browsers the framework supports, modelled as a <b>Strategy</b> via a
 * Java enum.
 *
 * <p>Each constant knows how to obtain its own {@link BrowserType} from a
 * {@link Playwright} instance. Adding a new engine means adding a constant here
 * and nothing else &mdash; the {@link BrowserFactory} and {@code BaseTest} stay
 * untouched (Open/Closed principle). This replaces the old {@code switch} that
 * was inlined in {@code BaseTest}.</p>
 */
public enum SupportedBrowser {

    CHROMIUM {
        @Override
        BrowserType browserType(Playwright playwright) {
            return playwright.chromium();
        }
    },
    FIREFOX {
        @Override
        BrowserType browserType(Playwright playwright) {
            return playwright.firefox();
        }
    },
    WEBKIT {
        @Override
        BrowserType browserType(Playwright playwright) {
            return playwright.webkit();
        }
    };

    /** Each strategy resolves its own Playwright browser type. */
    abstract BrowserType browserType(Playwright playwright);

    /**
     * Resolves a configured browser name (e.g. {@code "chromium"}) to a strategy,
     * case-insensitively, failing fast on unknown values.
     *
     * @throws IllegalArgumentException if the name matches no supported browser
     */
    public static SupportedBrowser from(String name) {
        if (name != null) {
            for (SupportedBrowser supported : values()) {
                if (supported.name().equalsIgnoreCase(name.trim())) {
                    return supported;
                }
            }
        }
        throw new IllegalArgumentException(
                "Unsupported browser '" + name + "'. Expected one of: chromium | firefox | webkit");
    }
}
