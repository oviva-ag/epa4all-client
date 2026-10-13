package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Problem JSON error response, see Oviva API guidelines rule #176 and <a
 * href="https://oviva-ag.github.io/static/openapi/problem-1.0.2.yaml">problem-1.0.2.yaml</a>.
 *
 * @param type identifies the problem type within this API, as a path
 * @param title short English summary of the problem type
 * @param detail explanation specific to this occurrence, must not contain sensitive data
 */
public record Problem(
    @JsonProperty("type") String type,
    @JsonProperty("title") String title,
    @JsonProperty("detail") String detail) {}
