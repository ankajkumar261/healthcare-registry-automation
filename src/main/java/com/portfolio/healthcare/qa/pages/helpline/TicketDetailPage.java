package com.portfolio.healthcare.qa.pages.helpline;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.pages.BasePage;

import java.util.regex.Pattern;

/**
 * A support-ticket detail/thread at {@code /helpline/{ticketId}} (shared route,
 * role-aware content).
 *
 * <p>Both the requester and the Super Admin see the message thread and have a
 * single reply {@code <textarea>} plus a "Send" button - for the requester the
 * placeholder is "Add a follow-up comment…"; for the Super Admin it is "Reply to
 * the doctor, or add an internal note…" (with an "Internal note" checkbox). The
 * Super Admin view additionally has a status combobox (Open / In Progress /
 * Resolved / Closed). Selectors verified against the live DOM.</p>
 */
public class TicketDetailPage extends BasePage {

    private static final Pattern STATUS = Pattern.compile("Open|In Progress|Resolved|Closed");

    public TicketDetailPage(Page page) {
        super(page);
    }

    void waitUntilLoaded() {
        // The reply textarea is present for both roles once the detail has rendered.
        waitUntilVisible(replyBox());
    }

    /** Navigates straight to a ticket by internal id and waits for it to load. */
    public TicketDetailPage openById(String ticketId) {
        page.navigate(ConfigManager.config().appBaseUrl() + "/helpline/" + ticketId);
        waitUntilLoaded();
        return this;
    }

    // --- Locators ------------------------------------------------------------
    private Locator replyBox() {
        return page.locator("textarea").first();
    }

    private Locator sendButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true));
    }

    private Locator statusCombobox() {
        return page.getByRole(AriaRole.COMBOBOX).filter(new Locator.FilterOptions().setHasText(STATUS)).first();
    }

    /** The internal id in the current detail URL ({@code /helpline/<id>}). */
    public String ticketId() {
        String url = page.url();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    // --- Actions -------------------------------------------------------------
    /** Posts a reply/comment in the thread and waits for it to appear. */
    public TicketDetailPage postReply(String message) {
        type(replyBox(), message);
        sendButton().click();
        waitUntilVisible(page.getByText(message, new Page.GetByTextOptions().setExact(false)).first());
        return this;
    }

    /**
     * Sets the ticket status via the Super Admin status combobox (e.g.
     * {@code "In Progress"}, {@code "Resolved"}). No-op controls are surfaced as a
     * failure by the follow-up assertion, not swallowed here.
     */
    public TicketDetailPage setStatus(String status) {
        statusCombobox().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(status).setExact(true))
                .first().click();
        // The change is applied via an async request; wait for the status control
        // to re-render with the new value before returning.
        page.getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText(status))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs()));
        return this;
    }

    // --- Queries -------------------------------------------------------------
    /** True if the thread shows a message containing the given text. */
    public boolean hasMessage(String text) {
        try {
            waitUntilVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)).first());
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    /** The current status shown on the Super Admin status combobox. */
    public String currentStatus() {
        return statusCombobox().innerText().trim();
    }

    /** True if the reply area offers a file attachment input. */
    public boolean replySupportsAttachment() {
        return page.locator("input[type=file]").count() > 0;
    }
}
