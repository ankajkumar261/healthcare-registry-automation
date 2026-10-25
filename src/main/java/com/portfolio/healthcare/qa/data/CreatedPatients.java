package com.portfolio.healthcare.qa.data;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Run-scoped registry of patients created by tests, so the cleanup listener can
 * delete <b>exactly and only</b> the records this run produced (never a blanket
 * delete). Tests call {@link #register(String)} after a successful create.
 */
public final class CreatedPatients {

    private static final Queue<String> HOSPITAL_PATIENT_IDS = new ConcurrentLinkedQueue<>();

    private CreatedPatients() {
    }

    public static void register(String hospitalPatientId) {
        HOSPITAL_PATIENT_IDS.add(hospitalPatientId);
    }

    public static List<String> all() {
        return List.copyOf(HOSPITAL_PATIENT_IDS);
    }

    public static void clear() {
        HOSPITAL_PATIENT_IDS.clear();
    }
}
