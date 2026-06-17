package com.redhat.demo;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import jakarta.inject.Inject;

@WebSocket(path = "/chat")
public class ChatWebSocket {

    @Inject
    TriageService triageService;

    @Inject
    ObjectMapper objectMapper;

    private String lastResponseId;

    @OnOpen
    public String onOpen() throws Exception {
        lastResponseId = null;
        var welcome = new WebSocketMessage(
                "assistant",
                "Hello! I'm the Patient Triage Assistant. I can help you look up patient records, "
                        + "assess symptom urgency, check drug interactions, and schedule specialist appointments. "
                        + "How can I help you today?",
                null);
        return objectMapper.writeValueAsString(welcome);
    }

    @OnTextMessage
    public String onMessage(String message) throws Exception {
        try {
            TriageService.ChatResponse response = triageService.chat(message, lastResponseId);
            if (!response.refused()) {
                lastResponseId = response.responseId();
            }
            var reply = new WebSocketMessage("assistant", response.text(), response.toolCalls());
            return objectMapper.writeValueAsString(reply);
        } catch (Exception e) {
            lastResponseId = null;
            var error = new WebSocketMessage(
                    "error",
                    "Sorry, I encountered an error processing your request: " + e.getMessage(),
                    null);
            return objectMapper.writeValueAsString(error);
        }
    }

    public record WebSocketMessage(String role, String text, java.util.List<String> toolCalls) {
    }
}
