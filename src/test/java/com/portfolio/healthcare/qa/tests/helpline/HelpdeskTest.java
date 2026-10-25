package com.portfolio.healthcare.qa.tests.helpline;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.auth.AuthStateManager;
import com.portfolio.healthcare.qa.base.BaseTest;
import com.portfolio.healthcare.qa.driver.DriverManager;
import com.portfolio.healthcare.qa.models.UserRole;
import com.portfolio.healthcare.qa.pages.helpline.AdminHelplinePage;
import com.portfolio.healthcare.qa.pages.helpline.HelplinePage;
import com.portfolio.healthcare.qa.pages.helpline.TicketDetailPage;
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
 * Module 8 - Helpline & Support Ticket System.
 *
 * <p>A support ticket is raised by a signed-in user on {@code /helpline}
 * (surgeon here), then managed by the Super Admin on {@code /admin/helpline};
 * both share the ticket-detail thread at {@code /helpline/{id}}. Tickets can only
 * be deleted by the Super Admin, so this class cleans up every {@code QAHELP…}
 * ticket it creates through the Super Admin API (see {@link #cleanupTickets()}).</p>
 *
 * <p>Coverage in this class:</p>
 * <ul>
 *   <li>TC-HELP-001  Create a support ticket -> status Open, HLP id, confirmation</li>
 *   <li>TC-HELP-002  Reply on the ticket thread -> message appears in the timeline</li>
 *   <li>TC-HELP-003  Super Admin transitions status Open -> In Progress</li>
 * </ul>
 *
 * <p><b>Findings vs the QA doc (verified live):</b> there is no separate "New
 * Ticket" button - the create form is the default "Contact Support" tab; ticket
 * ids are {@code HLP-000000} format and status values are Open / In Progress /
 * Resolved / Closed. The app also <b>strips digit sequences from the subject</b>
 * (so test subjects use an all-letters unique token - see {@code uniqueSubject}).
 * The reply thread is text-only (attachments are offered on
 * ticket <i>creation</i>, not on replies), so TC-HELP-002's "attach file" is
 * reported via {@link TicketDetailPage#replySupportsAttachment()} rather than
 * asserted. (Also noted for the product team: Registry Admin has no create form on
 * {@code /helpline} and cannot raise a ticket.)</p>
 */
@Epic("Helpline & Support")
@Feature("Support Ticket System")
public class HelpdeskTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(HelpdeskTest.class);
    private static final String SUBJECT_PREFIX = "QAHELP";

    /** Subjects of tickets created by this class, for Super-Admin cleanup. */
    private final List<String> createdSubjects = new CopyOnWriteArrayList<>();

    @Test(description = "TC-HELP-001: Creating a support ticket assigns an id and Open status")
    @Story("TC-HELP-001 Create new support ticket")
    @Severity(SeverityLevel.NORMAL)
    @Description("On /helpline, filling Subject + Issue Type + Description and submitting shows the "
            + "success confirmation; the new ticket then appears under 'My Tickets' with an HLP id and "
            + "status 'Open', and is present via the API.")
    public void createSupportTicket() {
        loginAs(UserRole.SURGEON);
        String subject = uniqueSubject("create");

        HelplinePage helpline = new HelplinePage(page()).open()
                .createTicket(subject, "Technical Issue", "QA automated ticket - please ignore.");
        Assert.assertTrue(helpline.submittedSuccessfully(),
                "Expected the 'submitted successfully' confirmation after creating a ticket.");

        helpline.openMyTickets();
        Assert.assertTrue(helpline.myTicketsShows(subject),
                "Expected the new ticket to appear under 'My Tickets'.");
        Assert.assertTrue(helpline.myTicketsShows("HLP-"),
                "Expected an assigned HLP ticket id.");
        Assert.assertTrue(helpline.myTicketsShows("Open"),
                "Expected the new ticket's status to be 'Open'.");

        Assert.assertTrue(ticketExistsViaApi(subject),
                "Expected the created ticket to be retrievable via the helpline API.");
    }

    @Test(description = "TC-HELP-002: A reply is added to the ticket thread")
    @Story("TC-HELP-002 Ticket thread conversation reply")
    @Severity(SeverityLevel.NORMAL)
    @Description("Opening a ticket and posting a follow-up comment adds it to the conversation "
            + "timeline (with author and timestamp). NOTE: the reply area is text-only - file "
            + "attachments exist on ticket creation, not on replies - so attachment support is "
            + "reported, not asserted.")
    public void replyOnTicketThread() {
        loginAs(UserRole.SURGEON);
        String subject = uniqueSubject("reply");

        HelplinePage helpline = new HelplinePage(page()).open()
                .createTicket(subject, "Technical Issue", "QA automated ticket - please ignore.");
        Assert.assertTrue(helpline.submittedSuccessfully(), "Ticket creation should be confirmed.");

        TicketDetailPage detail = helpline.openMyTickets().openTicket(subject);
        String message = "QA follow-up comment " + (System.currentTimeMillis() % 1_000_000);
        detail.postReply(message);

        Assert.assertTrue(detail.hasMessage(message),
                "Expected the reply to appear in the ticket conversation thread.");
        log.info("TC-HELP-002: reply attachment supported on this build? {}",
                detail.replySupportsAttachment());
    }

    @Test(description = "TC-HELP-003: Super Admin transitions ticket status Open -> In Progress")
    @Story("TC-HELP-003 Admin ticket status transition")
    @Severity(SeverityLevel.NORMAL)
    @Description("A surgeon raises a ticket (status Open); the Super Admin opens it in the "
            + "/admin/helpline console and moves the status to 'In Progress', which the status "
            + "control then reflects.")
    public void adminTransitionsTicketStatus() {
        loginAs(UserRole.SURGEON);
        String subject = uniqueSubject("status");
        new HelplinePage(page()).open()
                .createTicket(subject, "Technical Issue", "QA automated ticket - please ignore.");
        String ticketId = ticketIdBySubject(subject);   // resolved while still the surgeon

        // Switch this thread's session to the Super Admin and confirm the ticket
        // is visible in the console, then open its detail to change the status.
        AdminHelplinePage console = loginAsSuperAdmin();
        Assert.assertTrue(console.showsTicket(subject),
                "Expected the surgeon's ticket to be visible in the Super Admin console.");

        TicketDetailPage detail = new TicketDetailPage(page()).openById(ticketId);
        Assert.assertEquals(detail.currentStatus(), "Open", "A new ticket should start as 'Open'.");

        detail.setStatus("In Progress");
        Assert.assertEquals(detail.currentStatus(), "In Progress",
                "Expected the status control to reflect the transition to 'In Progress'.");
    }

    // --- Fixtures / helpers --------------------------------------------------

    private String uniqueSubject(String tag) {
        // FINDING: the app strips digit sequences from ticket subjects (e.g.
        // "QAHELP 999001 x" is stored/shown as "QAHELP  x"). So build the unique
        // token from letters only - each timestamp digit mapped to a letter - so
        // the submitted subject equals what is stored and rendered, and stays
        // matchable in both the UI and the API.
        StringBuilder token = new StringBuilder();
        for (char c : Long.toString(System.currentTimeMillis() % 100_000_000L).toCharArray()) {
            token.append((char) ('a' + (c - '0')));
        }
        String subject = SUBJECT_PREFIX + " " + tag + " " + token;
        createdSubjects.add(subject);
        return subject;
    }

    /** Switches the current thread's session to the Super Admin and opens the console. */
    private AdminHelplinePage loginAsSuperAdmin() {
        Page adminPage = DriverManager.useStorageState(
                AuthStateManager.storageStateFor(UserRole.SUPER_ADMIN));
        return new AdminHelplinePage(adminPage).open();
    }

    /** True if a ticket with the given subject is returned by the helpline list API. */
    private boolean ticketExistsViaApi(String subject) {
        return findTicketId(subject) != null;
    }

    /** The internal id of the ticket with the given subject (must exist). */
    private String ticketIdBySubject(String subject) {
        String id = findTicketId(subject);
        Assert.assertNotNull(id, "Expected to resolve a ticket id for subject: " + subject);
        return id;
    }

    private String findTicketId(String subject) {
        // A just-created ticket may not be immediately queryable; retry a few
        // times and use a generous page size so it isn't paginated out amid the
        // tickets that accumulate on dev (where cleanup is off).
        for (int attempt = 0; attempt < 5; attempt++) {
            JsonNode items = withApi(api ->
                    api.getData("/api/helpline/tickets?page=1&pageSize=200").path("items"));
            for (JsonNode item : items) {
                if (subject.equals(item.path("subject").asText())) {
                    return item.path("id").asText();
                }
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private <T> T withApi(Function<PatientApiClient, T> fn) {
        return fn.apply(PatientApiClient.usingPage(page()));
    }

    /**
     * Deletes every {@code QAHELP…} ticket via the Super Admin API. Only the Super
     * Admin can delete tickets, so this uses a throwaway Super-Admin session.
     * Cleanup never fails the run.
     */
    @AfterClass(alwaysRun = true)
    public void cleanupTickets() {
        if (!dataCleanupEnabled()) {
            log.info("data.cleanup=false: leaving created helpline tickets: {}", createdSubjects);
            createdSubjects.clear();
            return;
        }
        try {
            PatientApiClient.runAs(UserRole.SUPER_ADMIN, api -> {
                JsonNode items = api.getData("/api/helpline/tickets?page=1&pageSize=100").path("items");
                for (JsonNode item : items) {
                    if (item.path("subject").asText().startsWith(SUBJECT_PREFIX)) {
                        String id = item.path("id").asText();
                        log.info("Cleanup: deleting ticket {} ({}) -> {}",
                                item.path("subject").asText(), id,
                                api.deletePath("/api/helpline/tickets/" + id) ? "ok" : "failed");
                    }
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Helpline cleanup encountered an error (ignored): {}", e.getMessage());
        } finally {
            createdSubjects.clear();
        }
    }
}
