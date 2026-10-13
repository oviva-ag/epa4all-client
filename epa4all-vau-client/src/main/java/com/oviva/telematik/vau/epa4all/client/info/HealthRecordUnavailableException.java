package com.oviva.telematik.vau.epa4all.client.info;

import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;

/**
 * The health record exists but is not in state ACTIVATED, i.e. it is SUSPENDED (relocation to
 * another provider) or INACCESSIBLE. The Information Service documents a retry interval of approx.
 * 24 hours for this case.
 */
public class HealthRecordUnavailableException extends Epa4AllClientException {

  public HealthRecordUnavailableException(String message) {
    super(message);
  }
}
