package com.portfolio.healthcare.qa.driver;

import com.microsoft.playwright.Playwright;

/**
 * Provides exactly one {@link Playwright} engine <b>per thread</b>.
 *
 * <p>Playwright's {@code Playwright} object is not thread-safe and is relatively
 * expensive to create, so we keep one per thread in a {@link ThreadLocal}. In a
 * parallel run each TestNG thread gets its own engine; in a serial run there is
 * just one. This is the "Singleton Playwright instance (per thread)" the spec
 * asks for &mdash; a thread-scoped singleton, which is the correct scope for a
 * non-thread-safe resource.</p>
 */
public final class PlaywrightFactory {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();

    private PlaywrightFactory() {
        // Static holder - no instances.
    }

    /** Returns the current thread's engine, creating it lazily on first use. */
    public static Playwright get() {
        Playwright playwright = PLAYWRIGHT.get();
        if (playwright == null) {
            playwright = Playwright.create();
            PLAYWRIGHT.set(playwright);
        }
        return playwright;
    }

    /** Closes and detaches the current thread's engine, if any. */
    public static void close() {
        Playwright playwright = PLAYWRIGHT.get();
        if (playwright != null) {
            playwright.close();
            PLAYWRIGHT.remove();
        }
    }
}
