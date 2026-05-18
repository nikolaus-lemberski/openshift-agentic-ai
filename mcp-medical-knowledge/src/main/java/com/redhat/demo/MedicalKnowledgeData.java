package com.redhat.demo;

import java.util.List;

public class MedicalKnowledgeData {

    public record TriageResult(
            int esiLevel,
            String esiDescription,
            List<String> possibleConditions,
            List<String> redFlags,
            String recommendedSpecialty,
            String immediateActions) {
    }

    public record DrugInteraction(String drugA, String drugB, String severity, String warning) {
    }

    public record Protocol(String condition, List<String> steps, String notes) {
    }
}
