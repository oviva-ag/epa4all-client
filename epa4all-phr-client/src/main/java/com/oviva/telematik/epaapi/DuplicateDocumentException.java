package com.oviva.telematik.epaapi;

import java.util.List;

public class DuplicateDocumentException extends WriteDocumentException {

  public DuplicateDocumentException(String status, List<Error> errors) {
    super(status, errors);
  }
}
