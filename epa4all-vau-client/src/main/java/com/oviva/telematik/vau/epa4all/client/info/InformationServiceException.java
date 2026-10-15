package com.oviva.telematik.vau.epa4all.client.info;

import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;

/** The Information Service could not tell us where the insurant's active health record is. */
public class InformationServiceException extends Epa4AllClientException {

  public enum Reason {
    /**
     * The health record exists but is not in state ACTIVATED, i.e. it is SUSPENDED (relocation to
     * another provider) or INACCESSIBLE. The Information Service documents a retry interval of
     * approx. 24 hours for this case.
     *
     * <p>Sources:
     *
     * <ul>
     *   <li>409 statusMismatch and retry interval of getRecordStatus:
     *       https://github.com/gematik/ePA-Basic/blob/ePA-3.1.3/src/openapi/I_Information_Service.yaml
     *   <li>record states (section 3.1.2):
     *       https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/gemSpec_Aktensystem_ePAfueralle_V1.8.2/
     * </ul>
     */
    RECORD_UNAVAILABLE,

    /** A '400 malformedRequest': our request did not match the schema, retrying won't help. */
    MALFORMED_REQUEST,

    /** The status could not be determined, e.g. an Aktensystem was unreachable or failed. */
    ERROR
  }

  private final Reason reason;

  public InformationServiceException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public InformationServiceException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
