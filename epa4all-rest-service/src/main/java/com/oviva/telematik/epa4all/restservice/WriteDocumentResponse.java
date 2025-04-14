package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WriteDocumentResponse(@JsonProperty("id") String id) {}
