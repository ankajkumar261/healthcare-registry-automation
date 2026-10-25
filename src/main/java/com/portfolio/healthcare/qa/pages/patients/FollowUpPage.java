package com.portfolio.healthcare.qa.pages.patients;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The "Add Follow-up" form at {@code /patients/{id}/follow-up}.
 *
 * <p>A single full-page form (not a dialog) covering the visit, weight,
 * co-morbidity status, blood investigations and complications. Fields have
 * stable semantic ids ({@code #followUpDate}, {@code #weightKg}, {@code #hba1c},
 * {@code #complicationNotes}); the interval and status pickers are custom
 * comboboxes (opened by their placeholder text, options chosen by role); the
 * complication toggles are visually-hidden checkboxes driven by clicking their
 * labels. Selectors verified against the live staging DOM.</p>
 *
 * <p><b>Deviations from the QA doc worth noting:</b> the form has no
 * Clavien-Dindo complication grade (complications are boolean flags: Stricture,
 * Marginal Ulcer, Internal Hernia, Gallstones, Malnutrition, Revisional
 * Surgery) and no explicit 30-day readmission field. Diabetes status options are
 * {@code Remission / Improved / Unchanged} (not the doc's "Insulin Dependent ->
 * Resolved/Off Meds").</p>
 */
public class FollowUpPage extends BasePage {

    private static final String DUPLICATE_ERROR = "already exists for this surgery";

    public FollowUpPage(Page page) {
        super(page);
    }

    // --- Navigation ----------------------------------------------------------
    public FollowUpPage open(String patientId) {
        page.navigate(ConfigManager.config().appBaseUrl() + "/patients/" + patientId + "/follow-up");
        waitUntilVisible(dateInput());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator dateInput() {
        return page.locator("#followUpDate");
    }

    private Locator weightInput() {
        return page.locator("#weightKg");
    }

    private Locator hba1cInput() {
        return page.locator("#hba1c");
    }

    private Locator hemoglobinInput() {
        return page.locator("#hemoglobin");
    }

    private Locator ferritinInput() {
        return page.locator("#ferritin");
    }

    private Locator vitaminB12Input() {
        return page.locator("#vitaminB12");
    }

    private Locator vitaminDInput() {
        return page.locator("#vitaminD");
    }

    private Locator calciumInput() {
        return page.locator("#calcium");
    }

    private Locator albuminInput() {
        return page.locator("#albumin");
    }

    private Locator notesInput() {
        return page.locator("#complicationNotes");
    }

    private Locator saveButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Follow-up"));
    }

    // --- Actions -------------------------------------------------------------
    public FollowUpPage enterFollowUpDate(String isoDate) {
        type(dateInput(), isoDate);
        return this;
    }

    /**
     * Selects a follow-up interval (e.g. {@code "6 months"}, {@code "1 month"},
     * {@code "12 months (1 year)"}) from the interval combobox.
     */
    public FollowUpPage selectInterval(String intervalLabel) {
        page.getByText("Select interval", new Page.GetByTextOptions().setExact(false)).first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(intervalLabel)).first().click();
        return this;
    }

    public FollowUpPage enterWeightKg(String weight) {
        type(weightInput(), weight);
        weightInput().blur();
        return this;
    }

    public FollowUpPage enterHba1c(String hba1c) {
        type(hba1cInput(), hba1c);
        return this;
    }

    // --- Blood investigation / nutrition parameters --------------------------
    // These optional lab fields live in the same follow-up form (there is no
    // separate "Add Blood Test" screen on this app - see LabInvestigationTest).
    // On the patient detail page each value is rendered in the "Nutrition
    // Parameters" section with a Low/Normal/High badge against its reference
    // range; units are Hb g/dL, Ferritin ng/mL, B12 pg/mL, Vit D ng/mL,
    // Calcium mg/dL, Albumin g/dL, HbA1c %.

    public FollowUpPage enterHemoglobin(String gPerDl) {
        type(hemoglobinInput(), gPerDl);
        return this;
    }

    public FollowUpPage enterFerritin(String ngPerMl) {
        type(ferritinInput(), ngPerMl);
        return this;
    }

    public FollowUpPage enterVitaminB12(String pgPerMl) {
        type(vitaminB12Input(), pgPerMl);
        return this;
    }

    public FollowUpPage enterVitaminD(String ngPerMl) {
        type(vitaminDInput(), ngPerMl);
        return this;
    }

    public FollowUpPage enterCalcium(String mgPerDl) {
        type(calciumInput(), mgPerDl);
        return this;
    }

    public FollowUpPage enterAlbumin(String gPerDl) {
        type(albuminInput(), gPerDl);
        return this;
    }

    /**
     * Sets the Diabetes status (the first "Select status" combobox on the form).
     * Options: {@code Remission / Improved / Unchanged}.
     */
    public FollowUpPage selectDiabetesStatus(String status) {
        page.getByText("Select status", new Page.GetByTextOptions().setExact(false)).first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(status)).first().click();
        return this;
    }

    /**
     * Ticks a complication toggle by its label (e.g. {@code "Marginal Ulcer"},
     * {@code "Stricture"}, {@code "Revisional Surgery"}). The underlying checkbox
     * is visually hidden, so the label is clicked.
     */
    public FollowUpPage checkComplication(String label) {
        page.getByText(label, new Page.GetByTextOptions().setExact(false)).first().click();
        return this;
    }

    public FollowUpPage enterComplicationNotes(String notes) {
        type(notesInput(), notes);
        return this;
    }

    /**
     * Saves the follow-up and waits for the redirect back to the patient detail
     * page, returning it.
     */
    public PatientDetailPage save() {
        saveButton().click();
        waitForUrlToLeave("/follow-up");
        return new PatientDetailPage(page);
    }

    /**
     * Clicks Save without waiting for a redirect. Use when the save is expected
     * to be rejected (e.g. a duplicate interval) and the caller wants to assert
     * the error while still on the form.
     */
    public FollowUpPage clickSaveExpectingError() {
        saveButton().click();
        return this;
    }

    // --- Queries -------------------------------------------------------------
    /** True if the "duplicate interval" rejection error becomes visible. */
    public boolean hasDuplicateIntervalError() {
        try {
            waitUntilVisible(page.getByText(DUPLICATE_ERROR, new Page.GetByTextOptions().setExact(false)));
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }
}
