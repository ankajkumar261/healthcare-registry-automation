package com.portfolio.healthcare.qa.pages.helpline;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

/**
 * The Super Admin helpline console at {@code /admin/helpline}.
 *
 * <p>Lists every ticket (columns: Ticket ID, Doctor, Hospital, Subject, Issue
 * Type, Priority, Status, Created, Actions). Opening a row navigates to the shared
 * {@link TicketDetailPage} at {@code /helpline/{id}}. Selectors verified against
 * the live DOM.</p>
 */
public class AdminHelplinePage extends BasePage {

    public AdminHelplinePage(Page page) {
        super(page);
    }

    public AdminHelplinePage open() {
        page.navigate(ConfigManager.config().appBaseUrl() + "/admin/helpline");
        waitUntilVisible(page.getByText("Helpline Tickets", new Page.GetByTextOptions().setExact(false)).first());
        return this;
    }

    /** True if a ticket with the given subject appears in the console. */
    public boolean showsTicket(String subject) {
        try {
            waitUntilVisible(page.getByText(subject, new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /**
     * Opens the ticket with the given subject and returns its detail page. The
     * console is a table whose row navigates on click (the bare subject text node
     * is not the click target), so this clicks the matching {@code <tr>}.
     */
    public TicketDetailPage openTicket(String subject) {
        page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(subject))
                .first()
                .click();
        TicketDetailPage detail = new TicketDetailPage(page);
        detail.waitUntilLoaded();
        return detail;
    }
}
