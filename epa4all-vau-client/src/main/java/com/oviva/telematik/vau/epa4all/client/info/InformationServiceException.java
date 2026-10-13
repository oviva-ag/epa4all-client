package com.oviva.telematik.vau.epa4all.client.info;

import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;

/**
 * The health record status could not be determined, e.g. because an Aktensystem was unreachable or
 * answered with an error.
 */
public class InformationServiceException extends Epa4AllClientException {

  public InformationServiceException(String message) {
    super(message);
  }

  public InformationServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
