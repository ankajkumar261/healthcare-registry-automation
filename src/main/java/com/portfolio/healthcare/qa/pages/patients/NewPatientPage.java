package com.portfolio.healthcare.qa.pages.patients;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.util.regex.Pattern;

/**
 * The "New Case Entry" patient-registration wizard at {@code /patients/new}.
 *
 * <p>Step 1 ("Patient Info") holds the anthropometric fields and a live
 * <b>Calculated BMI</b> readout. Height/weight/etc. inputs have stable semantic
 * ids ({@code #heightCm}, {@code #preOpWeightKg}); the BMI value has no stable
 * id, so it's located by the only {@code kg/m} text on the page.</p>
 */
public class NewPatientPage extends BasePage {

    public NewPatientPage(Page page) {
        super(page);
    }

    // --- Locators (step 1: Patient Info) ------------------------------------
    private Locator hospitalPatientIdInput() {
        return page.locator("#hospitalPatientId");
    }

    private Locator ageInput() {
        return page.locator("#age");
    }

    private Locator heightInput() {
        return page.locator("#heightCm");
    }

    private Locator preOpWeightInput() {
        return page.locator("#preOpWeightKg");
    }

    private Locator idealBodyWeightInput() {
        return page.locator("#idealBodyWeightKg");
    }

    private Locator nextButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Next")).first();
    }

    private Locator calculatedBmi() {
        // The BMI readout is the only "kg/m²" text; shows "-- kg/m²" until valid.
        return page.getByText("kg/m").first();
    }

    // --- Actions -------------------------------------------------------------
    public NewPatientPage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/patients/new");
        waitUntilVisible(heightInput());
        return this;
    }

    public NewPatientPage enterHeightCm(String height) {
        type(heightInput(), height);
        return this;
    }

    public NewPatientPage enterPreOpWeightKg(String weight) {
        type(preOpWeightInput(), weight);
        // Blur so the reactive BMI recalculation fires deterministically.
        preOpWeightInput().blur();
        return this;
    }

    public NewPatientPage enterHospitalPatientId(String id) {
        type(hospitalPatientIdInput(), id);
        return this;
    }

    public NewPatientPage enterAge(String age) {
        type(ageInput(), age);
        return this;
    }

    /** Selects a gender from the custom dropdown (Male / Female / Other). */
    public NewPatientPage selectGender(String gender) {
        page.getByText("Select gender").first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(gender)).first().click();
        return this;
    }

    // --- Wizard navigation ---------------------------------------------------
    /** Advances to the Comorbidities step (waits for its HbA1c field). */
    public NewPatientPage nextToComorbidities() {
        nextButton().click();
        waitUntilVisible(page.locator("#preOpHba1c"));
        return this;
    }

    /** Advances to the Surgical Details step (waits for its procedure selector). */
    public NewPatientPage nextToSurgicalDetails() {
        nextButton().click();
        waitUntilVisible(page.getByText("Select procedure"));
        return this;
    }

    /** Clicks Next without waiting for a step change (used to trigger validation). */
    public NewPatientPage attemptNext() {
        nextButton().click();
        return this;
    }

    // --- Surgical Details fields --------------------------------------------
    public NewPatientPage selectProcedure(String procedure) {
        page.getByText("Select procedure").first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(procedure)).first().click();
        return this;
    }

    public NewPatientPage selectApproach(String approach) {
        page.getByText("Select approach").first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(approach)).first().click();
        return this;
    }

    /**
     * Selects the Operating Surgeon from the native {@code <select id="surgeonId">}.
     *
     * <p>This control appears in the <b>Hospital Admin</b> create flow: the
     * Hospital / Center is pre-filled to the admin's own facility (so the
     * surgeon-role hospital search &mdash; currently broken on staging &mdash; is
     * not needed), and the case is attributed to one of that hospital's surgeons.
     * The value is the surgeon's internal id; we select by visible label.</p>
     */
    public NewPatientPage selectOperatingSurgeon(String surgeonName) {
        page.locator("#surgeonId").selectOption(
                new com.microsoft.playwright.options.SelectOption().setLabel(surgeonName));
        return this;
    }

    public NewPatientPage enterSurgeryDate(String isoDate) {
        type(page.locator("#surgeryDate"), isoDate);
        return this;
    }

    /**
     * Selects a hospital from the async autocomplete. This field only searches
     * on real keystrokes, so {@code pressSequentially} is required here (a plain
     * fill sets the value without firing the search).
     *
     * @param searchTerm what to type (e.g. "HOPE")
     * @param optionText a distinctive substring of the option to click
     */
    public NewPatientPage selectHospital(String searchTerm, String optionText) {
        Locator hospital = page.getByPlaceholder("Search and select hospital");
        hospital.click();
        hospital.pressSequentially(searchTerm, new Locator.PressSequentiallyOptions().setDelay(100));
        Locator option = page.getByText(optionText, new Page.GetByTextOptions().setExact(false)).last();
        waitUntilVisible(option);
        option.click();
        return this;
    }

    /**
     * Advances through the remaining (optional) steps to Review and clicks
     * "Submit Case", then waits for the redirect to the patient list. Assumes
     * the caller is on Surgical Details with required fields filled.
     */
    /**
     * Advances to Review and clicks "Submit Case" WITHOUT waiting for a
     * redirect. Use when the submit may be rejected (e.g. duplicate id) and the
     * caller wants to assert the error while still on the form.
     */
    public NewPatientPage clickSubmitCase() {
        Locator submit = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit Case"));
        for (int i = 0; i < 4 && submit.count() == 0; i++) {
            nextButton().click();
            // Bounded wait: give the next step time to render while polling for Submit.
            try {
                submit.first().waitFor(new Locator.WaitForOptions().setTimeout(2500));
            } catch (com.microsoft.playwright.PlaywrightException ignored) {
                // not on Review yet; loop clicks Next again
            }
        }
        submit.first().click();
        return this;
    }

    /** Submits and waits for the successful redirect off the create form. */
    public void submitCase() {
        clickSubmitCase();
        waitForUrlToLeave("/patients/new");
    }

    /**
     * True if the duplicate Hospital Patient ID error is shown after submit.
     * Matches on "already exists", which covers both the surgeon-flow message
     * ("...already exists for this hospital") and the Hospital Admin flow's
     * system-wide message ("This Patient ID already exists in the system...").
     */
    public boolean hasDuplicateIdError() {
        return hasValidationError("already exists");
    }

    /** True if a validation message containing {@code text} becomes visible. */
    public boolean hasValidationError(String text) {
        try {
            waitUntilVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)));
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    // --- Queries -------------------------------------------------------------
    /**
     * The calculated BMI text (e.g. {@code "38.1 kg/m²"}). Waits until the
     * readout actually contains a digit, so callers never read the "-- kg/m²"
     * placeholder before the reactive update lands (explicit wait, no sleep).
     */
    public String calculatedBmiText() {
        PlaywrightAssertions.assertThat(calculatedBmi()).containsText(Pattern.compile("\\d"));
        return calculatedBmi().textContent().trim();
    }

    /**
     * The read-only auto-calculated Ideal Body Weight (25 × (height/100)²).
     * Waits until the field holds a numeric value.
     */
    public String idealBodyWeightValue() {
        PlaywrightAssertions.assertThat(idealBodyWeightInput()).hasValue(Pattern.compile("\\d"));
        return idealBodyWeightInput().inputValue();
    }
}
