package com.oviva.telematik.epa4all.restservice.problem;

public final class RecordNotFoundProblem extends Problem {

  public RecordNotFoundProblem() {
    super(
        404,
        "/epa4all/epa-record-not-found",
        "ePA Record Not Found",
        "No ePA record was found for the patient, e.g. they opted out of the ePA");
  }
}
