package com.oviva.telematik.vau.epa4all.client.authz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nimbusds.jwt.SignedJWT;
import com.oviva.telematik.vau.epa4all.client.authz.internal.AuthnChallengeResponder;
import com.oviva.telematik.vau.epa4all.client.authz.internal.AuthnClientAttester;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

  private static final URI VAU_ENDPOINT = URI.create("https://epa-as-1.dev.epa4all.de");
  private static final URI IDP_AUTH_ENDPOINT = URI.create("https://idp.example.com/authorize");
  private static final URI IDP_ISSUER = URI.create("https://idp.example.com");
  private static final String INSURANT_ID = "X123456789";
  private static final String NONCE_PATH = "/epa/authz/v1/getNonce";
  private static final String AUTHZ_REQUEST_PATH = "/epa/authz/v1/send_authorization_request_sc";
  private static final String AUTHCODE_PATH = "/epa/authz/v1/send_authcode_sc";

  @Test
  void getNonce_includesErrorBodyInException() {
    var body = "{\"errorCode\":\"statusMismatch\",\"errorDetail\":\"already authorized\"}";
    var service =
        new AuthorizationService(
            req -> new HttpResponse(409, List.of(), body.getBytes()), null, null, null);

    var e =
        assertThrows(
            IllegalStateException.class, () -> service.getNonce(VAU_ENDPOINT, INSURANT_ID));

    assertTrue(e.getMessage().contains("409"));
    assertTrue(e.getMessage().contains(body));
  }

  @Test
  void getNonce_withoutErrorBody_throws() {
    var service =
        new AuthorizationService(req -> new HttpResponse(500, List.of(), null), null, null, null);

    var e =
        assertThrows(
            IllegalStateException.class, () -> service.getNonce(VAU_ENDPOINT, INSURANT_ID));

    assertEquals("unexpected status 'GET " + NONCE_PATH + "' 500, body: ", e.getMessage());
  }

  @Test
  void authorize_sendAuthorizationRequestFails_includesErrorBodyInException() {
    var body = "{\"errorCode\":\"invalAuth\"}";
    var service =
        new AuthorizationService(
            inner(Map.of(AUTHZ_REQUEST_PATH, new HttpResponse(403, List.of(), body.getBytes()))),
            null,
            null,
            null);

    var e =
        assertThrows(
            IllegalStateException.class,
            () -> service.authorizeVauWithSmcB(VAU_ENDPOINT, INSURANT_ID));

    assertEquals(
        "unexpected status 'GET " + AUTHZ_REQUEST_PATH + "' 403, body: " + body, e.getMessage());
  }

  @Test
  void authorize_idpAuthorizationEndpointFails_includesErrorBodyInException() throws Exception {
    var outer = mock(HttpClient.class);
    var idpResponse = outerResponse(500, "idp down".getBytes(), Map.of());
    doReturn(idpResponse).when(outer).send(any(), any());

    var service = new AuthorizationService(inner(Map.of()), outer, null, null);

    var e =
        assertThrows(
            IllegalStateException.class,
            () -> service.authorizeVauWithSmcB(VAU_ENDPOINT, INSURANT_ID));

    assertEquals(
        "unexpected status 'GET " + IDP_AUTH_ENDPOINT + "' 500, body: idp down", e.getMessage());
  }

  @Test
  void authorize_sendAuthcodeFails_includesErrorBodyInException() throws Exception {
    var outer = mock(HttpClient.class);
    var challengeResponse = outerResponse(200, "{\"challenge\":\"c\"}".getBytes(), Map.of());
    var authResponse =
        outerResponse(
            302, "", Map.of("Location", List.of("https://redirect.example.com?code=abc")));
    doReturn(challengeResponse, authResponse).when(outer).send(any(), any());

    var challengeResponder = mock(AuthnChallengeResponder.class);
    when(challengeResponder.challengeResponse("c"))
        .thenReturn(new AuthnChallengeResponder.Response(IDP_ISSUER, "signed-challenge"));

    var attestation = mock(SignedJWT.class);
    when(attestation.serialize()).thenReturn("attestation");
    var attester = mock(AuthnClientAttester.class);
    when(attester.attestClient("nonce")).thenReturn(attestation);

    var body = "{\"errorCode\":\"statusMismatch\",\"errorDetail\":\"outdated nonce\"}";
    var service =
        new AuthorizationService(
            inner(Map.of(AUTHCODE_PATH, new HttpResponse(409, List.of(), body.getBytes()))),
            outer,
            challengeResponder,
            attester);

    var e =
        assertThrows(
            IllegalStateException.class,
            () -> service.authorizeVauWithSmcB(VAU_ENDPOINT, INSURANT_ID));

    assertEquals(
        "unexpected status 'POST " + AUTHCODE_PATH + "' 409, body: " + body, e.getMessage());
  }

  /** Inner (VAU) client that succeeds for every step unless {@code overrides} has the path. */
  private static com.oviva.telematik.vau.httpclient.HttpClient inner(
      Map<String, HttpResponse> overrides) {
    return (HttpRequest req) -> {
      var path = req.uri().getPath();
      if (overrides.containsKey(path)) {
        return overrides.get(path);
      }
      return switch (path) {
        case NONCE_PATH -> new HttpResponse(200, List.of(), "{\"nonce\":\"nonce\"}".getBytes());
        case AUTHZ_REQUEST_PATH ->
            new HttpResponse(
                302, List.of(new HttpHeader("Location", IDP_AUTH_ENDPOINT.toString())), null);
        case AUTHCODE_PATH -> new HttpResponse(200, List.of(), "{}".getBytes());
        default -> throw new AssertionError("unexpected request " + path);
      };
    };
  }

  @SuppressWarnings("unchecked")
  private static <T> java.net.http.HttpResponse<T> outerResponse(
      int status, T body, Map<String, List<String>> headers) {
    var res = (java.net.http.HttpResponse<T>) mock(java.net.http.HttpResponse.class);
    lenient().when(res.statusCode()).thenReturn(status);
    lenient().when(res.body()).thenReturn(body);
    lenient().when(res.headers()).thenReturn(HttpHeaders.of(headers, (a, b) -> true));
    return res;
  }
}
