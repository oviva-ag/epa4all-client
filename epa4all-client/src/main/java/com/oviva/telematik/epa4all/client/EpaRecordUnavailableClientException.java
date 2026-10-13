package com.oviva.telematik.epa4all.client;

/**
 * The insurant's ePA record exists but is temporarily not usable (SUSPENDED or INACCESSIBLE). The
 * Information Service documents a retry interval of approx. 24 hours for this case.
 */
public class EpaRecordUnavailableClientException extends ClientException {

  public EpaRecordUnavailableClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
