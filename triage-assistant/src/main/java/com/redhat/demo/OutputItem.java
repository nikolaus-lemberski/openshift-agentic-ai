package com.redhat.demo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OutputItem(
        String type,
        String text,
        String name,
        String status,
        @JsonProperty("call_id") String callId,
        List<ContentItem> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentItem(
            String type,
            String text,
            String refusal) {
    }
}
