package com.oviva.telematik.epa4all.restservice.problem;

public final class BadRequestProblem extends Problem {

  public BadRequestProblem(String detail) {
    super(400, "/epa4all/bad-request", "Bad Request", detail);
  }
}
