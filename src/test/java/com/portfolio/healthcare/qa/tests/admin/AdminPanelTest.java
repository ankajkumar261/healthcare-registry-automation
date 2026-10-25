package com.portfolio.healthcare.qa.tests.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.admin.AdminHospitalsPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Module 9 - Admin Panel & Multi-Tenant Setup.
 *
 * <p>Administered by the <b>Registry Admin</b> at {@code /admin/hospitals} and
 * {@code /admin/users}. Read-write against production, so the one mutating test
 * (hospital creation) cleans up after itself via the admin API
 * (see {@link #cleanupHospitals()}).</p>
 *
 * <p>Coverage in this class:</p>
 * <ul>
 *   <li>TC-ADM-001  Create a new hospital -> appears in the registry (UI + API)</li>
 *   <li>TC-ADM-002  Deactivate a user -> documented gap (skipped)</li>
 * </ul>
 *
 * <p>TC-ADM-003 (audit-log verification) was removed: the application has no
 * audit-log feature at all (every audit route/API 404s), so there is nothing to
 * test - reported to the product team as an absent capability.</p>
 *
 * <p><b>Findings vs the QA doc (verified live):</b></p>
 * <ul>
 *   <li>Admin functions belong to the <b>Registry Admin</b>, not a "Super Admin":
 *       Super Admin is <b>denied</b> on {@code /admin/hospitals} and
 *       {@code /admin/users} (it only owns the helpline console), and the admin
 *       APIs require {@code healthcare_admin}.</li>
 *   <li>TC-ADM-001: the create form has only Name / City / State / Code - there is
 *       <b>no "assign Hospital Admin email"</b> field, so no welcome email is
 *       triggered from hospital creation (contrary to the doc).</li>
 *   <li>TC-ADM-002: user administration would create a real account and send an
 *       <b>invite email</b> on the production system; the deactivation control is
 *       not exposed on the tested API paths; and the doc's "blocked login (403)"
 *       cannot be verified because invited users have no password until they use
 *       the emailed setup link. Skipped and documented rather than polluting prod
 *       with unverifiable side effects.</li>
 * </ul>
 */
@Epic("Admin Panel")
@Feature("Multi-Tenant Setup")
public class AdminPanelTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(AdminPanelTest.class);
    private static final String CODE_PREFIX = "QAH";

    /** Codes of hospitals this class created, for self-contained cleanup. */
    private final List<String> createdCodes = new CopyOnWriteArrayList<>();

    @Test(description = "TC-ADM-001: Create a new hospital and see it in the registry")
    @Story("TC-ADM-001 Create new hospital")
    @Severity(SeverityLevel.CRITICAL)
    @Description("As Registry Admin, add a hospital (Name/City/State/Code) via /admin/hospitals. The new "
            + "hospital then appears in the searchable table and is present via GET /api/admin/hospitals. "
            + "NOTE: hospital administration is an Registry-Admin capability (not Super Admin), the create "
            + "form has no admin-email assignment, and the API rejects a hospital NAME containing digits "
            + "(invalid_string) - so the test name uses letters only while the code carries the digits.")
    public void createNewHospital() {
        loginAs(UserRole.Registry_ADMIN);
        long stamp = System.currentTimeMillis() % 1_000_000;
        String code = CODE_PREFIX + stamp;                       // codes may contain digits
        String name = "QA Test Hospital " + digitsToLetters(stamp);   // names must be digit-free
        createdCodes.add(code);

        AdminHospitalsPage hospitals = new AdminHospitalsPage(page()).open()
                .addHospital(name, "QA City", "QA State", code);

        hospitals.search(code);
        Assert.assertTrue(hospitals.showsHospital(name),
                "Expected the newly created hospital to appear in the table after searching its code.");

        Assert.assertNotNull(findHospitalIdByCode(code),
                "Expected the created hospital (" + code + ") to be present via the admin API.");
    }

    @Test(description = "TC-ADM-002: Deactivate a user account")
    @Story("TC-ADM-002 Deactivate user account")
    @Severity(SeverityLevel.NORMAL)
    @Description("Documented gap: user administration on this build creates a real account and sends "
            + "an invite email on production; the deactivation control is not exposed on the tested "
            + "admin API paths; and the doc's 'blocked login (403)' outcome is unverifiable because an "
            + "invited user has no password until the emailed setup link is used. Skipped rather than "
            + "creating unverifiable side effects on the live system.")
    public void deactivateUserAccount() {
        throw new SkipException("TC-ADM-002: deactivating a user would create a real account + invite "
                + "email on production and the 'blocked login (403)' result cannot be verified "
                + "(invited users have no password). Documented as a gap pending a QA/staging user.");
    }

    // --- Fixtures / helpers --------------------------------------------------

    /** Maps each digit to a letter (0->a ... 9->j) so a value is digit-free. */
    private static String digitsToLetters(long value) {
        StringBuilder sb = new StringBuilder();
        for (char c : Long.toString(value).toCharArray()) {
            sb.append((char) ('a' + (c - '0')));
        }
        return sb.toString();
    }

    private String findHospitalIdByCode(String code) {
        JsonNode hospitals = withApi(api -> api.getData("/api/admin/hospitals"));
        for (JsonNode h : hospitals) {
            if (code.equals(h.path("code").asText())) {
                return h.path("id").asText();
            }
        }
        return null;
    }

    private <T> T withApi(Function<PatientApiClient, T> fn) {
        return fn.apply(PatientApiClient.usingPage(page()));
    }

    /**
     * Deletes every hospital this class created (matched by our {@code QAH} code
     * prefix), via the Registry-Admin admin API. Cleanup never fails the run.
     */
    @AfterClass(alwaysRun = true)
    public void cleanupHospitals() {
        if (createdCodes.isEmpty()) {
            return;
        }
        if (!dataCleanupEnabled()) {
            log.info("data.cleanup=false: leaving created hospitals: {}", createdCodes);
            createdCodes.clear();
            return;
        }
        try {
            PatientApiClient.runAs(UserRole.Registry_ADMIN, api -> {
                JsonNode hospitals = api.getData("/api/admin/hospitals");
                for (JsonNode h : hospitals) {
                    if (h.path("code").asText().startsWith(CODE_PREFIX)) {
                        String id = h.path("id").asText();
                        log.info("Cleanup: deleting hospital {} ({}) -> {}",
                                h.path("code").asText(), id,
                                api.deletePath("/api/admin/hospitals/" + id) ? "ok" : "failed");
                    }
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Hospital cleanup encountered an error (ignored): {}", e.getMessage());
        } finally {
            createdCodes.clear();
        }
    }
}
