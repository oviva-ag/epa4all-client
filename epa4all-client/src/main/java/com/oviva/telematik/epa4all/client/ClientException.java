package com.oviva.telematik.epa4all.client;

public class ClientException extends RuntimeException {

  public enum Reason {
    /** Any failure not covered by a more specific reason. */
    INTERNAL,
    DUPLICATE_DOCUMENT,
    NOT_AUTHORIZED,

    /**
     * The insurant's ePA record exists but is temporarily not usable (SUSPENDED or INACCESSIBLE).
     * The Information Service documents a retry interval of approx. 24 hours for this case.
     * https://github.com/gematik/ePA-Basic/blob/ePA-3.1.3/src/openapi/I_Information_Service.yaml#L19
     */
    RECORD_UNAVAILABLE,

    /**
     * None of the Aktensysteme holds an ePA record for the insurant, e.g. they opted out. Retrying
     * won't help.
     */
    RECORD_NOT_FOUND,

    /** The ePA record status could not be determined, e.g. an Aktensystem was unreachable. */
    INFORMATION_SERVICE_UNAVAILABLE,

    /** The Information Service rejected our request as malformed, retrying won't help. */
    INFORMATION_SERVICE_MALFORMED_REQUEST
  }

  private final Reason reason;

  public ClientException(String message) {
    this(Reason.INTERNAL, message);
  }

  public ClientException(String message, Throwable cause) {
    this(Reason.INTERNAL, message, cause);
  }

  public ClientException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public ClientException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
