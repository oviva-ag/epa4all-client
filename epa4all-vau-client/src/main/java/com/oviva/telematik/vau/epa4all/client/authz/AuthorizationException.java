package com.oviva.telematik.vau.epa4all.client.authz;

import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;

public class AuthorizationException extends Epa4AllClientException {
  public AuthorizationException(String message) {
    super(message);
  }

  public AuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
