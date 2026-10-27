package com.redhat.demo;

import java.util.List;

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

    @ConfigProperty(name = "triage.guardrails-enabled", defaultValue = "false")
    boolean guardrailsEnabled;

    @ConfigProperty(name = "triage.connectors")
    List<String> connectors;

    List<ToolConfig> getTools() {
        return connectors.stream().map(ToolConfig::mcp).toList();
    }

    public ChatResponse chat(String userMessage, String previousResponseId) {
        // null omits the field; true runs the input through the moderation endpoint (PromptGuard)
        Boolean guards = guardrailsEnabled ? Boolean.TRUE : null;
        var request = new ResponseRequest(model, systemPrompt, userMessage, getTools(), previousResponseId, "default",
                guards);
        try {
            var result = llamaStack.createResponse(request);
            return buildChatResponse(result);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 400 && previousResponseId != null) {
                // OGX guardrails can't serialize MCP tool outputs from conversation history
                // ("Unsupported content type: list"); retry without conversation context
                var retry = new ResponseRequest(model, systemPrompt, userMessage, getTools(), null, "default", guards);
                var result = llamaStack.createResponse(retry);
                return buildChatResponse(result);
            }
            throw e;
        }
    }

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
                : text.toString().strip();

        return new ChatResponse(result.id(), finalText, toolCalls, refused);
    }

    public record ChatResponse(String responseId, String text, List<String> toolCalls, boolean refused) {
    }
}
