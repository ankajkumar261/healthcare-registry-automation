package com.portfolio.healthcare.qa.tests.dashboards;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.DashboardPage;
import com.portfolio.healthcare.qa.pages.dashboards.HospitalBenchmarkPage;
import com.portfolio.healthcare.qa.pages.dashboards.ProcedureBenchmarkPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.function.Function;

/**
 * Module 6 - Executive Dashboards & Analytics.
 *
 * <p>Read-only across all three roles - no records are created, so there is no
 * cleanup. Because live totals drift, the aggregate-count tests assert the
 * <b>rendered figure against the backing dashboard API</b> (the doc's "match
 * underlying database query counts") rather than hard-coded numbers.</p>
 *
 * <p>Coverage in this class:</p>
 * <ul>
 *   <li>TC-DASH-001  Surgeon dashboard aggregate counts (vs {@code /api/dashboard/surgeon})</li>
 *   <li>TC-DASH-002  Hospital-admin multi-surgeon summary, tenant-isolated</li>
 *   <li>TC-DASH-003  National dashboard aggregate benchmarks (Registry admin)</li>
 *   <li>TC-DASH-004  Procedure-benchmark ranking controls</li>
 *   <li>TC-DASH-005  Hospital-benchmark charts and "Show top" control</li>
 * </ul>
 *
 * <p><b>Deviations from the QA doc (verified live):</b> the benchmark routes are
 * {@code /procedure-benchmark} and {@code /benchmark} (not {@code /dashboards/...});
 * the procedure-benchmark page has <b>no age-group or single-procedure filter</b>
 * (TC-DASH-004's "18-40 / RNY") - its controls are Rank by / Order / Compare vs,
 * so the test exercises the ranking behaviour that exists; the hospital benchmark
 * renders ranked bar charts rather than a radar chart.</p>
 */
@Epic("Executive Dashboards")
@Feature("Dashboards & Analytics")
public class DashboardTest extends BaseTest {

    @Test(description = "TC-DASH-001: Surgeon dashboard aggregate counts match the backing API")
    @Story("TC-DASH-001 Surgeon dashboard aggregate counts")
    @Severity(SeverityLevel.CRITICAL)
    @Description("The surgeon dashboard's 'Total Cases' card matches totalCases from "
            + "GET /api/dashboard/surgeon, and the Complication Rate metric is present.")
    public void surgeonDashboardAggregateCounts() {
        DashboardPage dash = loginAs(UserRole.SURGEON);
        JsonNode api = withApi(a -> a.getData("/api/dashboard/surgeon"));

        int totalCases = api.path("totalCases").asInt();
        Assert.assertTrue(dash.metricShowsValue("Total Cases", String.valueOf(totalCases)),
                "Surgeon 'Total Cases' card should show the API value " + totalCases + ".");
        Assert.assertTrue(dash.hasMetric("Complication Rate"),
                "Expected a 'Complication Rate' metric on the surgeon dashboard.");
    }

    @Test(description = "TC-DASH-002: Hospital-admin summary shows tenant-isolated multi-surgeon data")
    @Story("TC-DASH-002 Hospital-admin multi-surgeon summary")
    @Severity(SeverityLevel.CRITICAL)
    @Description("The hospital-admin dashboard's 'Total Surgeons' and 'Total Cases' cards match "
            + "GET /api/dashboard/hospital-admin, and the per-surgeon breakdown names a surgeon from "
            + "that hospital - confirming the summary is scoped to the admin's own hospital.")
    public void hospitalAdminMultiSurgeonSummary() {
        DashboardPage dash = loginAs(UserRole.HOSPITAL_ADMIN);
        JsonNode api = withApi(a -> a.getData("/api/dashboard/hospital-admin"));

        int totalSurgeons = api.path("totalSurgeons").asInt();
        int totalCases = api.path("totalCases").asInt();
        Assert.assertTrue(dash.metricShowsValue("Total Surgeons", String.valueOf(totalSurgeons)),
                "'Total Surgeons' card should show the API value " + totalSurgeons + ".");
        Assert.assertTrue(dash.metricShowsValue("Total Cases", String.valueOf(totalCases)),
                "'Total Cases' card should show the API value " + totalCases + ".");

        // Tenant isolation + per-surgeon breakdown: a surgeon from this hospital's
        // breakdown is rendered in the Surgeon Performance section.
        String surgeonLastName = api.path("surgeonBreakdown").path(0).path("lastName").asText();
        Assert.assertFalse(surgeonLastName.isBlank(), "Expected the API to list at least one surgeon.");
        Assert.assertTrue(dash.showsText(surgeonLastName),
                "Expected the hospital's surgeon '" + surgeonLastName + "' in the breakdown.");
    }

    @Test(description = "TC-DASH-003: National dashboard aggregate benchmarks match the backing API")
    @Story("TC-DASH-003 National aggregate benchmarks")
    @Severity(SeverityLevel.NORMAL)
    @Description("The Registry-admin National Dashboard's Total Surgeries / Total Surgeons / Total "
            + "Hospitals cards match GET /api/dashboard/national (nationwide aggregate across all "
            + "hospitals).")
    public void nationalDashboardAggregateBenchmarks() {
        DashboardPage dash = loginAs(UserRole.Registry_ADMIN);
        JsonNode api = withApi(a -> a.getData("/api/dashboard/national"));

        Assert.assertTrue(dash.metricShowsValue("Total Surgeries",
                        String.valueOf(api.path("totalSurgeries").asInt())),
                "'Total Surgeries' should match the national API total.");
        Assert.assertTrue(dash.metricShowsValue("Total Surgeons",
                        String.valueOf(api.path("totalSurgeons").asInt())),
                "'Total Surgeons' should match the national API total.");
        Assert.assertTrue(dash.metricShowsValue("Total Hospitals",
                        String.valueOf(api.path("totalHospitals").asInt())),
                "'Total Hospitals' should match the national API total.");
    }

    @Test(description = "TC-DASH-004: Procedure-benchmark ranking controls re-rank the table")
    @Story("TC-DASH-004 Procedure benchmark analytics filtering")
    @Severity(SeverityLevel.NORMAL)
    @Description("On /procedure-benchmark, flipping the Order control from 'Highest to Lowest' to "
            + "'Lowest to Highest' reverses the ranking, so the top-ranked procedure changes. NOTE: "
            + "the doc's age-group (18-40) and single-procedure (RNY) filters do not exist here; the "
            + "real controls are Rank by / Order / Compare vs.")
    public void procedureBenchmarkRankingControls() {
        loginAs(UserRole.HOSPITAL_ADMIN);
        ProcedureBenchmarkPage page = new ProcedureBenchmarkPage(page()).open();

        Assert.assertTrue(page.rowCount() >= 2,
                "Need at least two ranked procedures to demonstrate re-ranking.");

        String topDescending = page.topRankedProcedure();
        page.setOrder("Lowest to Highest");
        String topAscending = page.topRankedProcedure();

        Assert.assertNotEquals(topAscending, topDescending,
                "Reversing the ranking order should change the top-ranked procedure.");
    }

    @Test(description = "TC-DASH-005: Hospital-benchmark charts render and 'Show top' controls rows")
    @Story("TC-DASH-005 Hospital benchmark performance graph")
    @Severity(SeverityLevel.MINOR)
    @Description("On /benchmark, ranked bar charts render and the hospital ranking table populates. "
            + "The 'Show top' cap is honoured (top 10 shows at most 10 rows) and raising it to 50 "
            + "shows at least as many rows - the cross-hospital comparison the doc calls for.")
    public void hospitalBenchmarkChartsAndShowTop() {
        loginAs(UserRole.HOSPITAL_ADMIN);
        HospitalBenchmarkPage page = new HospitalBenchmarkPage(page()).open();

        Assert.assertTrue(page.chartCount() > 0, "Expected benchmark charts to render.");

        int top10 = page.showTop("10").rowCount();
        Assert.assertTrue(top10 > 0, "Expected at least one hospital in the ranking.");
        Assert.assertTrue(top10 <= 10, "'Show top 10' should cap the table at 10 rows, got " + top10 + ".");

        int top50 = page.showTop("50").rowCount();
        Assert.assertTrue(top50 >= top10,
                "Raising 'Show top' to 50 should show at least as many rows (" + top10 + " -> " + top50 + ").");
    }

    // --- helpers -------------------------------------------------------------
    private <T> T withApi(Function<PatientApiClient, T> fn) {
        return fn.apply(PatientApiClient.usingPage(page()));
    }
}
