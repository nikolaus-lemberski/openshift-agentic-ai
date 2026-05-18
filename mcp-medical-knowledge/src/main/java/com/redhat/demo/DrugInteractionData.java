package com.redhat.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.redhat.demo.MedicalKnowledgeData.DrugInteraction;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DrugInteractionData {

    private final List<DrugInteraction> interactions = List.of(
            new DrugInteraction(
                    "Lisinopril",
                    "Ibuprofen (or NSAIDs)",
                    "Moderate",
                    "Reduced antihypertensive effect, increased renal risk"),
            new DrugInteraction(
                    "Lisinopril",
                    "Potassium supplements",
                    "Major",
                    "Risk of hyperkalemia"),
            new DrugInteraction(
                    "Metformin",
                    "Contrast dye",
                    "Major",
                    "Risk of lactic acidosis; hold 48h before/after"),
            new DrugInteraction(
                    "Metformin",
                    "Alcohol",
                    "Moderate",
                    "Increased risk of hypoglycemia and lactic acidosis"),
            new DrugInteraction(
                    "Warfarin",
                    "Aspirin",
                    "Major",
                    "Increased bleeding risk"),
            new DrugInteraction(
                    "Warfarin",
                    "Ibuprofen (or NSAIDs)",
                    "Major",
                    "Increased bleeding risk"));

    public List<DrugInteraction> checkInteractions(List<String> currentMedications, String proposedMedication) {
        List<DrugInteraction> hits = new ArrayList<>();
        String proposed = normToken(proposedMedication);
        if (proposed.isEmpty()) {
            return hits;
        }

        for (String currentRaw : currentMedications) {
            if (currentRaw == null || currentRaw.isBlank()) {
                continue;
            }
            String current = normToken(currentRaw);

            for (DrugInteraction row : interactions) {
                String a = normToken(row.drugA());
                String b = normToken(row.drugB());

                boolean match =
                        (mentions(current, a) && mentions(proposed, b)) || (mentions(current, b) && mentions(proposed, a));
                if (match) {
                    hits.add(row);
                }
            }
        }
        return hits.stream().distinct().toList();
    }

    private static String normToken(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean mentions(String text, String token) {
        if (text.isEmpty() || token.isEmpty()) {
            return false;
        }
        if (token.contains("nsaid")) {
            return text.contains("ibuprofen") || text.contains("naproxen") || text.contains("nsaid");
        }
        if (token.contains("contrast")) {
            return text.contains("contrast");
        }
        if (token.contains("potassium")) {
            return text.contains("potassium") || text.contains("kcl") || text.contains("k+");
        }
        return text.contains(token);
    }
}
