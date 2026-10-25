package com.portfolio.healthcare.qa.pages.helpline;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.util.regex.Pattern;

/**
 * The requester-facing Helpline page at {@code /helpline}.
 *
 * <p>Two tabs: <b>Contact Support</b> (the create form) and <b>My Tickets</b>
 * (the requester's own tickets, listed as cards). The create form's fields have
 * unstable {@code base-ui} generated ids, so they are located by placeholder or
 * visible label: Subject (placeholder "Brief summary of your issue…"), Issue Type
 * (a combobox showing "Select issue type"; options Technical Issue / Login Issue /
 * Data Entry Issue / Patient Registry / Report Issue / Feature Request / Other),
 * Priority (Low/Medium/High, default Medium), Description (placeholder "Describe
 * your issue in detail…") and an Attachment file input. Submitting shows the toast
 * "Your support request has been submitted successfully." Selectors verified
 * against the live DOM.</p>
 */
public class HelplinePage extends BasePage {

    public HelplinePage(Page page) {
        super(page);
    }

    public HelplinePage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/helpline");
        waitUntilVisible(subjectInput());
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator subjectInput() {
        return page.getByPlaceholder(Pattern.compile("Brief summary of your issue", Pattern.CASE_INSENSITIVE));
    }

    private Locator descriptionInput() {
        return page.getByPlaceholder(Pattern.compile("Describe your issue in detail", Pattern.CASE_INSENSITIVE));
    }

    private Locator submitButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit Request"));
    }

    private Locator myTicketsTab() {
        // A role=tab whose label is "My Tickets (N)".
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions()
                .setName(Pattern.compile("My Tickets", Pattern.CASE_INSENSITIVE)));
    }

    // --- Actions -------------------------------------------------------------
    /**
     * Fills and submits a support ticket (Subject, Issue Type, Description; other
     * fields are pre-filled for the signed-in user). Returns this page; call
     * {@link #submittedSuccessfully()} to assert the confirmation.
     */
    public HelplinePage createTicket(String subject, String issueType, String description) {
        type(subjectInput(), subject);
        page.getByText("Select issue type", new Page.GetByTextOptions().setExact(false)).first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(issueType).setExact(true))
                .first().click();
        type(descriptionInput(), description);
        submitButton().click();
        return this;
    }

    /** True once the submission-success confirmation is shown. */
    public boolean submittedSuccessfully() {
        return showsText("submitted successfully");
    }

    /** Opens the "My Tickets" tab (the requester's own ticket list). */
    public HelplinePage openMyTickets() {
        myTicketsTab().click();
        return this;
    }

    /** Opens the ticket whose card matches the given text (subject or HLP id). */
    public TicketDetailPage openTicket(String subjectOrId) {
        page.getByText(subjectOrId, new Page.GetByTextOptions().setExact(false)).first().click();
        TicketDetailPage detail = new TicketDetailPage(page);
        detail.waitUntilLoaded();
        return detail;
    }

    // --- Queries -------------------------------------------------------------
    /** True if the "My Tickets" list currently shows a card containing the text. */
    public boolean myTicketsShows(String text) {
        return showsText(text);
    }

    private boolean showsText(String text) {
        try {
            waitUntilVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }
}
