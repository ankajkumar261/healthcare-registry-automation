package com.portfolio.healthcare.qa.tests.imports;

import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.data.CreatedPatients;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.patients.BulkImportPage;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Module 5 - Bulk Data Import & Validation Engine (Surgeon-scoped).
 *
 * <p>Drives the "Bulk Import Cases" flow at {@code /patients/import} with CSV
 * files generated at runtime (using the real template header row), so the tests
 * are self-contained. Imported records are cleaned up by the suite's
 * {@code PatientCleanupListener} and by {@link #cleanupCreated()} when the class
 * runs in isolation.</p>
 *
 * <p>Coverage in this class:</p>
 * <ul>
 *   <li>TC-IMP-001  Reject a non-spreadsheet upload</li>
 *   <li>TC-IMP-002  Validate and import a correct CSV -> records created</li>
 *   <li>TC-IMP-003  Highlight invalid rows with per-cell errors</li>
 *   <li>TC-IMP-004  Partial import: skip invalid rows (adapted from the doc's rollback)</li>
 *   <li>TC-IMP-005  Large-file / performance -> documented gap (skipped)</li>
 * </ul>
 *
 * <p><b>How this app models bulk import (verified live, deviations from the QA
 * doc):</b></p>
 * <ul>
 *   <li>Validation is entirely <b>client-side</b>; only on submit does the app
 *       call {@code POST /api/patients/bulk} ({@code {total, successCount,
 *       failureCount, results[]}}).</li>
 *   <li>TC-IMP-001: the rejection reads {@code "<name>" is not a spreadsheet.
 *       Upload a .xlsx, .xls or .csv file.}, not the doc's "Only .xlsx, .xls, or
 *       .csv files are allowed". It is an inline message; no preview renders.</li>
 *   <li>TC-IMP-004: the app does <b>not</b> roll back on a bad row. Invalid rows
 *       are skipped and the valid rows import (the submit button becomes
 *       "Import N valid case (skip M)"). There is no transactional fail-fast
 *       setting, and there is no API test tier in this framework, so the doc's
 *       API-integration rollback case is re-expressed as this UI skip behaviour.</li>
 *   <li>TC-IMP-005: the doc's 500-row/&lt;5s SLA case is API-tier and would
 *       create 500 real records on the live production backend; additionally the
 *       "up to 500 rows" copy is <b>not enforced</b> (501 rows still preview and
 *       import). It is therefore documented and skipped rather than faked.</li>
 * </ul>
 */
@Epic("Bulk Data Import")
@Feature("Import & Validation Engine")
public class BulkImportTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BulkImportTest.class);

    /** The bulk-template header row (verified against the downloaded template). */
    private static final String[] HEADERS = {
            "hospitalPatientId", "age", "gender", "heightCm", "phone",
            "surgeryDate (YYYY-MM-DD)", "dischargeDate (YYYY-MM-DD)", "preOpWeightKg",
            "Hospital Name", "Surgeon Name", "procedureType", "Revision Surgery", "approach",
            "operativeTimeMin", "bougieSize", "alimentaryLimbCm", "bpLimbCm", "hasDiabetes",
            "diabetesDuration", "diabetesTreatment", "diabetesInsulinDose", "preOpHbA1c",
            "hasHypertension", "hasDyslipidemia", "hasSleepApnea", "hasGerd", "hadLeak",
            "hadBleeding", "hadReoperation", "hadIcuAdmission", "hadMortality", "complicationNotes",
            "status", "Albumin (g/dL)", "Calcium (mg/dL)", "Ferritin (ng/mL)", "Haemoglobin (g/dL)",
            "Vitamin B12 (pg/mL)", "Vitamin D (ng/mL)"
    };

    private static final String HOSPITAL_NAME = "Centre for Obesity Control (Agrawal Clinic)";
    private static final String SURGEON_NAME = "Ankaj kumar";

    /** Hospital patient ids imported by this class, for self-contained cleanup. */
    private final List<String> importedHospitalPatientIds = new CopyOnWriteArrayList<>();

    @Test(description = "TC-IMP-001: A non-spreadsheet upload is rejected")
    @Story("TC-IMP-001 Reject invalid file format")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Selecting a .txt file on the bulk-import page is rejected inline with "
            + "'\"<name>\" is not a spreadsheet. Upload a .xlsx, .xls or .csv file.' and no preview "
            + "renders. NOTE: the message differs from the doc's exact wording.")
    public void rejectNonSpreadsheetUpload() {
        loginAs(UserRole.SURGEON);
        Path txt = writeTempFile("not-a-spreadsheet", ".txt", "this is plain text, not a spreadsheet");

        BulkImportPage importPage = new BulkImportPage(page()).open().selectFile(txt);

        Assert.assertTrue(importPage.showsFormatRejection(),
                "Expected a 'not a spreadsheet' rejection for a .txt upload.");
        Assert.assertFalse(importPage.showsAllRowsValid(),
                "Expected no validation preview to render for a rejected file.");
    }

    @Test(description = "TC-IMP-002: A valid CSV validates cleanly and imports all rows")
    @Story("TC-IMP-002 Validate and import correct data")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Uploading a CSV of 3 valid rows shows '3 rows parsed', 'Every row passed "
            + "validation', and the rows in the preview grid. Submitting 'Import 3 cases' creates 3 "
            + "records (POST /api/patients/bulk) and the result panel reports '3 imported / 0 failed'. "
            + "NOTE: the doc uses 10 rows; the mechanism is identical - a small count keeps the live "
            + "footprint minimal.")
    public void validateAndImportCorrectCsv() {
        loginAs(UserRole.SURGEON);
        String a = uniqueId("A");
        String b = uniqueId("B");
        String c = uniqueId("C");
        Path csv = writeCsv(List.of(validRow(a), validRow(b), validRow(c)));

        BulkImportPage importPage = new BulkImportPage(page()).open().selectFile(csv);

        Assert.assertTrue(importPage.showsRowsParsed(3), "Expected '3 rows parsed'.");
        Assert.assertTrue(importPage.showsAllRowsValid(), "Expected all 3 rows to pass validation.");
        Assert.assertTrue(importPage.previewShows(a), "Expected the first row to appear in the preview grid.");

        importPage.importAll();

        Assert.assertTrue(importPage.showsImported(3), "Expected '3 imported' in the result panel.");
        Assert.assertTrue(importPage.showsFailed(0), "Expected '0 failed' in the result panel.");

        // Confirm the records really exist in the backend.
        Assert.assertTrue(existsInApi(a), "Expected imported patient " + a + " to exist via the API.");
        Assert.assertTrue(existsInApi(c), "Expected imported patient " + c + " to exist via the API.");
    }

    @Test(description = "TC-IMP-003: Invalid rows are flagged with per-cell errors")
    @Story("TC-IMP-003 Highlight invalid rows")
    @Severity(SeverityLevel.CRITICAL)
    @Description("A CSV with one valid row and one row carrying age=-5 and gender=X renders an error "
            + "table (Row | Column | Problem) that names the offending line and fields: 'Must be "
            + "between 1 and 120 - got -5.' and '\"X\" is not Male, Female or Other.', reports '1 row "
            + "cannot be imported', and offers 'Import N valid case (skip M)'. Nothing is imported "
            + "(the test does not submit).")
    public void highlightInvalidRows() {
        loginAs(UserRole.SURGEON);
        String good = uniqueId("G");
        String bad = uniqueId("X");
        Map<String, String> badRow = validRow(bad);
        badRow.put("age", "-5");
        badRow.put("gender", "X");
        Path csv = writeCsv(List.of(validRow(good), badRow));

        BulkImportPage importPage = new BulkImportPage(page()).open().selectFile(csv);

        Assert.assertTrue(importPage.showsRowsParsed(2), "Expected '2 rows parsed'.");
        Assert.assertTrue(importPage.showsCannotImportCount(1), "Expected '1 row cannot be imported'.");
        Assert.assertTrue(importPage.showsRowError("Must be between 1 and 120"),
                "Expected the age range error for -5.");
        Assert.assertTrue(importPage.showsRowError("is not Male, Female or Other"),
                "Expected the gender error for 'X'.");
        Assert.assertTrue(importPage.offersSkipInvalidImport(),
                "Expected a 'skip invalid' import option alongside the error table.");

        // Not submitted -> nothing created. (Guard against accidental import.)
        Assert.assertFalse(existsInApi(good), "No import was submitted, so no record should exist yet.");
    }

    @Test(description = "TC-IMP-004: Importing past invalid rows skips them (no rollback)")
    @Story("TC-IMP-004 Partial import vs rollback")
    @Severity(SeverityLevel.NORMAL)
    @Description("With one valid and one invalid row, submitting 'Import 1 valid case (skip 1)' "
            + "imports only the valid row (POST /api/patients/bulk with the valid row alone) and the "
            + "result reads '1 imported / 0 failed'. FINDING: the app skips invalid rows rather than "
            + "rolling the whole batch back; the invalid row is never created.")
    public void partialImportSkipsInvalidRows() {
        loginAs(UserRole.SURGEON);
        String good = uniqueId("VG");
        String bad = uniqueId("VX");
        Map<String, String> badRow = validRow(bad);
        badRow.put("age", "-5");
        badRow.put("gender", "X");
        Path csv = writeCsv(List.of(validRow(good), badRow));

        BulkImportPage importPage = new BulkImportPage(page()).open().selectFile(csv);
        Assert.assertTrue(importPage.offersSkipInvalidImport(), "Expected the skip-invalid import option.");

        importPage.importValidSkippingInvalid();

        Assert.assertTrue(importPage.showsImported(1), "Expected exactly '1 imported'.");
        Assert.assertTrue(importPage.showsFailed(0), "Expected '0 failed' (the invalid row was skipped, not failed).");

        Assert.assertTrue(existsInApi(good), "Expected the valid row to be imported.");
        Assert.assertFalse(existsInApi(bad), "Expected the invalid row to be skipped (never created).");
    }

    @Test(description = "TC-IMP-005: Large-file (500-row) performance import")
    @Story("TC-IMP-005 Large file performance")
    @Severity(SeverityLevel.MINOR)
    @Description("Documented gap: this is an API-tier performance case in the doc. There is no API "
            + "test tier in this framework, importing 500 rows would create 500 real records on the "
            + "live production backend, and the '500-row' limit is not actually enforced by the app "
            + "(501 rows still preview and import). Skipped rather than faked.")
    public void largeFilePerformanceImport() {
        throw new SkipException("TC-IMP-005: 500-row performance import is an API-tier case not "
                + "automated here - it would create 500 live records and the 500-row cap is not "
                + "enforced by the app. Documented as a gap.");
    }

    // --- Fixtures / helpers --------------------------------------------------

    /** A fully-valid data row (all mandatory fields populated), keyed by header. */
    private Map<String, String> validRow(String hospitalPatientId) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("hospitalPatientId", hospitalPatientId);
        row.put("age", "42");
        row.put("gender", "Male");
        row.put("heightCm", "172");
        row.put("preOpWeightKg", "128");
        row.put("surgeryDate (YYYY-MM-DD)", "2026-06-01");
        row.put("Hospital Name", HOSPITAL_NAME);
        row.put("Surgeon Name", SURGEON_NAME);
        row.put("procedureType", "Sleeve");
        row.put("Revision Surgery", "0");
        row.put("approach", "Laparoscopic");
        row.put("status", "complete");
        return row;
    }

    /** A short, collision-resistant hospital patient id, tracked for cleanup. */
    private String uniqueId(String suffix) {
        String id = "QAIMP" + (System.currentTimeMillis() % 1_000_000) + suffix;
        CreatedPatients.register(id);            // suite cleanup soft-deletes it if imported
        importedHospitalPatientIds.add(id);       // class cleanup (below)
        return id;
    }

    /** Writes the header row + the given data rows to a temp CSV and returns its path. */
    private Path writeCsv(List<Map<String, String>> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(csvLine(HEADERS));
        for (Map<String, String> row : rows) {
            String[] cells = new String[HEADERS.length];
            for (int i = 0; i < HEADERS.length; i++) {
                cells[i] = row.getOrDefault(HEADERS[i], "");
            }
            lines.add(csvLine(cells));
        }
        return writeTempFile("bulk-import", ".csv", String.join("\n", lines));
    }

    private static String csvLine(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String cell = cells[i] == null ? "" : cells[i];
            sb.append(cell.contains(",") || cell.contains("\"")
                    ? '"' + cell.replace("\"", "\"\"") + '"'
                    : cell);
        }
        return sb.toString();
    }

    private Path writeTempFile(String prefix, String suffix, String content) {
        try {
            Path file = Files.createTempFile(prefix + "-", suffix);
            Files.writeString(file, content);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write import fixture file", e);
        }
    }

    /** True if a patient with the given hospital patient id exists via the API. */
    private boolean existsInApi(String hospitalPatientId) {
        return PatientApiClient.usingPage(page())
                .findIdByHospitalPatientId(hospitalPatientId)
                .isPresent();
    }

    /**
     * Deletes the patients this class imported. Complements the suite-level
     * {@code PatientCleanupListener} so the class stays self-cleaning when run in
     * isolation. A double-delete is harmless and cleanup never fails the run.
     */
    @AfterClass(alwaysRun = true)
    public void cleanupCreated() {
        if (importedHospitalPatientIds.isEmpty()) {
            return;
        }
        if (!dataCleanupEnabled()) {
            log.info("data.cleanup=false: leaving imported patient(s) on this environment: {}",
                    importedHospitalPatientIds);
            importedHospitalPatientIds.clear();
            return;
        }
        try {
            PatientApiClient.runAs(UserRole.SURGEON, api -> {
                for (String hospitalPatientId : importedHospitalPatientIds) {
                    api.findIdByHospitalPatientId(hospitalPatientId).ifPresent(id ->
                            log.info("Cleanup: deleting imported patient {} ({}) -> {}",
                                    hospitalPatientId, id, api.delete(id) ? "ok" : "already gone"));
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Import cleanup encountered an error (ignored): {}", e.getMessage());
        } finally {
            importedHospitalPatientIds.clear();
        }
    }
}
