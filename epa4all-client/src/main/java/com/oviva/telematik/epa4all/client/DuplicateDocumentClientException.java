package com.oviva.telematik.epa4all.client;

public class DuplicateDocumentClientException extends ClientException {

  public DuplicateDocumentClientException(String message) {
    super(message);
  }

  public DuplicateDocumentClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
