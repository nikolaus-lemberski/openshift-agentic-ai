package com.redhat.demo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ToolConfig(
        String type,
        @JsonProperty("server_label") String serverLabel,
        @JsonProperty("server_url") String serverUrl) {

    public static ToolConfig mcp(String label, String url) {
        return new ToolConfig("mcp", label, url);
    }
}
