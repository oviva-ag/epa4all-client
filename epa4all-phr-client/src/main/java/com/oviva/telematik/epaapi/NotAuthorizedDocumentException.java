package com.oviva.telematik.epaapi;

import java.util.List;

public class NotAuthorizedDocumentException extends WriteDocumentException {

  public NotAuthorizedDocumentException(String status, List<Error> errors) {
    super(status, errors);
  }
}
