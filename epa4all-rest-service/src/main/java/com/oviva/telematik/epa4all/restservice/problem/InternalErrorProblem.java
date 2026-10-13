package com.oviva.telematik.epa4all.restservice.problem;

public final class InternalErrorProblem extends Problem {

  public InternalErrorProblem() {
    super(
        500,
        "/epa4all/internal-error",
        "Internal Error",
        "An internal error occurred while processing the request");
  }
}
