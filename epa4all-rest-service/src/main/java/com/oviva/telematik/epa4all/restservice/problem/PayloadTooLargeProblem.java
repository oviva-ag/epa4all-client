package com.oviva.telematik.epa4all.restservice.problem;

public final class PayloadTooLargeProblem extends Problem {

  public PayloadTooLargeProblem() {
    super(
        413,
        "/epa4all/payload-too-large",
        "Payload Too Large",
        "The request body exceeds the maximum size accepted by the server");
  }
}
