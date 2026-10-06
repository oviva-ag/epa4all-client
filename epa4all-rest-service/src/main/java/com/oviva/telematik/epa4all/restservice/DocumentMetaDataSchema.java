package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.gematik.epa.conversion.internal.enumerated.ClassCode;
import de.gematik.epa.conversion.internal.enumerated.ConfidentialityCode;
import de.gematik.epa.conversion.internal.enumerated.EventCode;
import de.gematik.epa.conversion.internal.enumerated.HealthcareFacilityCode;
import de.gematik.epa.conversion.internal.enumerated.PracticeSettingCode;
import de.gematik.epa.conversion.internal.enumerated.TypeCode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Caller-supplied overrides for the {@code DocumentMetadata} produced when a document is written.
 *
 * <p>Every field is optional: a {@code null} field keeps the server's existing default. Only the
 * fields the caller actually sets are applied, so partial metadata is valid.
 */
public record DocumentMetaDataSchema(
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime startService,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime endService,
    String title,
    String comments,
    ConfidentialityCode confidential,
    ClassCode classCode,
    List<EventCode> eventCodeList,
    HealthcareFacilityCode healthcareFacilityTypeCode,
    PracticeSettingCode practiceSettingCode,
    TypeCode typeCode) {

  /** Schema with no overrides: all fields keep the server defaults. */
  public static DocumentMetaDataSchema empty() {
    return new DocumentMetaDataSchema(null, null, null, null, null, null, null, null, null, null);
  }
}
