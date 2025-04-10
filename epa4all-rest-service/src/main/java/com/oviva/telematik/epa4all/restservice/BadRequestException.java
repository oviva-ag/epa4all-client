package com.oviva.telematik.epa4all.restservice;

public class BadRequestException extends ApplicationException {
  public BadRequestException(String message) {
    super(message);
  }

  public BadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
