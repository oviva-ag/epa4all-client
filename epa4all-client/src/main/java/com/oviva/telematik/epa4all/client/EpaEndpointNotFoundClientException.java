package com.oviva.telematik.epa4all.client;

public class EpaEndpointNotFoundClientException extends ClientException {

  public EpaEndpointNotFoundClientException(String message) {
    super(message);
  }

  public EpaEndpointNotFoundClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
