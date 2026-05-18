package com.redhat.demo;

import java.util.List;

public class PatientData {
    public record Patient(String patientId, String name, String dateOfBirth, int age, String gender, String phone, List<String> allergies) {}
    public record MedicalHistory(String patientId, List<String> conditions, List<String> pastDiagnoses, List<String> allergies, String notes) {}
    public record Medication(String name, String dosage, String frequency, String prescribedFor) {}
    public record Visit(String date, String type, String provider, String notes, String vitals) {}
}
