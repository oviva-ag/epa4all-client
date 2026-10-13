package com.oviva.telematik.epa4all.restservice.problem;

public final class DuplicateDocumentProblem extends Problem {

  public DuplicateDocumentProblem() {
    super(
        409,
        "/epa4all/duplicate-document",
        "Duplicate Document",
        "The document already exists in the patient's ePA record");
  }
}
