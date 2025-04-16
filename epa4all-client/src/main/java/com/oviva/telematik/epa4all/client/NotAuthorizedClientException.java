package com.oviva.telematik.epa4all.client;

public class NotAuthorizedClientException extends ClientException {

  public NotAuthorizedClientException(String message) {
    super(message);
  }

  public NotAuthorizedClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
