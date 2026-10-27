package com.redhat.demo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ToolConfig(
        String type,
        @JsonProperty("server_label") String serverLabel,
        @JsonProperty("connector_id") String connectorId) {

    // The connector (and its URL) is registered in OGX; the app only knows its ID
    public static ToolConfig mcp(String connectorId) {
        return new ToolConfig("mcp", connectorId.replace("mcp::", ""), connectorId);
    }
}
