package com.portfolio.healthcare.qa.tests.labs;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.data.CreatedPatients;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.patients.FollowUpPage;
import com.portfolio.healthcare.qa.pages.patients.PatientDetailPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Module 4 - Blood Investigations & Diagnostic Lab Logs (Surgeon-scoped).
 *
 * <p>Each test seeds its own operated patient via the API and then drives the
 * lab-entry flow through the UI, so tests are self-contained and the created
 * records are cleaned up by the suite's {@code PatientCleanupListener} (and by
 * {@link #cleanupSeededPatients()} when the class is run in isolation).</p>
 *
 * <p>Coverage in this class:</p>
 * <ul>
 *   <li>TC-LAB-001  Enter post-op blood-investigation parameters -> persisted and rendered</li>
 *   <li>TC-LAB-002  Out-of-range lab values are flagged (abnormal-value alert)</li>
 *   <li>TC-LAB-003  Serial lab entries render a trajectory chart</li>
 * </ul>
 *
 * <p><b>How this app models "blood investigations" (verified against the live
 * app, deviates from the QA doc):</b></p>
 * <ul>
 *   <li>There is <b>no separate "Lab" tab or "Add Blood Test" screen</b>. The
 *       lab parameters (Hemoglobin, Ferritin, Vitamin B12, Vitamin D, Calcium,
 *       Albumin, HbA1c) are optional fields on the <b>follow-up form</b>, and are
 *       stored on the follow-up record - not in a distinct lab entity.</li>
 *   <li>On the patient detail page the recorded values render in a
 *       <b>"Nutrition Parameters"</b> section, one card per parameter, each shown
 *       against its reference range with a <b>Low / Normal / High</b> status
 *       badge. That badge is the abnormal-value flag.</li>
 *   <li>The detail page's separate <b>"Blood Investigation"</b> panel is fed by a
 *       different, admin-managed entity ({@code patient.bloodInvestigations});
 *       {@code POST /api/blood-investigations} requires healthcare_admin/superadmin, so
 *       a surgeon cannot populate it - it stays "No blood investigation results
 *       recorded." for surgeon-entered labs. {@code GET /api/blood-investigations}
 *       is only the reference-range <b>catalog</b> (parameter, unit, normal
 *       min/max).</li>
 *   <li>TC-LAB-002: the doc's thresholds ("Vitamin D &lt; 10", "Hemoglobin
 *       &lt; 7") describe a "critical" tier the app does not have. The app flags
 *       any value outside the parameter's reference range (e.g. Hemoglobin
 *       &lt; 12, Vitamin D &lt; 30) with a "Low"/"High" badge - there is no
 *       distinct critical level. The chosen test values are below even the doc's
 *       thresholds, so they are unambiguously flagged.</li>
 * </ul>
 */
@Epic("Blood Investigations")
@Feature("Diagnostic Lab Logs")
public class LabInvestigationTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(LabInvestigationTest.class);

    /** Internal ids of patients this class seeded, for self-contained cleanup. */
    private final List<String> seededPatientIds = new CopyOnWriteArrayList<>();

    @Test(description = "TC-LAB-001: Post-op blood-investigation parameters are recorded and rendered")
    @Story("TC-LAB-001 Enter blood investigation parameters")
    @Severity(SeverityLevel.NORMAL)
    @Description("On the follow-up form, enter Hemoglobin, HbA1c, Vitamin B12, Ferritin (plus "
            + "Vitamin D, Calcium, Albumin) and save. The values persist on the follow-up (verified "
            + "via the API) and render in the patient detail 'Nutrition Parameters' section, each "
            + "against its reference range. FINDING: labs are captured on the follow-up form, not a "
            + "separate 'Add Blood Test' screen; there is no distinct lab entity.")
    public void enterBloodInvestigationParameters() {
        loginAs(UserRole.SURGEON);
        PatientApiClient.PatientFixture fixture = seedOperatedPatient();

        PatientDetailPage detail = new FollowUpPage(page()).open(fixture.patientId())
                .enterFollowUpDate("2027-01-15")
                .selectInterval("6 months")
                .enterWeightKg("88")
                .enterHba1c("5.6")
                .enterHemoglobin("14")
                .enterFerritin("120")
                .enterVitaminB12("400")
                .enterVitaminD("45")
                .enterCalcium("9.5")
                .enterAlbumin("4.2")
                .save();

        // Rendered in the Nutrition Parameters section, within reference range.
        Assert.assertTrue(detail.showsNutritionParameters(),
                "Expected a 'Nutrition Parameters' section after recording lab values.");
        Assert.assertTrue(detail.nutritionParameterShowsValue("Hemoglobin", "14"),
                "Expected the recorded Hemoglobin (14) to render on the detail page.");
        Assert.assertEquals(detail.nutritionBadge("Hemoglobin"), "Normal",
                "Expected Hemoglobin 14 g/dL (ref 12-16) to be badged 'Normal'.");
        Assert.assertEquals(detail.nutritionBadge("Vitamin B12"), "Normal",
                "Expected Vitamin B12 400 pg/mL (ref 200-900) to be badged 'Normal'.");

        // Persisted on the follow-up record (the strong, data-level assertion).
        JsonNode fu = withApi(api -> api.followUps(fixture.patientId()).path(0));
        Assert.assertEquals(fu.path("hemoglobin").asDouble(), 14.0, 0.001, "Stored hemoglobin mismatch.");
        Assert.assertEquals(fu.path("ferritin").asDouble(), 120.0, 0.001, "Stored ferritin mismatch.");
        Assert.assertEquals(fu.path("vitaminB12").asDouble(), 400.0, 0.001, "Stored vitaminB12 mismatch.");
        Assert.assertEquals(fu.path("hbA1c").asDouble(), 5.6, 0.001, "Stored HbA1c mismatch.");
    }

    @Test(description = "TC-LAB-002: Out-of-range lab values are flagged as abnormal")
    @Story("TC-LAB-002 Abnormal lab value alert")
    @Severity(SeverityLevel.NORMAL)
    @Description("Enter an abnormally low Hemoglobin (6.5 g/dL) and Vitamin D (8 ng/mL). Both cards "
            + "in the Nutrition Parameters section are badged 'Low', while an in-range value "
            + "(Ferritin 120) is 'Normal'. FINDING: the app flags anything outside the reference "
            + "range (Hb < 12, Vit D < 30) with a Low/High badge; it has no separate 'critical' tier, "
            + "so the doc's Hb < 7 / Vit D < 10 thresholds are covered by (and stricter than) the "
            + "app's range check. Values chosen sit below both.")
    public void abnormalLabValuesAreFlagged() {
        loginAs(UserRole.SURGEON);
        PatientApiClient.PatientFixture fixture = seedOperatedPatient();

        PatientDetailPage detail = new FollowUpPage(page()).open(fixture.patientId())
                .enterFollowUpDate("2027-01-15")
                .selectInterval("6 months")
                .enterWeightKg("88")
                .enterHemoglobin("6.5")   // ref 12-16 -> Low (also < doc's 7)
                .enterVitaminD("8")       // ref 30-100 -> Low (also < doc's 10)
                .enterFerritin("120")     // ref 30-400 -> Normal (control)
                .save();

        Assert.assertEquals(detail.nutritionBadge("Hemoglobin"), "Low",
                "Expected Hemoglobin 6.5 g/dL to be flagged 'Low'.");
        Assert.assertEquals(detail.nutritionBadge("Vitamin D"), "Low",
                "Expected Vitamin D 8 ng/mL to be flagged 'Low'.");
        Assert.assertTrue(detail.isNutritionParameterAbnormal("Hemoglobin"),
                "Expected Hemoglobin to read as abnormal (out of range).");
        Assert.assertFalse(detail.isNutritionParameterAbnormal("Ferritin"),
                "Expected an in-range Ferritin (120) to NOT be flagged abnormal.");
    }

    @Test(description = "TC-LAB-003: Serial lab entries render a trajectory chart")
    @Story("TC-LAB-003 Serial lab trajectory chart")
    @Severity(SeverityLevel.MINOR)
    @Description("Log two follow-ups at different intervals (3 months, then 6 months) with declining "
            + "HbA1c and differing lab values. The detail page renders the 'HbA1c Improvement' trend "
            + "chart and both interval nodes on the timeline; the API confirms both follow-ups carry "
            + "their own lab values. NOTE: pre-op is a separate baseline field (surgery.preOpHbA1c), "
            + "so the serial trajectory is built from the follow-up entries.")
    public void serialLabEntriesRenderTrajectory() {
        loginAs(UserRole.SURGEON);
        PatientApiClient.PatientFixture fixture = seedOperatedPatient();

        // First serial point: 3 months.
        new FollowUpPage(page()).open(fixture.patientId())
                .enterFollowUpDate("2026-10-01")
                .selectInterval("3 months")
                .enterWeightKg("95")
                .enterHba1c("6.5")
                .enterHemoglobin("13")
                .save();

        // Second serial point: 6 months.
        PatientDetailPage detail = new FollowUpPage(page()).open(fixture.patientId())
                .enterFollowUpDate("2027-01-01")
                .selectInterval("6 months")
                .enterWeightKg("88")
                .enterHba1c("5.5")
                .enterHemoglobin("15")
                .save();

        Assert.assertTrue(detail.showsHba1cTrendChart(),
                "Expected the 'HbA1c Improvement' trend chart to render for serial entries.");
        Assert.assertTrue(detail.showsTimelineInterval("3 mo"),
                "Expected the 3-month node on the follow-up timeline.");
        Assert.assertTrue(detail.showsTimelineInterval("6 mo"),
                "Expected the 6-month node on the follow-up timeline.");

        // Both follow-ups persisted their own serial lab values.
        JsonNode followUps = withApi(api -> api.followUps(fixture.patientId()));
        Assert.assertEquals(followUps.size(), 2, "Expected two serial follow-up entries.");
        for (JsonNode fu : followUps) {
            Assert.assertTrue(fu.path("hbA1c").isNumber(),
                    "Expected each serial follow-up to carry an HbA1c value.");
            Assert.assertTrue(fu.path("hemoglobin").isNumber(),
                    "Expected each serial follow-up to carry a Hemoglobin value.");
        }
    }

    // --- Fixtures / helpers --------------------------------------------------

    /**
     * Seeds an operated patient (height 170 cm, pre-op 110 kg, Sleeve) via the
     * API and registers it for cleanup. See {@link FollowUpPage} for why labs are
     * driven through the follow-up form.
     */
    private PatientApiClient.PatientFixture seedOperatedPatient() {
        return withApi(api -> {
            String hospitalPatientId = "QALAB" + (System.currentTimeMillis() % 1_000_000);
            PatientApiClient.PatientFixture fixture = api.createOperatedPatient(
                    hospitalPatientId, 45, "Male", 170, 110, "Sleeve", "Laparoscopic", "2026-07-01");
            CreatedPatients.register(hospitalPatientId);   // suite cleanup soft-deletes it
            seededPatientIds.add(fixture.patientId());      // class-level cleanup (below)
            return fixture;
        });
    }

    /**
     * Deletes the patients this class seeded. Complements the suite-level
     * {@code PatientCleanupListener} so the class stays self-cleaning when run in
     * isolation (e.g. {@code mvn test -Dtest=LabInvestigationTest}). A
     * double-delete is harmless (the second is a 404 no-op) and cleanup never
     * fails the run.
     */
    @AfterClass(alwaysRun = true)
    public void cleanupSeededPatients() {
        if (seededPatientIds.isEmpty()) {
            return;
        }
        if (!dataCleanupEnabled()) {
            log.info("data.cleanup=false: leaving {} seeded patient(s) on this environment: {}",
                    seededPatientIds.size(), seededPatientIds);
            seededPatientIds.clear();
            return;
        }
        try {
            PatientApiClient.runAs(UserRole.SURGEON, api -> {
                for (String id : seededPatientIds) {
                    log.info("Cleanup: deleting seeded patient {} -> {}", id, api.delete(id) ? "ok" : "already gone");
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Fixture cleanup encountered an error (ignored): {}", e.getMessage());
        } finally {
            seededPatientIds.clear();
        }
    }

    /**
     * Runs a block with an API client backed by the current authenticated page.
     * Requires the caller to have logged in first (the in-browser fetch reuses
     * the page's session and origin).
     */
    private <T> T withApi(Function<PatientApiClient, T> fn) {
        return fn.apply(PatientApiClient.usingPage(page()));
    }
}
