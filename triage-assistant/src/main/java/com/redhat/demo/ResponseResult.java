package com.redhat.demo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseResult(
        String id,
        String status,
        List<OutputItem> output) {
}
