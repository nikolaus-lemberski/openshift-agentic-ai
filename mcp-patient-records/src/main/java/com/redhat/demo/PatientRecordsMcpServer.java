package com.redhat.demo;

import java.util.List;
import java.util.Optional;

import com.redhat.demo.PatientData.MedicalHistory;
import com.redhat.demo.PatientData.Medication;
import com.redhat.demo.PatientData.Patient;
import com.redhat.demo.PatientData.Visit;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PatientRecordsMcpServer {

    private static final String NOT_FOUND_ID = "NOT_FOUND";

    @Inject
    SampleDataStore dataStore;

    @Tool(description = "List all patients currently registered in the system.")
    public List<Patient> listAllPatients() {
        return dataStore.getAllPatients();
    }

    @Tool(description = "Search for a patient by name and date of birth. Returns patient ID and demographics.")
    public Patient searchPatient(
            @ToolArg(description = "Patient full name") String name,
            @ToolArg(description = "Date of birth in YYYY-MM-DD format") String dateOfBirth) {
        Optional<Patient> found = dataStore.findByNameAndDateOfBirth(name, dateOfBirth);
        if (found.isPresent()) {
            return found.get();
        }
        String safeName = name == null ? "" : name.trim();
        String safeDob = dateOfBirth == null ? "" : dateOfBirth.trim();
        return new Patient(
                NOT_FOUND_ID,
                "No patient found for name \"" + safeName + "\" and date of birth " + safeDob + ".",
                "",
                0,
                "",
                "",
                List.of());
    }

    @Tool(description = "Get a patient's medical history including conditions, past diagnoses, and allergies.")
    public MedicalHistory getPatientHistory(@ToolArg(description = "Patient ID") String patientId) {
        return dataStore
                .getMedicalHistory(patientId)
                .orElseGet(
                        () ->
                                new MedicalHistory(
                                        patientId == null ? "" : patientId.trim(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        "No patient registered with this ID."));
    }

    @Tool(description = "Get a patient's current active medications with dosages.")
    public List<Medication> getCurrentMedications(@ToolArg(description = "Patient ID") String patientId) {
        if (patientId == null || patientId.isBlank() || dataStore.getPatientById(patientId).isEmpty()) {
            String id = patientId == null ? "(not provided)" : patientId.trim();
            return List.of(
                    new Medication(
                            "Patient not found",
                            "",
                            "",
                            "No patient registered with ID " + id + "; no medications on file."));
        }
        return dataStore.getMedications(patientId);
    }

    @Tool(description = "Get a patient's recent visit history with dates and notes.")
    public List<Visit> getRecentVisits(@ToolArg(description = "Patient ID") String patientId) {
        if (patientId == null || patientId.isBlank() || dataStore.getPatientById(patientId).isEmpty()) {
            String id = patientId == null ? "(not provided)" : patientId.trim();
            return List.of(
                    new Visit(
                            "",
                            "Patient not found",
                            "",
                            "No patient registered with ID " + id + "; no visits on file.",
                            ""));
        }
        return dataStore.getVisits(patientId);
    }
}
