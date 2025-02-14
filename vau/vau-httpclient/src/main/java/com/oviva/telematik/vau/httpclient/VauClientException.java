package com.oviva.telematik.vau.httpclient;

public class VauClientException extends RuntimeException {

  public VauClientException(String message) {
    super(message);
  }

  public VauClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
