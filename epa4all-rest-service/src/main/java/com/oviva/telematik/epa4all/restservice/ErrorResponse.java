package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
    @JsonProperty("code") String code,
    @JsonProperty("message") String message) {}
