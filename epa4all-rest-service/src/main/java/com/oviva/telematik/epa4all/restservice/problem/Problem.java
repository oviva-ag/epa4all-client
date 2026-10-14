package com.oviva.telematik.epa4all.restservice.problem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Problem JSON error response, see Oviva API guidelines rule #176 and <a
 * href="https://oviva-ag.github.io/static/openapi/problem-1.0.2.yaml">problem-1.0.2.yaml</a>.
 *
 * <p>Only {@code type}, {@code title} and {@code detail} are serialized, {@code detail} must not
 * contain sensitive data.
 */
public abstract sealed class Problem
    permits BadRequestProblem, NotEntitledProblem, DuplicateDocumentProblem, InternalErrorProblem {

  private final int status;
  private final String type;
  private final String title;
  private final String detail;

  protected Problem(int status, String type, String title, String detail) {
    this.status = status;
    this.type = type;
    this.title = title;
    this.detail = detail;
  }

  /** HTTP status code of the response carrying this problem. */
  @JsonIgnore
  public int status() {
    return status;
  }

  /** Identifies the problem type within this API, as a path. */
  @JsonProperty("type")
  public String type() {
    return type;
  }

  /** Short English summary of the problem type. */
  @JsonProperty("title")
  public String title() {
    return title;
  }

  /** Explanation specific to this occurrence of the problem. */
  @JsonProperty("detail")
  public String detail() {
    return detail;
  }
}
