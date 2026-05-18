package com.redhat.demo;

import java.util.List;

import com.redhat.demo.MedicalKnowledgeData.DrugInteraction;
import com.redhat.demo.MedicalKnowledgeData.Protocol;
import com.redhat.demo.MedicalKnowledgeData.TriageResult;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MedicalKnowledgeMcpServer {

    @Inject
    TriageEngine triageEngine;

    @Inject
    DrugInteractionData drugInteractionData;

    @Inject
    ProtocolData protocolData;

    @Tool(description = "Assess symptom urgency. Returns ESI triage level (1-5), possible conditions, red flags, and recommended specialty.")
    public TriageResult triageAssessment(
            @ToolArg(description = "List of current symptoms") List<String> symptoms,
            @ToolArg(description = "Patient age in years") int patientAge,
            @ToolArg(description = "List of existing medical conditions") List<String> existingConditions) {
        return triageEngine.assess(symptoms, patientAge, existingConditions);
    }

    @Tool(description = "Check for drug interactions between current medications and a proposed new medication.")
    public List<DrugInteraction> checkDrugInteractions(
            @ToolArg(description = "List of current medications") List<String> currentMedications,
            @ToolArg(description = "Proposed new medication to check") String proposedMedication) {
        return drugInteractionData.checkInteractions(currentMedications, proposedMedication);
    }

    @Tool(description = "Get the standard clinical protocol steps for a given condition.")
    public Protocol getProtocol(@ToolArg(description = "Medical condition name") String condition) {
        return protocolData.getProtocol(condition);
    }
}
