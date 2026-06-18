package com.redhat.demo;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class TriageService {

    @Inject
    @RestClient
    LlamaStackClient llamaStack;

    @ConfigProperty(name = "triage.model", defaultValue = "triage-model")
    String model;

    @ConfigProperty(name = "triage.system-prompt")
    String systemPrompt;

    @ConfigProperty(name = "triage.guardrail-ids")
    Optional<List<String>> guardrailIds;

    @ConfigProperty(name = "triage.mcp.patient-records.url")
    String patientRecordsUrl;

    @ConfigProperty(name = "triage.mcp.medical-knowledge.url")
    String medicalKnowledgeUrl;

    @ConfigProperty(name = "triage.mcp.scheduling.url")
    String schedulingUrl;

    List<ToolConfig> getTools() {
        return List.of(
                ToolConfig.mcp("patient-records", patientRecordsUrl),
                ToolConfig.mcp("medical-knowledge", medicalKnowledgeUrl),
                ToolConfig.mcp("scheduling", schedulingUrl));
    }

    public ChatResponse chat(String userMessage, String previousResponseId) {
        var guards = guardrailIds
                .map(ids -> ids.stream().filter(s -> s != null && !s.isBlank()).toList())
                .filter(ids -> !ids.isEmpty())
                .orElse(null);
        var request = new ResponseRequest(model, systemPrompt, userMessage, getTools(), previousResponseId, "default",
                guards);
        try {
            var result = llamaStack.createResponse(request);
            return buildChatResponse(result);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 400 && previousResponseId != null) {
                // Llama Stack can't serialize MCP tool outputs from conversation history
                // for guardrail evaluation; retry without conversation context
                var retry = new ResponseRequest(model, systemPrompt, userMessage, getTools(), null, "default", guards);
                var result = llamaStack.createResponse(retry);
                return buildChatResponse(result);
            }
            throw e;
        }
    }

    private static final java.util.regex.Pattern THINK_PATTERN =
            java.util.regex.Pattern.compile("<think>.*?</think>\\s*|<think>.*$|</think>\\s*", java.util.regex.Pattern.DOTALL);

    private ChatResponse buildChatResponse(ResponseResult result) {
        var text = new StringBuilder();
        var toolCalls = new java.util.ArrayList<String>();
        boolean refused = false;

        if (result.output() != null) {
            for (OutputItem item : result.output()) {
                switch (item.type()) {
                    case "message" -> {
                        if (item.content() != null) {
                            for (OutputItem.ContentItem content : item.content()) {
                                if ("output_text".equals(content.type()) && content.text() != null) {
                                    text.append(content.text());
                                } else if ("refusal".equals(content.type()) && content.refusal() != null) {
                                    text.append(content.refusal());
                                    refused = true;
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
                : stripThinkingBlocks(text.toString());

        return new ChatResponse(result.id(), finalText, toolCalls, refused);
    }

    private String stripThinkingBlocks(String text) {
        return THINK_PATTERN.matcher(text).replaceAll("").strip();
    }

    public record ChatResponse(String responseId, String text, List<String> toolCalls, boolean refused) {
    }
}
