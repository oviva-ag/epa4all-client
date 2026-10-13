package com.oviva.telematik.epa4all.client;

/**
 * The status of the insurant's ePA record could not be determined because an Aktensystem was
 * unreachable or answered with an error.
 */
public class EpaInformationServiceUnavailableClientException extends ClientException {

  public EpaInformationServiceUnavailableClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
