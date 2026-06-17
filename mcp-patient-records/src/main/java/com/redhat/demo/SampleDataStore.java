package com.redhat.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.redhat.demo.PatientData.MedicalHistory;
import com.redhat.demo.PatientData.Medication;
import com.redhat.demo.PatientData.Patient;
import com.redhat.demo.PatientData.Visit;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SampleDataStore {

    private final Map<String, Patient> patientsById = new LinkedHashMap<>();
    private final Map<String, MedicalHistory> historyByPatientId = new LinkedHashMap<>();
    private final Map<String, List<Medication>> medicationsByPatientId = new LinkedHashMap<>();
    private final Map<String, List<Visit>> visitsByPatientId = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        putPatient(
                "P-1042",
                new Patient(
                        "P-1042",
                        "Maria Garcia",
                        "1978-03-15",
                        48,
                        "Female",
                        "555-0101",
                        List.of("Penicillin")),
                new MedicalHistory(
                        "P-1042",
                        List.of("Hypertension (diagnosed 2019)"),
                        List.of("Hypertension"),
                        List.of("Penicillin"),
                        "Primary demo patient; hypertension followed in primary care."),
                List.of(new Medication("Lisinopril", "20 mg", "Once daily", "Hypertension")),
                List.of(
                        new Visit(
                                "2026-03-18",
                                "Routine blood pressure check",
                                "Dr. Anna Kowalski, MD",
                                "BP trending stable on current therapy.",
                                "BP 135/85 mmHg")));

        putPatient(
                "P-2187",
                new Patient(
                        "P-2187",
                        "James Wilson",
                        "1954-08-22",
                        72,
                        "Male",
                        "555-0102",
                        List.of()),
                new MedicalHistory(
                        "P-2187",
                        List.of("Type 2 Diabetes Mellitus", "COPD"),
                        List.of("Type 2 Diabetes Mellitus", "COPD exacerbation (2024)"),
                        List.of(),
                        "Co-managed diabetes and COPD; inhaler technique reviewed in clinic."),
                List.of(
                        new Medication("Metformin", "1000 mg", "Twice daily with meals", "Type 2 Diabetes Mellitus"),
                        new Medication("Tiotropium", "18 mcg", "Once daily (inhaler)", "COPD")),
                List.of(
                        new Visit(
                                "2026-04-08",
                                "Diabetes management follow-up",
                                "Dr. Anna Kowalski, MD",
                                "Discussed diet, exercise, and medication adherence.",
                                "A1C 7.2%")));

        putPatient(
                "P-3291",
                new Patient(
                        "P-3291",
                        "Sofia Andersson",
                        "1995-01-10",
                        31,
                        "Female",
                        "555-0103",
                        List.of("Latex")),
                new MedicalHistory(
                        "P-3291",
                        List.of("Pregnancy (28 weeks gestation)"),
                        List.of(),
                        List.of("Latex"),
                        "Routine obstetric care; latex allergy documented."),
                List.of(
                        new Medication("Prenatal vitamins", "As labeled", "Once daily", "Pregnancy"),
                        new Medication("Iron supplement", "As labeled", "Once daily", "Iron supplementation in pregnancy")),
                List.of(
                        new Visit(
                                "2026-05-12",
                                "Prenatal checkup",
                                "Dr. Michael O'Brien, MD",
                                "Routine prenatal visit; fetal growth and maternal vitals normal.",
                                "Maternal vitals WNL; routine OB exam")));
    }

    private void putPatient(
            String patientId,
            Patient patient,
            MedicalHistory history,
            List<Medication> medications,
            List<Visit> visits) {
        patientsById.put(patientId, patient);
        historyByPatientId.put(patientId, history);
        medicationsByPatientId.put(patientId, new ArrayList<>(medications));
        visitsByPatientId.put(patientId, new ArrayList<>(visits));
    }

    public List<Patient> getAllPatients() {
        return List.copyOf(patientsById.values());
    }

    public Optional<Patient> getPatientById(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(patientsById.get(patientId.trim()));
    }

    public Optional<Patient> findByNameAndDateOfBirth(String name, String dateOfBirth) {
        String dob = dateOfBirth == null ? "" : dateOfBirth.trim();
        String rawName = name == null ? "" : name.trim();
        if (dob.isEmpty() || rawName.isEmpty()) {
            return Optional.empty();
        }
        String nameLower = rawName.toLowerCase(Locale.ROOT);
        return patientsById.values().stream()
                .filter(p -> p.dateOfBirth().equals(dob))
                .filter(
                        p -> {
                            String patientName = p.name().toLowerCase(Locale.ROOT);
                            return patientName.equals(nameLower) || patientName.contains(nameLower);
                        })
                .findFirst();
    }

    public List<Patient> findPatientsByName(String partialName) {
        if (partialName == null || partialName.isBlank()) {
            return List.of();
        }
        String q = partialName.trim().toLowerCase(Locale.ROOT);
        return patientsById.values().stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public Optional<MedicalHistory> getMedicalHistory(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(historyByPatientId.get(patientId.trim()));
    }

    public List<Medication> getMedications(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return List.of();
        }
        List<Medication> meds = medicationsByPatientId.get(patientId.trim());
        if (meds == null) {
            return List.of();
        }
        return List.copyOf(meds);
    }

    public List<Visit> getVisits(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return List.of();
        }
        List<Visit> visits = visitsByPatientId.get(patientId.trim());
        if (visits == null) {
            return List.of();
        }
        return List.copyOf(visits);
    }
}
