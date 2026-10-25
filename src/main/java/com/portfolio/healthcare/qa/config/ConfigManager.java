package com.portfolio.healthcare.qa.config;

import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eager singleton that resolves the active {@link Environment}, builds the
 * {@link FrameworkConfig} proxy for it, and exposes it to the rest of the
 * framework.
 *
 * <p><b>Why a singleton:</b> configuration is immutable, read-only, JVM-wide
 * state &mdash; there is exactly one active environment per test run. The eager
 * {@code static} initializer is thread-safe by virtue of the JVM classloading
 * contract (the class is initialized once, under lock), so no explicit
 * synchronization is required.</p>
 *
 * <p>Resolution order for the environment name:
 * {@code -Denv} &rarr; {@code ENV} variable &rarr; default {@code dev}.</p>
 */
public final class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private static final FrameworkConfig CONFIG;

    static {
        Environment environment = resolveEnvironment();
        // Owner substitutes ${env} in @Config.Sources from factory properties.
        ConfigFactory.setProperty("env", environment.key());
        CONFIG = ConfigFactory.create(FrameworkConfig.class);
        log.info("Configuration loaded for environment '{}' (app.base.url={}, browser={}, headless={})",
                environment.key(), CONFIG.appBaseUrl(), CONFIG.browser(), CONFIG.headless());
    }

    private ConfigManager() {
        // Static holder - no instances.
    }

    /** The strongly-typed configuration for the active environment. */
    public static FrameworkConfig config() {
        return CONFIG;
    }

    /** Full login URL for the active environment ({@code app.base.url} + {@code login.path}). */
    public static String loginUrl() {
        return CONFIG.appBaseUrl() + CONFIG.loginPath();
    }

    private static Environment resolveEnvironment() {
        String raw = System.getProperty("env");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("ENV");
        }
        if (raw == null || raw.isBlank()) {
            raw = Environment.DEV.key();
        }
        return Environment.from(raw);
    }
}
