package com.portfolio.healthcare.qa.listeners;

import com.portfolio.healthcare.qa.api.PatientApiClient;
import com.portfolio.healthcare.qa.config.ConfigManager;
import com.portfolio.healthcare.qa.data.CreatedPatients;
import com.portfolio.healthcare.qa.models.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.util.List;

/**
 * Suite-level cleanup: after all tests finish, soft-deletes the patient records
 * created during the run (tracked in {@link CreatedPatients}) via the backend
 * API, so tests don't accumulate data on the shared environment.
 *
 * <p>Deletes only run-created records, never a blanket sweep. Failures are
 * logged, never thrown &mdash; cleanup must not turn a green suite red.</p>
 */
public class PatientCleanupListener implements ISuiteListener {

    private static final Logger log = LoggerFactory.getLogger(PatientCleanupListener.class);

    @Override
    public void onFinish(ISuite suite) {
        List<String> ids = CreatedPatients.all();
        if (ids.isEmpty()) {
            return;
        }
        if (!ConfigManager.config().dataCleanup()) {
            log.info("data.cleanup=false: leaving {} test-created patient(s) on this environment: {}",
                    ids.size(), ids);
            CreatedPatients.clear();
            return;
        }
        log.info("Cleanup: soft-deleting {} test-created patient(s): {}", ids.size(), ids);
        try {
            PatientApiClient.runAs(UserRole.SURGEON, client -> {
                for (String hospitalPatientId : ids) {
                    client.findIdByHospitalPatientId(hospitalPatientId).ifPresentOrElse(
                            id -> log.info("  {} ({}) -> {}", hospitalPatientId, id,
                                    client.delete(id) ? "deleted" : "DELETE FAILED"),
                            () -> log.warn("  {} not found in API (already gone?)", hospitalPatientId));
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Cleanup encountered an error (ignored): {}", e.getMessage());
        } finally {
            CreatedPatients.clear();
        }
    }
}
