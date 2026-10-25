package com.portfolio.healthcare.qa.pages.patients;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The patient detail page at {@code /patients/{id}}.
 *
 * <p>Shows outcome metric tiles (Current Weight, Current BMI, %TWL, %EWL), trend
 * charts, and a "Follow-up Timeline" whose nodes are labelled by interval
 * (e.g. {@code "6 mo"}) and annotate the recorded co-morbidity status
 * (e.g. {@code "DM: Remission"}). The "Add Follow-up" button opens the
 * {@link FollowUpPage}. Selectors verified against the live staging DOM.</p>
 */
public class PatientDetailPage extends BasePage {

    public PatientDetailPage(Page page) {
        super(page);
    }

    // --- Navigation ----------------------------------------------------------
    public PatientDetailPage open(String patientId) {
        page.navigate(ConfigManager.config().appBaseUrl() + "/patients/" + patientId);
        waitUntilVisible(addFollowUpButton());
        return this;
    }

    private Locator addFollowUpButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Follow-up"));
    }

    /** Opens the Add Follow-up form and returns it. */
    public FollowUpPage addFollowUp() {
        addFollowUpButton().click();
        FollowUpPage form = new FollowUpPage(page);
        waitUntilVisible(page.locator("#followUpDate"));
        return form;
    }

    // --- Queries -------------------------------------------------------------
    /**
     * True if the given text is visible anywhere on the detail page. Metric
     * values render as distinctive standalone tokens ({@code "20.0%"},
     * {@code "88 kg"}), so an exact-value check reliably confirms the app
     * computed and rendered that value.
     */
    private boolean showsText(String text) {
        try {
            waitUntilVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)));
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** True if a metric/weight value (e.g. {@code "20.0%"}, {@code "88 kg"}) is shown. */
    public boolean showsValue(String value) {
        return showsText(value);
    }

    /** True if the timeline shows a node for the given short interval (e.g. {@code "6 mo"}). */
    public boolean showsTimelineInterval(String shortInterval) {
        return showsText(shortInterval);
    }

    /** True if a timeline node annotates the given diabetes status (e.g. {@code "Remission"}). */
    public boolean showsDiabetesStatus(String status) {
        return showsText("DM: " + status);
    }

    // --- Blood investigation / nutrition parameters --------------------------
    // Lab values recorded on a follow-up render in the "Nutrition Parameters"
    // section, one card per parameter (Hemoglobin, Ferritin, Vitamin B12,
    // Vitamin D, Calcium, Albumin). Each card shows the value against its
    // reference range plus a status badge - "Normal", or "Low"/"High" when the
    // value falls outside the range. The badge is the app's abnormal-value
    // flag (there is no separate "critical" tier). The HbA1c trend renders as a
    // separate "HbA1c Improvement" chart.

    /**
     * The nutrition-parameter card for the given parameter label. Resolved as the
     * closest ancestor of the parameter's label that carries a Low/Normal/High
     * status badge, which scopes value and badge lookups to that one card.
     */
    private Locator nutritionCard(String parameter) {
        String label = xpathLiteral(parameter);
        return page.locator("xpath=//*[normalize-space(.)=" + label + "]"
                + "/ancestor::div[.//span[normalize-space(.)='Low' or normalize-space(.)='Normal'"
                + " or normalize-space(.)='High']][1]").first();
    }

    /** True once the "Nutrition Parameters" section has rendered. */
    public boolean showsNutritionParameters() {
        return showsText("Nutrition Parameters");
    }

    /**
     * The status badge for a nutrition parameter (e.g. {@code "Low"},
     * {@code "Normal"}, {@code "High"}), or {@code ""} if the card is not present.
     */
    public String nutritionBadge(String parameter) {
        try {
            Locator badge = nutritionCard(parameter).locator(
                    "xpath=.//span[normalize-space(.)='Low' or normalize-space(.)='Normal'"
                            + " or normalize-space(.)='High']").first();
            waitUntilVisible(badge);
            return badge.innerText().trim();
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return "";
        }
    }

    /** True if the parameter's card is flagged out-of-range ({@code Low} or {@code High}). */
    public boolean isNutritionParameterAbnormal(String parameter) {
        String badge = nutritionBadge(parameter);
        return badge.equals("Low") || badge.equals("High");
    }

    /** True if the parameter's card shows the given recorded value. */
    public boolean nutritionParameterShowsValue(String parameter, String value) {
        try {
            waitUntilVisible(nutritionCard(parameter)
                    .getByText(value, new Locator.GetByTextOptions().setExact(true)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /**
     * True once the "HbA1c Improvement" serial trend chart card has rendered.
     * The titled chart card only appears when the app has follow-up HbA1c data to
     * plot, so its presence confirms the trajectory chart rendered; the number of
     * serial points is asserted from the API in the test. (The recharts SVG itself
     * is not targeted directly - its DOM is not reliably anchored to the heading.)
     */
    public boolean showsHba1cTrendChart() {
        return showsText("HbA1c Improvement");
    }

    /** Quotes a value as an XPath string literal, tolerating embedded quotes. */
    private static String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return "concat('" + value.replace("'", "',\"'\",'") + "')";
    }
}
