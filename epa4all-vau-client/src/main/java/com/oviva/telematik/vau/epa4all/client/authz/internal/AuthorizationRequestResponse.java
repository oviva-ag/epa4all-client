package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/* example:
  {
  "challenge": "eyJhbGciOiJCUDI1NlIxIiwia2lkIjoicHVrX2lkcF9zaWciLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2lkcC1yZWYuYXBwLnRpLWRpZW5zdGUuZGUiLCJpYXQiOjE3MzIyOTMzODEsImV4cCI6MTczMjI5MzU2MSwidG9rZW5fdHlwZSI6ImNoYWxsZW5nZSIsImp0aSI6IjhkYjcyNTk2LTQ0MzAtNDI2OS1iMTdlLTU3ZGQ1ZWQ5OGExNiIsInNuYyI6IjFkODRlMzUxYzlmNDQxYmJiOGUzMjJiZmU1MmE2NTc1Iiwic2NvcGUiOiJvcGVuaWQgZVBBLWJtdC1ydCIsImNvZGVfY2hhbGxlbmdlIjoiMDVCeWFvWkdVcmM0d2VyTzNFbU9lMUpiYVczaHdUNTRCMUhHRGRSUnF3NCIsImNvZGVfY2hhbGxlbmdlX21ldGhvZCI6IlMyNTYiLCJyZXNwb25zZV90eXBlIjoiY29kZSIsInJlZGlyZWN0X3VyaSI6Imh0dHBzOi8vZTRhLXJ0LmRlaW5lLWVwYS5kZS8iLCJjbGllbnRfaWQiOiJHRU1CSVRNQWVQQWUyenJ4ekxPUiIsInN0YXRlIjoiMVhHM3paQUU5VHRhS2szSE92MWpPS2cxY3BQMzQ4aTQzVUVZc2JmQmJHWGpkTXBFSVo5ejE5U0tPd3hKVUkyRCIsIm5vbmNlIjoiZGlWV1lEUHNJSDZhM1R1VVN6VlBra2IyRUpuTTJmc0p1SkUxM3p0UXNZRThDVmdQSWJSaHlEdXduTzFFTUpKNiJ9.F55CrK1OCyIcJopungedjKXOBDN_1IhgweQHlwa0t5OmOaZwmBNar1I0LFPF_1AYC08gJ0pEZ5G9_rD-JGEGbA",
  "user_consent": {
    "requested_scopes": {
      "openid": "Der Zugriff auf den ID-Token",
      "ePA-bmt-rt": "Zugriff auf die ePA-AS-ePA4all-Funktionalitaet"
    },
    "requested_claims": {
      "given_name": "Zustimmung zur Verarbeitung des Vornamens",
      "professionOID": "Zustimmung zur Verarbeitung der Rolle",
      "organizationName": "Zustimmung zur Verarbeitung der Organisationszugehörigkeit",
      "family_name": "Zustimmung zur Verarbeitung des Nachnamens",
      "idNummer": "Zustimmung zur Verarbeitung der Id (z.B. Krankenversichertennummer, Telematik-Id)"
    }
  }
}
   */
public record AuthorizationRequestResponse(@JsonProperty("challenge") String challenge) {}
