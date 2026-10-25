package com.portfolio.healthcare.qa.config;

import org.aeonbits.owner.Config;

/**
 * Typed, environment-aware framework configuration.
 *
 * <p>This is an <b>Owner</b> config interface: you declare <i>what</i> config
 * exists as strongly-typed accessor methods, and Owner generates a proxy that
 * resolves values at runtime. Benefits over a hand-rolled {@code Properties}
 * reader: type safety, defaults, and a declarative source-precedence chain.</p>
 *
 * <h3>Source precedence (Chain of Responsibility)</h3>
 * Values are resolved top-to-bottom; the first source that has the key wins:
 * <ol>
 *   <li>{@code system:properties} &mdash; JVM {@code -Dkey=value} (CLI override)</li>
 *   <li>{@code system:env} &mdash; environment variables (CI / Docker override)</li>
 *   <li>{@code classpath:config/<env>.properties} &mdash; the checked-in file</li>
 * </ol>
 * The {@code ${env}} token is substituted from the factory property set by
 * {@link ConfigManager} before the interface is created.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "system:env",
        "classpath:config/${env}.properties"
})
public interface FrameworkConfig extends Config {

    @Key("env")
    @DefaultValue("dev")
    String env();

    // --- Application under test ---------------------------------------------
    @Key("app.base.url")
    String appBaseUrl();

    @Key("login.path")
    @DefaultValue("/healthcare/login")
    String loginPath();

    @Key("frontend.base.url")
    String frontendBaseUrl();

    @Key("api.base.url")
    String apiBaseUrl();

    // --- Browser / driver settings ------------------------------------------
    @Key("browser")
    @DefaultValue("chromium")
    String browser();

    @Key("headless")
    @DefaultValue("false")
    boolean headless();

    @Key("slowmo")
    @DefaultValue("0")
    double slowMo();

    @Key("timeout.ms")
    @DefaultValue("30000")
    int timeoutMs();

    @Key("viewport.width")
    @DefaultValue("1440")
    int viewportWidth();

    @Key("viewport.height")
    @DefaultValue("900")
    int viewportHeight();

    // --- Environment behaviour ----------------------------------------------
    /**
     * Whether tests soft-delete the records they create at the end of a run.
     * {@code true} on live (keep the environment clean); {@code false} on dev,
     * where left-behind records are wanted for manual inspection.
     */
    @Key("data.cleanup")
    @DefaultValue("true")
    boolean dataCleanup();

    /**
     * Whether the Surgeon can register a case directly (hospital auto-assigned).
     * {@code true} on live; {@code false} on dev, where the surgeon's Hospital /
     * Center picker is broken and cases must be created via the Hospital Admin
     * flow (hospital pre-filled + an explicit Operating Surgeon).
     */
    @Key("surgeon.creates.directly")
    @DefaultValue("true")
    boolean surgeonCreatesDirectly();

    /**
     * Operating surgeon to attribute created cases to in the Hospital Admin
     * create flow. Unused when {@link #surgeonCreatesDirectly()} is {@code true}.
     */
    @Key("operating.surgeon.name")
    @DefaultValue("")
    String operatingSurgeonName();

    // --- Role credentials ----------------------------------------------------
    @Key("surgeon.email")
    String surgeonEmail();

    @Key("surgeon.password")
    String surgeonPassword();

    @Key("hospitaladmin.email")
    String hospitalAdminEmail();

    @Key("hospitaladmin.password")
    String hospitalAdminPassword();

    @Key("healthcareadmin.email")
    String healthcareAdminEmail();

    @Key("healthcareadmin.password")
    String healthcareAdminPassword();

    @Key("superadmin.email")
    String superAdminEmail();

    @Key("superadmin.password")
    String superAdminPassword();

    // NOTE: composed values (e.g. full login URL) intentionally live in
    // ConfigManager, not here. Owner treats every interface method as a key
    // lookup, so a `default` method would be intercepted and return null
    // instead of running its body.
}
