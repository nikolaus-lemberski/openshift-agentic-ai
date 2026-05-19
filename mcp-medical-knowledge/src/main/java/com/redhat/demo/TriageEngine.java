package com.redhat.demo;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.redhat.demo.MedicalKnowledgeData.TriageResult;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TriageEngine {

    private static final Pattern GESTATIONAL_WEEKS = Pattern.compile("(\\d+)\\s*weeks?", Pattern.CASE_INSENSITIVE);

    public TriageResult assess(List<String> symptoms, int patientAge, List<String> existingConditions) {
        String symptomBlob = joinLower(symptoms);
        String conditionBlob = joinLower(existingConditions);

        boolean headache = contains(symptomBlob, "headache");
        boolean blurredVision = contains(symptomBlob, "blurred vision") || contains(symptomBlob, "blurred");
        boolean hypertensionHx = contains(conditionBlob, "hypertension") || contains(conditionBlob, "high blood pressure");
        if (headache && blurredVision && hypertensionHx) {
            return new TriageResult(
                    2,
                    "2 (Emergent)",
                    List.of("Hypertensive urgency", "possible end-organ involvement"),
                    List.of("Blurred vision", "Severe headache with hypertension"),
                    "cardiology",
                    "Check BP; if > 170/110 notify physician immediately");
        }

        boolean sob = contains(symptomBlob, "shortness of breath")
                || contains(symptomBlob, "short of breath")
                || contains(symptomBlob, "dyspnea");
        boolean copdHx = contains(conditionBlob, "copd")
                || contains(conditionBlob, "chronic obstructive pulmonary");
        if (sob && copdHx) {
            return new TriageResult(
                    2,
                    "2 (Emergent)",
                    List.of("COPD exacerbation", "respiratory distress"),
                    List.of("SpO2 < 92%", "Use of accessory muscles", "Cyanosis"),
                    "pulmonology",
                    "Apply supplemental oxygen; continuous pulse oximetry");
        }

        boolean abdominalPain = contains(symptomBlob, "abdominal pain") || contains(symptomBlob, "abdominal");
        boolean pregnant = contains(conditionBlob, "pregnant") || contains(conditionBlob, "pregnancy");
        if (abdominalPain && pregnant && gestationalWeeksAtLeast(existingConditions, 20)) {
            return new TriageResult(
                    2,
                    "2 (Emergent)",
                    List.of("Obstetric emergency", "common pregnancy discomfort (rule out emergency)"),
                    List.of("Vaginal bleeding", "Severe pain", "Decreased fetal movement"),
                    "obstetrics",
                    "Fetal monitoring; OB consult");
        }

        if (contains(symptomBlob, "chest pain")) {
            return new TriageResult(
                    2,
                    "2 (Emergent)",
                    List.of("Possible acute coronary syndrome"),
                    List.of("Radiating pain", "Diaphoresis", "Dyspnea"),
                    "cardiology",
                    "12-lead ECG within 10 minutes; troponin levels");
        }

        boolean mildHeadacheCandidate = headache
                && !blurredVision
                && !contains(symptomBlob, "chest pain")
                && !sob
                && !abdominalPain
                && !hypertensionHx
                && !copdHx
                && !pregnant;
        if (mildHeadacheCandidate) {
            return new TriageResult(
                    4,
                    "4 (Less urgent)",
                    List.of("Tension headache", "routine follow-up"),
                    List.of(),
                    "internal medicine",
                    "Routine follow-up; return precautions if symptoms worsen");
        }

        return new TriageResult(
                3,
                "3 (Urgent)",
                List.of("Further assessment needed"),
                List.of(),
                "per clinical judgment",
                "Escalate care if red flags develop");
    }

    private static boolean gestationalWeeksAtLeast(List<String> existingConditions, int minWeeks) {
        for (String raw : existingConditions) {
            if (raw == null) {
                continue;
            }
            Matcher m = GESTATIONAL_WEEKS.matcher(raw);
            if (m.find()) {
                try {
                    int w = Integer.parseInt(m.group(1));
                    if (w >= minWeeks) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }

    private static String joinLower(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(p.toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null && !needle.isEmpty() && haystack.contains(needle.toLowerCase(Locale.ROOT));
    }
}
