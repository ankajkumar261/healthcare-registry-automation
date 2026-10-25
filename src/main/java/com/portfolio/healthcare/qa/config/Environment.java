package com.portfolio.healthcare.qa.config;

/**
 * The environments the framework can target.
 *
 * <p>{@link #LIVE} is the default ({@code app.example.test} /
 * {@code api.example.test}); {@link #DEV} is the staging app
 * ({@code app.example.test}). Modelled as a type-safe enum so a stray
 * {@code -Denv=<typo>} fails fast with a clear message; {@link #key()} maps to
 * the {@code config/<key>.properties} resource on the classpath.</p>
 */
public enum Environment {

    LIVE("live"),
    DEV("dev");

    private final String key;

    Environment(String key) {
        this.key = key;
    }

    /** The lowercase token used both on the command line and as the properties filename. */
    public String key() {
        return key;
    }

    /**
     * Resolves a raw string (from {@code -Denv}, an env var, or a default) to an
     * {@link Environment}, case-insensitively.
     *
     * @throws IllegalArgumentException if the value matches no known environment
     */
    public static Environment from(String value) {
        if (value != null) {
            for (Environment environment : values()) {
                if (environment.key.equalsIgnoreCase(value.trim())) {
                    return environment;
                }
            }
        }
        throw new IllegalArgumentException(
                "Unknown environment '" + value + "'. Expected one of: live | dev");
    }
}
