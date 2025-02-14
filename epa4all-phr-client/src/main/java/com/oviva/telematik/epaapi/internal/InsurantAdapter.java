package com.oviva.telematik.epaapi.internal;

import de.gematik.epa.ihe.model.simple.InsurantId;

public class InsurantAdapter implements InsurantId {

  private static final String INSURANT_ID_ROOT = "1.2.276.0.76.4.8";

  private final String kvnr;

  public InsurantAdapter(String kvnr) {
    this.kvnr = kvnr;
  }

  @Override
  public String getRoot() {
    return INSURANT_ID_ROOT;
  }

  @Override
  public String getExtension() {
    return kvnr;
  }
}
