package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("java:S6218")
public record DocumentCreateRequest(
    @JsonProperty("insurant_id") String insurantId,
    @JsonProperty("content_type") String contentType,
    @JsonProperty("content") byte[] content) {}
