package com.portfolio.healthcare.qa.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.portfolio.healthcare.qa.auth.AuthStateManager;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.factory.BrowserFactory;
import com.portfolio.healthcare.qa.models.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * REST client for the patient API, driven through an authenticated browser
 * {@link Page} rather than Playwright's {@code APIRequestContext}.
 *
 * <p><b>Why in-browser fetch:</b> the live backend ({@code api.example.test})
 * returns a response header that Playwright's Node-based HTTP client rejects with
 * "Invalid header value char", so {@code APIRequestContext} cannot talk to it.
 * Chromium tolerates the header, so every call here runs as an in-page
 * {@code fetch(..., {credentials:'include'})} via {@link Page#evaluate}, reusing
 * the page's authenticated session &mdash; exactly how the app itself calls the
 * API. This also works unchanged on staging.</p>
 *
 * <p>Used both for test-data hygiene (resolve a Hospital Patient ID to its
 * internal id, soft-delete it) and for seeding operated-patient fixtures.</p>
 */
public final class PatientApiClient {

    private static final Logger log = LoggerFactory.getLogger(PatientApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Page page;
    private final String apiBaseUrl;

    private PatientApiClient(Page page) {
        this.page = page;
        this.apiBaseUrl = ConfigManager.config().apiBaseUrl();
    }

    /** Wraps an already-authenticated page (on the app origin) for API calls. */
    public static PatientApiClient usingPage(Page page) {
        return new PatientApiClient(page);
    }

    /**
     * Runs {@code fn} with a client backed by a throwaway authenticated page for
     * {@code role}. Use when no live test page is available (e.g. suite/class
     * cleanup): a fresh browser context is seeded from the role's saved storage
     * state and navigated to the app origin so cross-origin API fetches carry the
     * session cookie.
     */
    public static <T> T runAs(UserRole role, Function<PatientApiClient, T> fn) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = BrowserFactory.createBrowser(playwright);
            try {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setStorageStatePath(AuthStateManager.storageStateFor(role)));
                Page page = context.newPage();
                page.navigate(ConfigManager.config().appBaseUrl());
                return fn.apply(usingPage(page));
            } finally {
                browser.close();
            }
        }
    }

    // --- HTTP via in-browser fetch ------------------------------------------
    private record HttpResponse(int status, String body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse send(String method, String path, String jsonBody) {
        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("base", apiBaseUrl);
        arg.put("method", method);
        arg.put("path", path);
        arg.put("body", jsonBody);   // null when no body

        Object raw = page.evaluate(
                "async (a) => {"
                        + "  const opts = { method: a.method, credentials: 'include', headers: {} };"
                        + "  if (a.body) { opts.headers['Content-Type'] = 'application/json'; opts.body = a.body; }"
                        + "  const r = await fetch(a.base + a.path, opts);"
                        + "  const text = await r.text();"
                        + "  return { status: r.status, body: text };"
                        + "}",
                arg);

        Map<String, Object> result = (Map<String, Object>) raw;
        int status = ((Number) result.get("status")).intValue();
        String body = (String) result.getOrDefault("body", "");
        return new HttpResponse(status, body);
    }

    // --- Queries / mutations -------------------------------------------------
    /** Resolves the internal patient id for a Hospital Patient ID, if present. */
    public Optional<String> findIdByHospitalPatientId(String hospitalPatientId) {
        HttpResponse response = send("GET", "/api/patients", null);
        if (!response.ok()) {
            log.warn("GET /api/patients returned {}", response.status());
            return Optional.empty();
        }
        try {
            JsonNode items = MAPPER.readTree(response.body()).path("data").path("items");
            for (JsonNode item : items) {
                if (hospitalPatientId.equals(item.path("hospitalPatientId").asText())) {
                    return Optional.of(item.path("id").asText());
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse patients list: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Deletes a patient by internal id. Returns {@code true} on success. */
    public boolean delete(String id) {
        return send("DELETE", "/api/patients/" + id, null).ok();
    }

    /** DELETEs an arbitrary API path. Returns {@code true} on a 2xx. Used for
     * non-patient cleanup (e.g. deleting helpline tickets as Super Admin). */
    public boolean deletePath(String path) {
        return send("DELETE", path, null).ok();
    }

    /**
     * The authenticated user's own hospital id (from {@code /api/auth/me}).
     *
     * <p>Cases can only be recorded at the caller's own hospital (the backend
     * rejects a foreign {@code hospitalId} with a {@code HOSPITAL_MISMATCH}
     * 403), so fixture creation resolves this dynamically rather than hard-coding
     * a hospital that a later reassignment could invalidate.</p>
     */
    public String ownHospitalId() {
        HttpResponse response = send("GET", "/api/auth/me", null);
        if (!response.ok()) {
            throw new IllegalStateException("GET /api/auth/me returned " + response.status());
        }
        try {
            return MAPPER.readTree(response.body()).path("data").path("user").path("hospitalId").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse /api/auth/me", e);
        }
    }

    /**
     * Seeds an operated patient (+ its primary surgery) directly via the API, at
     * the caller's own hospital, and returns the ids needed to drive follow-up
     * tests against it. Registers the record for cleanup at the call site.
     */
    public PatientFixture createOperatedPatient(String hospitalPatientId, int age, String gender,
            int heightCm, double preOpWeightKg, String procedureType, String approach, String surgeryDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hospitalPatientId", hospitalPatientId);
        body.put("age", age);
        body.put("gender", gender);
        body.put("heightCm", heightCm);
        body.put("preOpWeightKg", preOpWeightKg);
        body.put("procedureType", procedureType);
        body.put("approach", approach);
        body.put("surgeryDate", surgeryDate);
        body.put("hospitalId", ownHospitalId());

        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize patient body", e);
        }

        HttpResponse response = send("POST", "/api/patients", json);
        if (response.status() != 201) {
            throw new IllegalStateException("POST /api/patients returned " + response.status()
                    + ": " + response.body());
        }
        try {
            JsonNode data = MAPPER.readTree(response.body()).path("data");
            PatientFixture fixture = new PatientFixture(
                    data.path("patient").path("id").asText(),
                    data.path("surgery").path("id").asText(),
                    hospitalPatientId,
                    data.path("patient").path("registryId").asText());
            log.info("Seeded fixture patient {} (surgery {})", fixture.registryId(), fixture.surgeryId());
            return fixture;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse create-patient response", e);
        }
    }

    /**
     * The follow-ups recorded against the patient's (single) surgery, as returned
     * by {@code GET /api/patients/{id}}. Used to verify data-level outcomes that
     * the detail UI does not surface as text (e.g. complication flags).
     */
    public JsonNode followUps(String patientId) {
        HttpResponse response = send("GET", "/api/patients/" + patientId, null);
        if (!response.ok()) {
            throw new IllegalStateException("GET /api/patients/" + patientId + " -> " + response.status());
        }
        try {
            return MAPPER.readTree(response.body()).path("data").path("surgeries").path(0).path("followUps");
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse patient follow-ups", e);
        }
    }

    /**
     * GETs {@code path} and returns its {@code data} node. Used by dashboard tests
     * to read the same aggregate figures the UI renders, so a UI value can be
     * asserted against the backing query result rather than a hard-coded number
     * (live totals drift over time).
     */
    public JsonNode getData(String path) {
        HttpResponse response = send("GET", path, null);
        if (!response.ok()) {
            throw new IllegalStateException("GET " + path + " -> " + response.status());
        }
        try {
            return MAPPER.readTree(response.body()).path("data");
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse " + path, e);
        }
    }

    /** Ids of a seeded patient fixture and its primary surgery. */
    public record PatientFixture(String patientId, String surgeryId,
                                 String hospitalPatientId, String registryId) {
    }
}
