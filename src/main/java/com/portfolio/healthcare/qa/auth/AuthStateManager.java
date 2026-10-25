package com.portfolio.healthcare.qa.auth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.portfolio.healthcare.qa.factory.BrowserFactory;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.DashboardPage;
import com.portfolio.healthcare.qa.pages.auth.LoginPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Produces and caches a Playwright <b>storage state</b> (cookies + localStorage,
 * i.e. the auth session) per {@link UserRole}.
 *
 * <p>Logging in through the UI is slow, so we pay that cost <b>once per role per
 * JVM</b>: the first request for a role performs a real UI login in a throwaway
 * context and saves the resulting session to {@code target/auth-state/<role>.json}.
 * Every test then seeds a fresh context from that file and is instantly
 * authenticated &mdash; no repeated logins. The cache is a
 * {@link ConcurrentHashMap} so parallel threads generate each role's state at
 * most once.</p>
 */
public final class AuthStateManager {

    private static final Logger log = LoggerFactory.getLogger(AuthStateManager.class);
    private static final Path DIR = Paths.get("target", "auth-state");
    private static final Map<UserRole, Path> CACHE = new ConcurrentHashMap<>();

    private AuthStateManager() {
        // Static holder - no instances.
    }

    /** Path to a saved storage state for {@code role}, generating it on first use. */
    public static Path storageStateFor(UserRole role) {
        return CACHE.computeIfAbsent(role, AuthStateManager::generate);
    }

    private static Path generate(UserRole role) {
        log.info("Generating auth storage state for {} (one-time UI login)", role.displayName());
        try (Playwright playwright = Playwright.create()) {
            Browser browser = BrowserFactory.createBrowser(playwright);
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            DashboardPage dashboard = new LoginPage(page).open().loginAs(role);
            if (!dashboard.isLoaded()) {
                throw new IllegalStateException("Could not authenticate " + role.displayName()
                        + " while generating storage state (URL: " + dashboard.currentUrl() + ")");
            }

            Files.createDirectories(DIR);
            Path path = DIR.resolve(role.name().toLowerCase() + ".json");
            context.storageState(new BrowserContext.StorageStateOptions().setPath(path));
            browser.close();
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write storage state for " + role, e);
        }
    }
}
