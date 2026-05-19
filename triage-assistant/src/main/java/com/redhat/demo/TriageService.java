package com.redhat.demo;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TriageService {

    @Inject
    @RestClient
    LlamaStackClient llamaStack;

    @ConfigProperty(name = "triage.model", defaultValue = "triage-model")
    String model;

    @ConfigProperty(name = "triage.system-prompt")
    String systemPrompt;

    @ConfigProperty(name = "triage.mcp.patient-records.url")
    String patientRecordsUrl;

    @ConfigProperty(name = "triage.mcp.medical-knowledge.url")
    String medicalKnowledgeUrl;

    @ConfigProperty(name = "triage.mcp.scheduling.url")
    String schedulingUrl;

    public ChatResponse chat(String userMessage, String previousResponseId) {
        List<ToolConfig> tools = List.of(
                ToolConfig.mcp("patient-records", patientRecordsUrl),
                ToolConfig.mcp("medical-knowledge", medicalKnowledgeUrl),
                ToolConfig.mcp("scheduling", schedulingUrl));

        var request = new ResponseRequest(model, systemPrompt, userMessage, tools, previousResponseId, "default");
        var result = llamaStack.createResponse(request);
        return buildChatResponse(result);
    }

    private ChatResponse buildChatResponse(ResponseResult result) {
        var text = new StringBuilder();
        var toolCalls = new java.util.ArrayList<String>();

        if (result.output() != null) {
            for (OutputItem item : result.output()) {
                switch (item.type()) {
                    case "message" -> {
                        if (item.content() != null) {
                            for (OutputItem.ContentItem content : item.content()) {
                                if ("output_text".equals(content.type()) && content.text() != null) {
                                    text.append(content.text());
                                }
                            }
                        }
                    }
                    case "mcp_call" -> {
                        if (item.name() != null) {
                            toolCalls.add(item.name());
                        }
                    }
                    default -> {}
                }
            }
        }

        String finalText = text.isEmpty()
                ? "I wasn't able to generate a response. Please try again."
                : text.toString();

        return new ChatResponse(result.id(), finalText, toolCalls);
    }

    public record ChatResponse(String responseId, String text, List<String> toolCalls) {
    }
}
