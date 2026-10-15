package com.oviva.telematik.epa4all.restservice.problem;

public final class InformationServiceUnavailableProblem extends Problem {

  public InformationServiceUnavailableProblem() {
    super(
        503,
        "/epa4all/information-service-unavailable",
        "ePA Information Service Unavailable",
        "Could not determine the status of the patient's ePA record");
  }
}
