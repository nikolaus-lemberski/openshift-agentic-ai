package com.redhat.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolConfig(
        String type,
        @JsonProperty("server_label") String serverLabel,
        @JsonProperty("server_url") String serverUrl) {

    public static ToolConfig mcp(String serverLabel, String serverUrl) {
        return new ToolConfig("mcp", serverLabel, serverUrl);
    }
}
