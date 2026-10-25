package com.portfolio.healthcare.qa.dataproviders;

import com.portfolio.healthcare.qa.models.UserRole;
import org.testng.annotations.DataProvider;

/**
 * TestNG data providers for role-parameterised tests.
 *
 * <p>{@link #authenticatableRoles()} yields the roles with credentials valid on
 * both environments (dev and live): Surgeon, Hospital Admin and Registry Admin. A
 * valid-login test iterating this provider covers all three.</p>
 */
public class RoleDataProvider {

    @DataProvider(name = "authenticatableRoles")
    public static Object[][] authenticatableRoles() {
        return new Object[][]{
                {UserRole.SURGEON},
                {UserRole.HOSPITAL_ADMIN},
                {UserRole.Registry_ADMIN}
        };
    }
}
