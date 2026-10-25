package com.portfolio.healthcare.qa.tests.patients;

import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.patients.EditPatientPage;
import com.portfolio.healthcare.qa.pages.patients.NewPatientPage;
import com.portfolio.healthcare.qa.pages.patients.PatientListPage;
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

/**
 * Module 2 - Patient & Surgery Management (Surgeon-scoped).
 *
 * Coverage in this class:
 *   TC-PAT-002  Auto-calculate BMI on height/weight entry (+ reactivity)
 *   TC-PAT-004  Patient list search (match + empty state)
 *
 * Deferred (need create/edit flows or more DOM): TC-PAT-001 (full registration -
 * README documents a known 500 on submission), TC-PAT-003 (drafts),
 * TC-PAT-005 (edit), TC-PAT-006 (duplicate id), TC-PAT-007 (procedure-specific).
 */
@Epic("Patient & Surgery")
@Feature("Patient Registration")
public class PatientTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(PatientTest.class);

    /** Hospital-Patient-IDs created by this class, for cleanup. */
    private final List<String> createdHospitalPatientIds = new CopyOnWriteArrayList<>();

    @Test(description = "TC-PAT-002: BMI auto-calculates from height and pre-op weight")
    @Story("TC-PAT-002 Auto-calculate BMI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Height 170cm + Weight 110kg -> app shows 38.1 kg/m². NOTE: the app "
            + "rounds BMI to 1 decimal place; the QA doc's expected '38.06' assumes 2 "
            + "decimals and should be corrected to '38.1'.")
    public void bmiAutoCalculatesFromHeightAndWeight() {
        loginAs(UserRole.SURGEON);
        NewPatientPage form = new NewPatientPage(page()).open()
                .enterHeightCm("170")
                .enterPreOpWeightKg("110");

        String bmi = form.calculatedBmiText();
        Assert.assertTrue(bmi.contains("38.1"),
                "Expected calculated BMI '38.1 kg/m²' for 170cm/110kg, but was: " + bmi);
    }

    @Test(description = "TC-PAT-002: BMI recalculates reactively when weight changes")
    @Story("TC-PAT-002 Auto-calculate BMI")
    @Severity(SeverityLevel.NORMAL)
    @Description("Changing pre-op weight from 110kg to 88kg (height 170cm) updates BMI "
            + "from 38.1 to 30.4 kg/m² without a form submission.")
    public void bmiRecalculatesWhenWeightChanges() {
        loginAs(UserRole.SURGEON);
        NewPatientPage form = new NewPatientPage(page()).open()
                .enterHeightCm("170")
                .enterPreOpWeightKg("110");
        Assert.assertTrue(form.calculatedBmiText().contains("38.1"), "Precondition BMI mismatch.");

        form.enterPreOpWeightKg("88");
        String updated = form.calculatedBmiText();
        Assert.assertTrue(updated.contains("30.4"),
                "Expected BMI to update to '30.4 kg/m²' after weight change, but was: " + updated);
    }

    @Test(description = "TC-PAT-004: Patient search filters the grid and shows an empty state")
    @Story("TC-PAT-004 Patient search")
    @Severity(SeverityLevel.NORMAL)
    @Description("Searching an existing registry ID narrows the grid to that record; "
            + "a non-matching term yields zero rows and a 'No patients found.' message.")
    public void patientSearchFiltersAndHandlesNoResults() {
        loginAs(UserRole.SURGEON);
        PatientListPage list = new PatientListPage(page()).open();

        Assert.assertTrue(list.rowCount() > 0,
                "Precondition failed: expected at least one patient in the registry to search.");

        String registryId = list.firstRowRegistryId();
        list.search(registryId).waitForRowCount(1);
        Assert.assertEquals(list.rowCount(), 1,
                "Expected exactly one row after searching for a specific registry ID: " + registryId);

        list.search("ZZZ-NO-MATCH-999").waitForNoResults();
        Assert.assertTrue(list.isNoResultsShown(),
                "Expected a 'No patients found.' empty state for a non-matching search.");
    }

    @Test(description = "TC-PAT-002b: Ideal Body Weight auto-calculates from height")
    @Story("TC-PAT-002 Auto-calculations")
    @Severity(SeverityLevel.NORMAL)
    @Description("Height 170cm -> Ideal Body Weight 72.3 kg (25 × (h/100)²), shown in a "
            + "read-only field. Also rounded to 1 decimal place, like BMI.")
    public void idealBodyWeightAutoCalculatesFromHeight() {
        loginAs(UserRole.SURGEON);
        NewPatientPage form = new NewPatientPage(page()).open().enterHeightCm("170");

        String ideal = form.idealBodyWeightValue();
        Assert.assertTrue(ideal.contains("72.3"),
                "Expected Ideal Body Weight '72.3' for 170cm, but was: " + ideal);
    }

    @Test(description = "TC-PAT-001: Register a new patient & surgical record end-to-end")
    @Story("TC-PAT-001 Register new patient")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Completes the full wizard (Patient Info -> Comorbidities -> Surgical Details "
            + "-> Review) and submits. Asserts the redirect to the patient list and that the new "
            + "record is searchable. On live the surgeon's own hospital is auto-assigned (no "
            + "Hospital / Center picker), so the case is created directly under the logged-in "
            + "surgeon. Creates a real record each run (cleaned up afterwards).")
    public void registerNewPatientEndToEnd() {
        loginAs(patientCreateRole());
        String hospitalPatientId = "QA" + (System.currentTimeMillis() % 1_000_000);

        fillValidCase(hospitalPatientId).submitCase();
        createdHospitalPatientIds.add(hospitalPatientId);   // role-correct cleanup (see @AfterClass)

        // Verify the record was created and is now searchable in the registry.
        PatientListPage list = new PatientListPage(page()).open();
        list.search(hospitalPatientId).waitForRowCount(1);
        Assert.assertEquals(list.rowCount(), 1,
                "Expected the newly created patient (" + hospitalPatientId + ") to be searchable in the registry.");
    }

    @Test(description = "TC-PAT-001/007: Surgical Details enforces required fields before advancing")
    @Feature("Patient Registration")
    @Story("TC-PAT-007 Mandatory field validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("With Patient Info completed, the Surgical Details step refuses to advance until "
            + "procedure, approach and surgery date are provided, surfacing specific validation "
            + "errors. No record is created (no submission).")
    public void surgicalDetailsEnforcesRequiredFields() {
        loginAs(patientCreateRole());
        String uniqueId = "QA" + (System.currentTimeMillis() % 1_000_000);

        NewPatientPage form = new NewPatientPage(page()).open()
                .enterHospitalPatientId(uniqueId)
                .enterAge("45")
                .selectGender("Male")
                .enterHeightCm("170")
                .enterPreOpWeightKg("110")
                .nextToComorbidities()
                .nextToSurgicalDetails();
        // On the Hospital Admin flow (dev) the surgeon is also required; selecting
        // it first isolates the procedure/date enforcement asserted below.
        String operatingSurgeon = ConfigManager.config().operatingSurgeonName();
        if (!operatingSurgeon.isBlank()) {
            form.selectOperatingSurgeon(operatingSurgeon);
        }
        form.attemptNext();

        Assert.assertTrue(form.hasValidationError("Please select procedure type"),
                "Expected a 'procedure type' required-field error on Surgical Details.");
        Assert.assertTrue(form.hasValidationError("Surgery date is required"),
                "Expected a 'Surgery date is required' error on Surgical Details.");
    }

    @Test(description = "TC-PAT-006: Duplicate Hospital Patient ID is rejected")
    @Story("TC-PAT-006 Duplicate patient ID")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Creates a patient, then submits a second patient with the SAME Hospital Patient "
            + "ID. The second is rejected with a 409 and an inline 'already exists' error and the "
            + "user stays on the create form (no duplicate created). Self-contained (no seed-data "
            + "dependency). NOTE: uniqueness is enforced system-wide (an id is not reusable even "
            + "after a record is deleted), not merely per-hospital as the QA doc states.")
    public void duplicateHospitalPatientIdIsRejected() {
        loginAs(patientCreateRole());
        String hospitalPatientId = "QADUP" + (System.currentTimeMillis() % 1_000_000);

        // Arrange: create a patient that owns this id.
        fillValidCase(hospitalPatientId).submitCase();
        createdHospitalPatientIds.add(hospitalPatientId);

        // Act: attempt to create a second patient with the same id.
        NewPatientPage form = fillValidCase(hospitalPatientId).clickSubmitCase();

        // Assert: rejected, still on the create form.
        Assert.assertTrue(form.hasDuplicateIdError(),
                "Expected a duplicate Hospital Patient ID error for " + hospitalPatientId + ".");
        Assert.assertTrue(form.currentUrl().contains("/patients/new"),
                "Expected to stay on the create form after a duplicate-id rejection.");
    }

    @Test(description = "TC-PAT-005: Edit an existing patient record and persist the change")
    @Story("TC-PAT-005 Edit patient")
    @Severity(SeverityLevel.NORMAL)
    @Description("Creates a patient, opens its Edit form, changes Age 45 -> 50, saves, then "
            + "re-opens the Edit form and asserts the new age persisted.")
    public void editPatientPersistsChange() {
        loginAs(patientCreateRole());
        String hospitalPatientId = "QAEDIT" + (System.currentTimeMillis() % 1_000_000);

        // Arrange: create a patient to edit (self-contained, no shared-data dependency).
        fillValidCase(hospitalPatientId).submitCase();
        createdHospitalPatientIds.add(hospitalPatientId);   // role-correct cleanup

        PatientListPage list = new PatientListPage(page()).open();
        list.search(hospitalPatientId).waitForRowCount(1);
        String editHref = list.firstRowEditHref();

        // Act: change age and save.
        EditPatientPage edit = new EditPatientPage(page()).open(editHref);
        Assert.assertEquals(edit.currentAge(), "45", "Precondition: created patient should be age 45.");
        edit.setAge("50").save();

        // Assert: re-open the edit form; the change persisted.
        EditPatientPage reopened = new EditPatientPage(page()).open(editHref);
        Assert.assertEquals(reopened.currentAge(), "50",
                "Expected the edited age (50) to persist after saving.");
    }

    // --- Helpers / cleanup ---------------------------------------------------

    /**
     * Fills a complete, valid case (Patient Info -> Surgical Details), ready to
     * submit. On live the logged-in surgeon's hospital is auto-assigned; on dev
     * the Hospital Admin flow requires an explicit Operating Surgeon
     * ({@code operating.surgeon.name}), selected here when configured.
     */
    private NewPatientPage fillValidCase(String hospitalPatientId) {
        NewPatientPage form = new NewPatientPage(page()).open()
                .enterHospitalPatientId(hospitalPatientId)
                .enterAge("45")
                .selectGender("Male")
                .enterHeightCm("170")
                .enterPreOpWeightKg("110")
                .nextToComorbidities()
                .nextToSurgicalDetails();
        String operatingSurgeon = ConfigManager.config().operatingSurgeonName();
        if (!operatingSurgeon.isBlank()) {
            form.selectOperatingSurgeon(operatingSurgeon);
        }
        return form.selectProcedure("Sleeve")
                .selectApproach("Laparoscopic")
                .enterSurgeryDate("2026-07-01");
    }

    /**
     * Deletes the patients this class created, resolving each Hospital Patient ID
     * to its internal id via the API (run as the role that owns them). Skipped
     * entirely when {@code data.cleanup=false} (dev), so records are left for
     * inspection. Cleanup never fails the run.
     */
    @AfterClass(alwaysRun = true)
    public void cleanupCreatedPatients() {
        if (createdHospitalPatientIds.isEmpty()) {
            return;
        }
        if (!dataCleanupEnabled()) {
            log.info("data.cleanup=false: leaving {} created patient(s) on this environment: {}",
                    createdHospitalPatientIds.size(), createdHospitalPatientIds);
            createdHospitalPatientIds.clear();
            return;
        }
        try {
            PatientApiClient.runAs(patientCreateRole(), api -> {
                for (String hospitalPatientId : createdHospitalPatientIds) {
                    api.findIdByHospitalPatientId(hospitalPatientId).ifPresentOrElse(
                            id -> log.info("Cleanup: {} ({}) -> {}", hospitalPatientId, id,
                                    api.delete(id) ? "deleted" : "DELETE FAILED"),
                            () -> log.warn("Cleanup: {} not found in API (already gone?)", hospitalPatientId));
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Fixture cleanup encountered an error (ignored): {}", e.getMessage());
        } finally {
            createdHospitalPatientIds.clear();
        }
    }
}
