package com.redhat.demo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.redhat.demo.MedicalKnowledgeData.Protocol;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProtocolData {

    private final Map<String, Protocol> byKey = Map.ofEntries(
            Map.entry(
                    "hypertensive urgency",
                    new Protocol(
                            "Hypertensive urgency",
                            List.of(
                                    "Confirm BP > 180/120",
                                    "Assess for end-organ damage",
                                    "Obtain ECG and basic labs",
                                    "Administer oral antihypertensive",
                                    "Monitor BP every 15-30 minutes",
                                    "Target 25% reduction in first hour"),
                            "")),
            Map.entry(
                    "copd exacerbation",
                    new Protocol(
                            "COPD exacerbation",
                            List.of(
                                    "Assess airway and breathing",
                                    "Apply supplemental oxygen (target SpO2 88-92%)",
                                    "Administer nebulized bronchodilators",
                                    "Systemic corticosteroids",
                                    "Antibiotics if purulent sputum",
                                    "Consider non-invasive ventilation if severe"),
                            "")),
            Map.entry(
                    "chest pain / acs protocol",
                    new Protocol(
                            "Chest pain / ACS protocol",
                            List.of(
                                    "12-lead ECG within 10 minutes",
                                    "IV access and cardiac monitoring",
                                    "Aspirin 325mg (if not allergic)",
                                    "Troponin levels at 0 and 3 hours",
                                    "Nitroglycerin if ongoing pain",
                                    "Cardiology consult for STEMI"),
                            "")),
            Map.entry(
                    "chest pain",
                    new Protocol(
                            "Chest pain / ACS protocol",
                            List.of(
                                    "12-lead ECG within 10 minutes",
                                    "IV access and cardiac monitoring",
                                    "Aspirin 325mg (if not allergic)",
                                    "Troponin levels at 0 and 3 hours",
                                    "Nitroglycerin if ongoing pain",
                                    "Cardiology consult for STEMI"),
                            "")),
            Map.entry(
                    "acs",
                    new Protocol(
                            "Chest pain / ACS protocol",
                            List.of(
                                    "12-lead ECG within 10 minutes",
                                    "IV access and cardiac monitoring",
                                    "Aspirin 325mg (if not allergic)",
                                    "Troponin levels at 0 and 3 hours",
                                    "Nitroglycerin if ongoing pain",
                                    "Cardiology consult for STEMI"),
                            "")));

    public Protocol getProtocol(String condition) {
        String q = condition == null ? "" : condition.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return unknown(condition);
        }

        Optional<Protocol> direct = Optional.ofNullable(byKey.get(q));
        if (direct.isPresent()) {
            return direct.get();
        }

        for (Map.Entry<String, Protocol> e : byKey.entrySet()) {
            if (q.contains(e.getKey()) || e.getKey().contains(q)) {
                return e.getValue();
            }
        }
        return unknown(condition);
    }

    private static Protocol unknown(String condition) {
        return new Protocol(
                condition == null ? "" : condition,
                List.of("Consult your local clinical guidelines"),
                "No matching demo protocol entry");
    }
}
