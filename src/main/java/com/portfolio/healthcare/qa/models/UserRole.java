package com.portfolio.healthcare.qa.models;

import com.portfolio.healthcare.qa.config.FrameworkConfig;

/**
 * The application roles the framework can authenticate as, each knowing how to
 * pull its own credentials from configuration.
 *
 * <p>Keeping the config lookup here (rather than scattering
 * {@code config.surgeonEmail()} across tests) means a data provider can iterate
 * roles generically and a single test body covers all of them.</p>
 *
 * <p>Note: the application has four roles - Surgeon, Hospital Admin, Registry Admin
 * and Super Admin (the platform operator who manages the helpline console at
 * {@code /admin/helpline}). Surgeon/Hospital Admin/Registry Admin credentials are
 * valid on both environments; Super Admin is used by the helpline tests on
 * live.</p>
 */
public enum UserRole {

    SURGEON("Surgeon"),
    HOSPITAL_ADMIN("Hospital Admin"),
    Registry_ADMIN("Registry Admin"),
    SUPER_ADMIN("Super Admin");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String email(FrameworkConfig config) {
        return switch (this) {
            case SURGEON -> config.surgeonEmail();
            case HOSPITAL_ADMIN -> config.hospitalAdminEmail();
            case Registry_ADMIN -> config.healthcareAdminEmail();
            case SUPER_ADMIN -> config.superAdminEmail();
        };
    }

    public String password(FrameworkConfig config) {
        return switch (this) {
            case SURGEON -> config.surgeonPassword();
            case HOSPITAL_ADMIN -> config.hospitalAdminPassword();
            case Registry_ADMIN -> config.healthcareAdminPassword();
            case SUPER_ADMIN -> config.superAdminPassword();
        };
    }
}
