package com.redhat.demo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseRequest(
        String model,
        String instructions,
        Object input,
        List<ToolConfig> tools,
        String previous_response_id,
        String service_tier,
        List<String> guardrails) {

    public ResponseRequest(String model, String instructions, Object input, List<ToolConfig> tools) {
        this(model, instructions, input, tools, null, "default", null);
    }
}
