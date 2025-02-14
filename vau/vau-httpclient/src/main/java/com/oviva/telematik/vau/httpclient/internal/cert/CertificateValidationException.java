package com.oviva.telematik.vau.httpclient.internal.cert;

public class CertificateValidationException extends Exception {
  public CertificateValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  public CertificateValidationException(String message) {
    super(message);
  }
}
