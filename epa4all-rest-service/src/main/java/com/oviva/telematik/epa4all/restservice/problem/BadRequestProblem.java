package com.oviva.telematik.epa4all.restservice.problem;

public final class BadRequestProblem extends Problem {

  public BadRequestProblem() {
    super(
        400,
        "/epa4all/bad-request",
        "Bad Request",
        "The request is invalid, e.g. a required field is missing or a value has an invalid format");
  }
}
