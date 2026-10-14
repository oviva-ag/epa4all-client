package com.oviva.telematik.epa4all.restservice.problem;

public final class NotEntitledProblem extends Problem {

  public NotEntitledProblem() {
    super(
        403,
        "/epa4all/not-entitled",
        "Not Entitled",
        "No valid entitlement to access the patient's ePA record");
  }
}
