package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.jwk.JWK;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OidcClientTest {

  // Real signed discovery document JWT (TEST-ONLY)
  private static final String DISCOVERY_JWT =
"""
  eyJhbGciOiJCUDI1NlIxIiwia2lkIjoicHVrX2Rpc2Nfc2lnIiwieDVjIjpbIk1JSUM5ekNDQXA2Z0F3SUJBZ0lEQUxYcU1Bb0dDQ3FHU00\
  0OUJBTUNNSUdFTVFzd0NRWURWUVFHRXdKRVJURWZNQjBHQTFVRUNnd1daMlZ0WVhScGF5QkhiV0pJSUU1UFZDMVdRVXhKUkRFeU1EQUdBMV\
  VFQ3d3cFMyOXRjRzl1Wlc1MFpXNHRRMEVnWkdWeUlGUmxiR1Z0WVhScGEybHVabkpoYzNSeWRXdDBkWEl4SURBZUJnTlZCQU1NRjBkRlRTN\
  UxUMDFRTFVOQk5UWWdWRVZUVkMxUFRreFpNQjRYRFRJMk1ERXlNVEUxTXprek9Gb1hEVE14TURFeU1ERTFNemt6TjFvd2ZURUxNQWtHQTFV\
  RUJoTUNRVlF4S0RBbUJnTlZCQW9NSDFKSlUwVWdSMjFpU0NCVVJWTlVMVTlPVEZrZ0xTQk9UMVF0VmtGTVNVUXhLVEFuQmdOVkJBVVRJRE0\
  0TnpjNExWWXdNVWt3TURBMFZESXdNall3TVRJeE1UVXlNekk0TWpFeE1Sa3dGd1lEVlFRRERCQmthWE5qTG5KMUxtbGtjQzV5YVhObE1Gb3\
  dGQVlIS29aSXpqMENBUVlKS3lRREF3SUlBUUVIQTBJQUJHTCtubUNoalN2R2hWQkgvbzE0aXVVc0s5Q1NaQkF5TytVQ05zNkQ3blphTzV4Y\
  VRMclZOQ2RBNFpiK0hqam9DdWNRamFoRFlaZm12dTNDekNmNFJBYWpnZ0VDTUlIL01CMEdBMVVkRGdRV0JCU09wOE1KTExrcmtzdE5mSEhr\
  QUt3d1VySlVIVEFmQmdOVkhTTUVHREFXZ0JUVnVCeDVpYU9scmNXTnR2NWIvaEEzQTUwRHd6Qk5CZ2dyQmdFRkJRY0JBUVJCTUQ4d1BRWUl\
  Ld1lCQlFVSE1BR0dNV2gwZEhBNkx5OWtiM2R1Ykc5aFpDMTBaWE4wY21WbUxtTnliQzUwYVMxa2FXVnVjM1JsTG1SbEwyOWpjM0F2WldNd0\
  RnWURWUjBQQVFIL0JBUURBZ2VBTUNFR0ExVWRJQVFhTUJnd0NnWUlLb0lVQUV3RWdTTXdDZ1lJS29JVUFFd0VnVXN3REFZRFZSMFRBUUgvQ\
  kFJd0FEQXRCZ1VySkFnREF3UWtNQ0l3SURBZU1Cd3dHakFNREFwSlJGQXRSR2xsYm5OME1Bb0dDQ3FDRkFCTUJJSUVNQW9HQ0NxR1NNNDlC\
  QU1DQTBjQU1FUUNJRVlEYmpndlI2SWJjTlF4R3YxRlFLZzBxQ3FIbGZCbDhrYnJOWFhPRjMrYUFpQllqYWpRenhtV3BRQWV3YXRrZXBTRTh\
  IUXRCTGFSTkFuV0d2Z214TFJXRlE9PSJdLCJ0eXAiOiJKV1QifQ.eyJpYXQiOjE3NzgxNDIzODUsImV4cCI6MTc3ODIyODc4NSwiaXNzdWV\
  yIjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUiLCJqd2tzX3VyaSI6Imh0dHBzOi8vaWRwLX\
  JlZi56ZW50cmFsLmlkcC5zcGxpdGRucy50aS1kaWVuc3RlLmRlL2NlcnRzIiwidXJpX2Rpc2MiOiJodHRwczovL2lkcC1yZWYuemVudHJhb\
  C5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS8ud2VsbC1rbm93bi9vcGVuaWQtY29uZmlndXJhdGlvbiIsImF1dGhvcml6YXRpb25fZW5k\
  cG9pbnQiOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS9hdXRoIiwic3NvX2VuZHBvaW50Ijo\
  iaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUvYXV0aC9zc29fcmVzcG9uc2UiLCJ0b2tlbl9lbm\
  Rwb2ludCI6Imh0dHBzOi8vaWRwLXJlZi56ZW50cmFsLmlkcC5zcGxpdGRucy50aS1kaWVuc3RlLmRlL3Rva2VuIiwidXJpX3B1a19pZHBfZ\
  W5jIjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUvY2VydHMvcHVrX2lkcF9lbmMiLCJ1cmlf\
  cHVrX2lkcF9zaWciOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS9jZXJ0cy9wdWtfaWRwX3N\
  pZyIsImNvZGVfY2hhbGxlbmdlX21ldGhvZHNfc3VwcG9ydGVkIjpbIlMyNTYiXSwicmVzcG9uc2VfdHlwZXNfc3VwcG9ydGVkIjpbImNvZG\
  UiXSwiZ3JhbnRfdHlwZXNfc3VwcG9ydGVkIjpbImF1dGhvcml6YXRpb25fY29kZSJdLCJpZF90b2tlbl9zaWduaW5nX2FsZ192YWx1ZXNfc\
  3VwcG9ydGVkIjpbIkJQMjU2UjEiXSwiYWNyX3ZhbHVlc19zdXBwb3J0ZWQiOlsiZ2VtYXRpay1laGVhbHRoLWxvYS1oaWdoIl0sInJlc3Bv\
  bnNlX21vZGVzX3N1cHBvcnRlZCI6WyJxdWVyeSJdLCJ0b2tlbl9lbmRwb2ludF9hdXRoX21ldGhvZHNfc3VwcG9ydGVkIjpbIm5vbmUiXSw\
  ic2NvcGVzX3N1cHBvcnRlZCI6WyJvcGVuaWQiLCJlLXJlemVwdCIsImUtcmV6ZXB0LWRldiIsImVQQS1QUy1nZW10ayIsImVQQS1ibXQtcX\
  QiLCJlUEEtYm10LXF1IiwiZVBBLWJtdC1ydCIsImVQQS1ibXQtcnUiLCJlUEEtaWJtLXJ1LWludCIsImVQQS1pYm0xIiwiZVBBLWlibTIiL\
  CJlYnRtLWJkciIsImVidG0tYmRyMiIsImZoLWZva3VzLWRlbWlzIiwiZmhpci12emQiLCJnZW0tYXV0aCIsImdtdGlrLWRlbWlzIiwiZ210\
  aWstZGVtaXMtZmtiIiwiZ210aWstZGVtaXMtZnJhIiwiZ210aWstZGVtaXMtcXMiLCJnbXRpay1kZW1pcy1yZWYiLCJnbXRpay1kZW1pcy1\
  ydS10ZXN0IiwiZ210aWstZmhpcmRpcmVjdG9yeS1zc3AiLCJnbXRpay16ZXJvdHJ1c3QtcG9jIiwiaXJkLWJtZyIsImt2c2gtb3B0Iiwib2\
  dyLW5leGVuaW8tZGVtbyIsIm9nci1uZXhlbmlvLWRldiIsIm9nci1uZXhlbmlvLXByZXByb2QiLCJvZ3ItbmV4ZW5pby10ZXN0Iiwib3JnY\
  W5zcGVuZGUtcmVnaXN0ZXIiLCJwYWlyaW5nIiwicnBkb2MtZW1tYSIsInJwZG9jLWVtbWEtcGhhYiIsInRpLW1lc3NlbmdlciIsInRpLXNj\
  b3JlIiwidGktc2NvcmUyIiwienZyLWJub3RrIl0sInN1YmplY3RfdHlwZXNfc3VwcG9ydGVkIjpbInBhaXJ3aXNlIl19.RDczQM7RLwCV_U\
  6V_LLwNgSqm9CH4KDq3nXjGe-hKHiEwYOb5d4MoWoAuIpl0lfH0HhoSodiFJZEX5uOQ_XzsA
""";

  private static final String P256_JWK_JSON =
      """
      {
        "kty": "EC",
        "crv": "P-256",
        "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
        "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"
      }
      """;

  @Mock HttpClient httpClient;
  @Mock OidcClient.DiscoveryValidator discoveryValidator;
  @Mock HttpResponse<String> mockResponse;
  @Mock HttpHeaders mockHeaders;

  private OidcClient oidcClient;

  @BeforeEach
  void setUp() {
    oidcClient = new OidcClient(httpClient, discoveryValidator);
  }

  // -- fetchOidcDiscoveryDocument --

  @Test
  void fetchOidcDiscoveryDocument_validJwt_returnsResponse() throws Exception {
    var issuer = URI.create("https://idp.example.test");
    setupMockResponse(200, "application/jwt", DISCOVERY_JWT);

    var response = oidcClient.fetchOidcDiscoveryDocument(issuer);

    assertNotNull(response);
    assertEquals(
        URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de"), response.issuer());
    assertEquals(
        URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de/certs/puk_idp_enc"),
        response.uriPukIdpEnc());
    assertEquals(
        URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de/certs/puk_idp_sig"),
        response.uriPukIdpSig());
    assertEquals(
        URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de/certs"), response.jwksUri());
  }

  @Test
  void fetchOidcDiscoveryDocument_validatesJwt() throws Exception {
    var issuer = URI.create("https://idp.example.test");
    setupMockResponse(200, "application/jwt", DISCOVERY_JWT);

    oidcClient.fetchOidcDiscoveryDocument(issuer);

    verify(discoveryValidator).validate(any());
  }

  @Test
  void fetchOidcDiscoveryDocument_validationFails_throwsAuthorizationException() throws Exception {
    var issuer = URI.create("https://idp.example.test");
    setupMockResponse(200, "application/jwt", DISCOVERY_JWT);

    doThrow(new AuthorizationException("untrusted")).when(discoveryValidator).validate(any());

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchOidcDiscoveryDocument(issuer));
  }

  @Test
  void fetchOidcDiscoveryDocument_non200Status_throwsAuthorizationException() throws Exception {
    var issuer = URI.create("https://idp.example.test");
    setupMockResponse(404, "application/jwt", DISCOVERY_JWT);

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchOidcDiscoveryDocument(issuer));
  }

  @Test
  void fetchOidcDiscoveryDocument_missingContentType_throwsAuthorizationException()
      throws Exception {
    var issuer = URI.create("https://idp.example.test");
    doReturn(mockResponse).when(httpClient).send(any(), any());
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.headers()).thenReturn(mockHeaders);
    when(mockHeaders.firstValue("content-type")).thenReturn(Optional.empty());

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchOidcDiscoveryDocument(issuer));
  }

  @Test
  void fetchOidcDiscoveryDocument_unsupportedContentType_throwsAuthorizationException()
      throws Exception {
    var issuer = URI.create("https://idp.example.test");
    setupMockResponse(200, "application/json", DISCOVERY_JWT);

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchOidcDiscoveryDocument(issuer));
  }

  // -- fetchJwk --

  @Test
  void fetchJwk_validJson_returnsJwk() throws Exception {
    var jwksUri = URI.create("https://idp.example.test/certs/puk_idp_enc");
    setupMockResponse(200, "application/json", P256_JWK_JSON);

    var jwk = oidcClient.fetchJwk(jwksUri);

    assertNotNull(jwk);
    assertInstanceOf(JWK.class, jwk);
  }

  @Test
  void fetchJwk_non200Status_throwsAuthorizationException() throws Exception {
    var jwksUri = URI.create("https://idp.example.test/certs/puk_idp_enc");
    setupMockResponse(404, "application/json", P256_JWK_JSON);

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchJwk(jwksUri));
  }

  @Test
  void fetchJwk_missingContentType_throwsAuthorizationException() throws Exception {
    var jwksUri = URI.create("https://idp.example.test/certs/puk_idp_enc");
    doReturn(mockResponse).when(httpClient).send(any(), any());
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.headers()).thenReturn(mockHeaders);
    when(mockHeaders.firstValue("content-type")).thenReturn(Optional.empty());

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchJwk(jwksUri));
  }

  @Test
  void fetchJwk_wrongContentType_throwsAuthorizationException() throws Exception {
    var jwksUri = URI.create("https://idp.example.test/certs/puk_idp_enc");
    setupMockResponse(200, "text/plain", P256_JWK_JSON);

    assertThrows(AuthorizationException.class, () -> oidcClient.fetchJwk(jwksUri));
  }

  @Test
  void fetchJwk_contentTypeWithCharset_isAccepted() throws Exception {
    var jwksUri = URI.create("https://idp.example.test/certs/puk_idp_enc");
    setupMockResponse(200, "application/json; charset=utf-8", P256_JWK_JSON);

    var jwk = oidcClient.fetchJwk(jwksUri);

    assertNotNull(jwk);
  }

  // -- helpers --

  @SuppressWarnings("unchecked")
  private void setupMockResponse(int status, String contentType, String body) throws Exception {
    doReturn(mockResponse).when(httpClient).send(any(), any());
    when(mockResponse.statusCode()).thenReturn(status);
    if (status == 200) {
      when(mockResponse.headers()).thenReturn(mockHeaders);
      when(mockHeaders.firstValue("content-type")).thenReturn(Optional.of(contentType));
      lenient().when(mockResponse.body()).thenReturn(body);
    }
  }
}
