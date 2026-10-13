package com.oviva.telematik.epa4all.restservice.problem;

public final class RecordUnavailableProblem extends Problem {

  public RecordUnavailableProblem() {
    super(
        503,
        "/epa4all/epa-record-unavailable",
        "ePA Record Unavailable",
        "The patient's ePA record is temporarily unavailable (relocation or maintenance)");
  }
}
